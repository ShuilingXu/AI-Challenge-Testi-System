package com.autohr.common.file;

import com.autohr.modules.system.service.SystemConfigService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;

@Service
public class S3ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorageService.class);
    private static final String[] CONFIG_KEYS = {
            "S3_ENABLED", "S3_ENDPOINT", "S3_REGION", "S3_BUCKET", "S3_ACCESS_KEY_ID",
            "S3_SECRET_ACCESS_KEY", "S3_SESSION_TOKEN", "S3_PREFIX", "S3_PATH_STYLE_ACCESS",
            "S3_INTERNAL_ENDPOINT_ENABLED", "S3_INTERNAL_ENDPOINT", "S3_ALLOW_HTTP_ENDPOINTS",
            "S3_ALLOW_PRIVATE_ENDPOINTS"
    };
    private static final long ENDPOINT_VALIDATION_TTL_NANOS = Duration.ofMinutes(1).toNanos();
    private static final long DNS_RETRY_TTL_NANOS = Duration.ofSeconds(10).toNanos();
    private static final int DELETE_MAX_ATTEMPTS = 3;

    private final SystemConfigService systemConfigService;
    private final ThreadPoolTaskExecutor s3ArchiveExecutor;
    private final S3ClientFactory s3ClientFactory;
    private final S3PresignerFactory s3PresignerFactory;
    private final S3EndpointValidator.AddressResolver addressResolver;
    private final LongSupplier nanoClock;
    private final Object clientMonitor = new Object();
    private final Object endpointValidationMonitor = new Object();
    private volatile ManagedResource<UploadClientBundle> uploadClientResource;
    private volatile ManagedResource<ExternalClientBundle> externalClientResource;
    private volatile EndpointValidationSnapshot uploadEndpointValidation;
    private volatile EndpointValidationSnapshot externalEndpointValidation;
    private volatile boolean closed;

    @Autowired
    public S3ObjectStorageService(SystemConfigService systemConfigService,
                                  ThreadPoolTaskExecutor s3ArchiveExecutor) {
        this(systemConfigService, s3ArchiveExecutor, S3ObjectStorageService::buildClient,
                S3ObjectStorageService::buildPresigner, java.net.InetAddress::getAllByName,
                System::nanoTime);
    }

    S3ObjectStorageService(SystemConfigService systemConfigService,
                           ThreadPoolTaskExecutor s3ArchiveExecutor,
                           S3ClientFactory s3ClientFactory,
                           S3PresignerFactory s3PresignerFactory,
                           S3EndpointValidator.AddressResolver addressResolver,
                           LongSupplier nanoClock) {
        this.systemConfigService = systemConfigService;
        this.s3ArchiveExecutor = s3ArchiveExecutor;
        this.s3ClientFactory = s3ClientFactory;
        this.s3PresignerFactory = s3PresignerFactory;
        this.addressResolver = addressResolver;
        this.nanoClock = nanoClock;
    }

    /**
     * Archives a local file to an S3-compatible object store. Local storage remains
     * the source of truth so a storage outage never interrupts interview processing.
     */
    public void archiveIfEnabled(Path localFile, String objectName, String contentType) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitArchive(localFile, objectName, contentType);
                }
            });
            return;
        }
        submitArchive(localFile, objectName, contentType);
    }

    private void submitArchive(Path localFile, String objectName, String contentType) {
        try {
            s3ArchiveExecutor.execute(() -> archive(localFile, objectName, contentType));
        } catch (TaskRejectedException ex) {
            log.warn("S3 archive queue is full for {}. Keeping the local file.", localFile.getFileName());
        }
    }

    private void archive(Path localFile, String objectName, String contentType) {
        StorageSettings settings = loadSettings();
        if (!settings.enabled()) {
            return;
        }
        if (!Files.isRegularFile(localFile)) {
            log.warn("Skipping S3 archive because the local file is unavailable: {}", localFile);
            return;
        }
        if (!isUploadUsable(settings)) {
            log.warn("Skipping S3 archive because the object storage configuration is incomplete or unsafe");
            return;
        }

        String objectKey;
        try {
            objectKey = buildObjectKey(settings.prefix(), objectName);
        } catch (IllegalArgumentException ex) {
            log.warn("Skipping S3 archive because the object key is invalid: {}", ex.getMessage());
            return;
        }

        try {
            withUploadClient(settings, client -> {
                PutObjectRequest.Builder request = PutObjectRequest.builder()
                        .bucket(settings.bucket())
                        .key(objectKey);
                if (contentType != null && !contentType.isBlank()) {
                    request.contentType(contentType);
                }
                client.putObject(request.build(), RequestBody.fromFile(localFile));
                return null;
            });
            log.info("Archived {} to S3 bucket {} as {}", localFile.getFileName(), settings.bucket(), objectKey);
        } catch (Exception ex) {
            // S3 storage is optional. The caller keeps its local file on every failure.
            log.warn("S3 archive failed for {}. Keeping the local file: {}", localFile.getFileName(), ex.toString());
        }
    }

    /**
     * Deletes an archived object without affecting the caller when storage is unavailable.
     * Callers should invoke this only after the owning database transaction commits.
     */
    public void deleteObjectIfEnabled(String objectName) {
        deleteObjectsIfEnabled(objectName == null ? List.of() : List.of(objectName));
    }

    public void deleteObjectsIfEnabled(Collection<String> objectNames) {
        if (objectNames == null || objectNames.isEmpty()) {
            return;
        }
        List<String> distinctObjectNames = new LinkedHashSet<>(objectNames).stream()
                .filter(objectName -> objectName != null && !objectName.isBlank())
                .toList();
        if (distinctObjectNames.isEmpty()) {
            return;
        }
        try {
            s3ArchiveExecutor.execute(() -> deleteObjects(distinctObjectNames));
        } catch (TaskRejectedException ex) {
            log.warn("S3 deletion queue is full for {} object(s). Retrying synchronously.", distinctObjectNames.size());
            deleteObjects(distinctObjectNames);
        }
    }

    private void deleteObjects(List<String> objectNames) {
        StorageSettings settings = loadSettings();
        if (!settings.enabled() || !isUploadUsable(settings)) {
            return;
        }
        try {
            withUploadClient(settings, client -> {
                for (String objectName : objectNames) {
                    String objectKey;
                    try {
                        objectKey = buildObjectKey(settings.prefix(), objectName);
                    } catch (Exception ex) {
                        log.warn("Skipping invalid S3 deletion key {}: {}", objectName, ex.toString());
                        continue;
                    }
                    for (int attempt = 1; attempt <= DELETE_MAX_ATTEMPTS; attempt++) {
                        try {
                            client.deleteObject(DeleteObjectRequest.builder()
                                    .bucket(settings.bucket())
                                    .key(objectKey)
                                    .build());
                            log.info("Deleted S3 object {} from bucket {}", objectKey, settings.bucket());
                            break;
                        } catch (Exception ex) {
                            if (attempt == DELETE_MAX_ATTEMPTS) {
                                log.error("S3 deletion failed after {} attempts for {}: {}",
                                        DELETE_MAX_ATTEMPTS, objectName, ex.toString());
                                break;
                            }
                            try {
                                Thread.sleep(200L * attempt);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                log.warn("S3 deletion retry interrupted for {}", objectName);
                                break;
                            }
                        }
                    }
                }
                return null;
            });
        } catch (Exception ex) {
            log.warn("Best-effort S3 deletion could not start for {} object(s): {}",
                    objectNames.size(), ex.toString());
        }
    }

    /**
     * Returns a short-lived URL signed for the browser-facing endpoint. A failed
     * archive leaves no readable object, so callers retain their local response.
     */
    public Optional<URI> presignExternalDownloadIfAvailable(String objectName) {
        StorageSettings settings = loadSettings();
        if (!settings.enabled() || !isExternalUsable(settings)) {
            return Optional.empty();
        }
        try {
            String objectKey = buildObjectKey(settings.prefix(), objectName);
            return withExternalClients(settings, bundle -> {
                bundle.externalClient().headObject(HeadObjectRequest.builder()
                        .bucket(settings.bucket())
                        .key(objectKey)
                        .build());
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(settings.bucket())
                        .key(objectKey)
                        .build();
                URI url = URI.create(bundle.presigner().presignGetObject(GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(5))
                                .getObjectRequest(getObjectRequest)
                                .build())
                        .url()
                        .toExternalForm());
                return Optional.of(url);
            });
        } catch (Exception ex) {
            log.debug("S3 object {} is not available through the browser endpoint: {}", objectName, ex.toString());
            return Optional.empty();
        }
    }

    private StorageSettings loadSettings() {
        Map<String, String> config = systemConfigService.loadConfig(CONFIG_KEYS);
        String externalEndpoint = trim(config.get("S3_ENDPOINT"));
        boolean internalEndpointEnabled = Boolean.parseBoolean(config.get("S3_INTERNAL_ENDPOINT_ENABLED"));
        String uploadEndpoint = internalEndpointEnabled
                ? trim(config.get("S3_INTERNAL_ENDPOINT"))
                : externalEndpoint;
        return new StorageSettings(
                Boolean.parseBoolean(config.get("S3_ENABLED")),
                uploadEndpoint,
                externalEndpoint,
                valueOrDefault(config.get("S3_REGION"), "us-east-1"),
                trim(config.get("S3_BUCKET")),
                trim(config.get("S3_ACCESS_KEY_ID")),
                trim(config.get("S3_SECRET_ACCESS_KEY")),
                trim(config.get("S3_SESSION_TOKEN")),
                trim(config.get("S3_PREFIX")),
                Boolean.parseBoolean(config.get("S3_PATH_STYLE_ACCESS")),
                Boolean.parseBoolean(config.get("S3_ALLOW_HTTP_ENDPOINTS")),
                Boolean.parseBoolean(config.get("S3_ALLOW_PRIVATE_ENDPOINTS")));
    }

    private boolean isUploadUsable(StorageSettings settings) {
        return settings.hasCredentials()
                && !settings.uploadEndpoint().isEmpty()
                && isEndpointAllowed(settings.uploadEndpoint(), settings.allowHttpEndpoints(),
                        settings.allowPrivateEndpoints(), true);
    }

    private boolean isExternalUsable(StorageSettings settings) {
        return settings.hasCredentials()
                && !settings.externalEndpoint().isEmpty()
                && isEndpointAllowed(settings.externalEndpoint(), settings.allowHttpEndpoints(),
                        settings.allowPrivateEndpoints(), false);
    }

    private boolean isEndpointAllowed(String endpoint, boolean allowHttp, boolean allowPrivate,
                                      boolean uploadEndpoint) {
        EndpointValidationKey key = new EndpointValidationKey(endpoint, allowHttp, allowPrivate);
        long now = nanoClock.getAsLong();
        EndpointValidationSnapshot observed = uploadEndpoint ? uploadEndpointValidation : externalEndpointValidation;
        if (isFreshEndpointValidation(observed, key, now)) {
            return observed.allowed();
        }

        // DNS may block. Resolve outside the publication lock so upload and download
        // endpoint checks cannot stall each other.
        S3EndpointValidator.ValidationResult result = S3EndpointValidator.validate(
                endpoint, allowHttp, allowPrivate, addressResolver);
        synchronized (endpointValidationMonitor) {
            EndpointValidationSnapshot current = uploadEndpoint
                    ? uploadEndpointValidation
                    : externalEndpointValidation;
            now = nanoClock.getAsLong();
            if (current != observed && isFreshEndpointValidation(current, key, now)) {
                return current.allowed();
            }
            EndpointValidationSnapshot replacement;
            if (result.allowed()) {
                long expiresAt = now + ENDPOINT_VALIDATION_TTL_NANOS;
                replacement = new EndpointValidationSnapshot(key, true, expiresAt);
            } else {
                long retryTtl = result.failure() == S3EndpointValidator.ValidationFailure.DNS_FAILURE
                        ? DNS_RETRY_TTL_NANOS
                        : ENDPOINT_VALIDATION_TTL_NANOS;
                long expiresAt = now + retryTtl;
                replacement = new EndpointValidationSnapshot(key, false, expiresAt);
            }
            if (uploadEndpoint) {
                uploadEndpointValidation = replacement;
            } else {
                externalEndpointValidation = replacement;
            }
            return replacement.allowed();
        }
    }

    private boolean isFreshEndpointValidation(EndpointValidationSnapshot snapshot,
                                              EndpointValidationKey key,
                                              long now) {
        return snapshot != null
                && snapshot.key().equals(key)
                && now < snapshot.expiresAtNanos();
    }

    private <T> T withUploadClient(StorageSettings settings, Function<S3Client, T> action) {
        try (ResourceLease<UploadClientBundle> lease = acquireUploadClient(settings.uploadClientKey())) {
            return action.apply(lease.resource().client());
        }
    }

    private <T> T withExternalClients(StorageSettings settings, Function<ExternalClientBundle, T> action) {
        try (ResourceLease<ExternalClientBundle> lease = acquireExternalClients(settings.externalClientKey())) {
            return action.apply(lease.resource());
        }
    }

    private ResourceLease<UploadClientBundle> acquireUploadClient(ClientKey requiredKey) {
        ManagedResource<UploadClientBundle> previous = null;
        ResourceLease<UploadClientBundle> lease;
        synchronized (clientMonitor) {
            if (closed) {
                throw new IllegalStateException("S3 object storage service is shutting down");
            }
            ManagedResource<UploadClientBundle> current = uploadClientResource;
            if (current == null || !current.key().equals(requiredKey)) {
                UploadClientBundle replacement = new UploadClientBundle(createClient(requiredKey));
                previous = current;
                current = new ManagedResource<>(requiredKey, replacement);
                uploadClientResource = current;
            }
            lease = current.acquire();
        }
        retire(previous);
        return lease;
    }

    private ResourceLease<ExternalClientBundle> acquireExternalClients(ClientKey requiredKey) {
        ManagedResource<ExternalClientBundle> previous = null;
        ResourceLease<ExternalClientBundle> lease;
        synchronized (clientMonitor) {
            if (closed) {
                throw new IllegalStateException("S3 object storage service is shutting down");
            }
            ManagedResource<ExternalClientBundle> current = externalClientResource;
            if (current == null || !current.key().equals(requiredKey)) {
                ExternalClientBundle replacement = createExternalClientBundle(requiredKey);
                previous = current;
                current = new ManagedResource<>(requiredKey, replacement);
                externalClientResource = current;
            }
            lease = current.acquire();
        }
        retire(previous);
        return lease;
    }

    private ExternalClientBundle createExternalClientBundle(ClientKey key) {
        S3Client externalClient = null;
        S3Presigner presigner = null;
        try {
            externalClient = createClient(key);
            presigner = s3PresignerFactory.create(key.endpoint(), key.region(), key.accessKeyId(),
                    key.secretAccessKey(), key.sessionToken(), key.pathStyleAccess());
            return new ExternalClientBundle(externalClient, presigner);
        } catch (RuntimeException ex) {
            closeQuietly(presigner);
            closeQuietly(externalClient);
            throw ex;
        }
    }

    private S3Client createClient(ClientKey key) {
        return s3ClientFactory.create(key.endpoint(), key.region(), key.accessKeyId(), key.secretAccessKey(),
                key.sessionToken(), key.pathStyleAccess());
    }

    private static S3Client buildClient(String endpoint, String region, String accessKeyId, String secretAccessKey,
                                        String sessionToken, boolean pathStyleAccess) {
        var credentials = sessionToken.isEmpty()
                ? AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                : AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(pathStyleAccess)
                .endpointOverride(URI.create(endpoint))
                .overrideConfiguration(configuration -> configuration
                        .apiCallAttemptTimeout(Duration.ofSeconds(15))
                        .apiCallTimeout(Duration.ofSeconds(30)))
                .build();
    }

    private static S3Presigner buildPresigner(String endpoint, String region, String accessKeyId, String secretAccessKey,
                                              String sessionToken, boolean pathStyleAccess) {
        var credentials = sessionToken.isEmpty()
                ? AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                : AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build())
                .endpointOverride(URI.create(endpoint))
                .build();
    }

    @PreDestroy
    void closeClients() {
        ManagedResource<UploadClientBundle> upload;
        ManagedResource<ExternalClientBundle> external;
        synchronized (clientMonitor) {
            closed = true;
            upload = uploadClientResource;
            external = externalClientResource;
            uploadClientResource = null;
            externalClientResource = null;
        }
        retire(upload);
        retire(external);
    }

    private void retire(ManagedResource<?> resource) {
        if (resource != null) {
            resource.retire();
        }
    }

    private String buildObjectKey(String prefix, String objectName) {
        String normalizedName = normalizePath(objectName);
        String normalizedPrefix = normalizePath(prefix == null ? "" : prefix);
        return normalizedPrefix.isEmpty() ? normalizedName : normalizedPrefix + "/" + normalizedName;
    }

    private String normalizePath(String value) {
        String normalized = trim(value).replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            return "";
        }
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("object path must not contain empty, dot, or parent segments");
            }
        }
        return normalized;
    }

    private String valueOrDefault(String value, String fallback) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private record StorageSettings(boolean enabled, String uploadEndpoint, String externalEndpoint,
                                   String region, String bucket, String accessKeyId, String secretAccessKey,
                                   String sessionToken, String prefix, boolean pathStyleAccess,
                                   boolean allowHttpEndpoints, boolean allowPrivateEndpoints) {

        private boolean hasCredentials() {
            return !bucket.isEmpty()
                    && !accessKeyId.isEmpty()
                    && !secretAccessKey.isEmpty();
        }

        private ClientKey uploadClientKey() {
            return new ClientKey(uploadEndpoint, region, accessKeyId, secretAccessKey,
                    sessionToken, pathStyleAccess);
        }

        private ClientKey externalClientKey() {
            return new ClientKey(externalEndpoint, region, accessKeyId, secretAccessKey,
                    sessionToken, pathStyleAccess);
        }
    }

    private record ClientKey(String endpoint, String region,
                             String accessKeyId, String secretAccessKey, String sessionToken,
                             boolean pathStyleAccess) {
    }

    private record EndpointValidationKey(String endpoint, boolean allowHttp, boolean allowPrivate) {
    }

    private record EndpointValidationSnapshot(EndpointValidationKey key, boolean allowed,
                                              long expiresAtNanos) {
    }

    private record UploadClientBundle(S3Client client) implements AutoCloseable {
        @Override
        public void close() {
            closeQuietly(client);
        }
    }

    private record ExternalClientBundle(S3Client externalClient,
                                        S3Presigner presigner) implements AutoCloseable {
        @Override
        public void close() {
            closeQuietly(presigner);
            closeQuietly(externalClient);
        }
    }

    private static final class ManagedResource<T extends AutoCloseable> {
        private final ClientKey key;
        private final T resource;
        private int leases;
        private boolean retired;

        private ManagedResource(ClientKey key, T resource) {
            this.key = key;
            this.resource = resource;
        }

        private ClientKey key() {
            return key;
        }

        private synchronized ResourceLease<T> acquire() {
            if (retired) {
                throw new IllegalStateException("S3 client has been retired");
            }
            leases++;
            return new ResourceLease<>(this, resource);
        }

        private synchronized void retire() {
            retired = true;
            closeIfUnused();
        }

        private synchronized void release() {
            if (leases <= 0) {
                return;
            }
            leases--;
            closeIfUnused();
        }

        private void closeIfUnused() {
            if (retired && leases == 0) {
                closeQuietly(resource);
            }
        }
    }

    private static final class ResourceLease<T extends AutoCloseable> implements AutoCloseable {
        private ManagedResource<T> owner;
        private final T resource;

        private ResourceLease(ManagedResource<T> owner, T resource) {
            this.owner = owner;
            this.resource = resource;
        }

        private T resource() {
            return resource;
        }

        @Override
        public void close() {
            ManagedResource<T> currentOwner = owner;
            if (currentOwner != null) {
                owner = null;
                currentOwner.release();
            }
        }
    }

    @FunctionalInterface
    interface S3ClientFactory {
        S3Client create(String endpoint, String region, String accessKeyId, String secretAccessKey,
                        String sessionToken, boolean pathStyleAccess);
    }

    @FunctionalInterface
    interface S3PresignerFactory {
        S3Presigner create(String endpoint, String region, String accessKeyId, String secretAccessKey,
                           String sessionToken, boolean pathStyleAccess);
    }
}
