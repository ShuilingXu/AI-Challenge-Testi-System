package com.autohr.common.file;

import com.autohr.modules.system.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ObjectStorageServiceTest {

    @Mock
    SystemConfigService systemConfigService;

    @Mock
    ThreadPoolTaskExecutor s3ArchiveExecutor;

    @Mock
    S3ObjectStorageService.S3ClientFactory s3ClientFactory;

    @Mock
    S3ObjectStorageService.S3PresignerFactory s3PresignerFactory;

    @Mock
    S3Client s3Client;

    @Test
    void executesQueuedDeleteWithCachedDnsValidationAndSingleClient() throws Exception {
        when(systemConfigService.loadConfig(any(String[].class))).thenReturn(enabledConfig());
        when(s3ClientFactory.create(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(s3Client);
        AtomicInteger dnsLookups = new AtomicInteger();
        S3ObjectStorageService service = service(host -> {
            dnsLookups.incrementAndGet();
            return new InetAddress[]{InetAddress.getByAddress(new byte[]{1, 1, 1, 1})};
        });
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);

        service.deleteObjectIfEnabled("resumes/first.pdf");
        service.deleteObjectIfEnabled("resumes/second.pdf");
        verify(s3ArchiveExecutor, times(2)).execute(tasks.capture());
        tasks.getAllValues().forEach(Runnable::run);

        ArgumentCaptor<DeleteObjectRequest> requests = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(2)).deleteObject(requests.capture());
        assertEquals("autohr", requests.getAllValues().get(0).bucket());
        assertEquals("resumes/first.pdf", requests.getAllValues().get(0).key());
        assertEquals("resumes/second.pdf", requests.getAllValues().get(1).key());
        assertEquals(1, dnsLookups.get());
        verify(s3ClientFactory, times(1))
                .create(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void shutdownDoesNotWaitForInFlightDeleteAndClosesClientAfterLeaseRelease() throws Exception {
        when(systemConfigService.loadConfig(any(String[].class))).thenReturn(enabledConfig());
        when(s3ClientFactory.create(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(s3Client);
        CountDownLatch deleteStarted = new CountDownLatch(1);
        CountDownLatch allowDeleteToFinish = new CountDownLatch(1);
        doAnswer(invocation -> {
            deleteStarted.countDown();
            assertTrue(allowDeleteToFinish.await(3, TimeUnit.SECONDS));
            return null;
        }).when(s3Client).deleteObject(any(DeleteObjectRequest.class));
        S3ObjectStorageService service = service(host ->
                new InetAddress[]{InetAddress.getByAddress(new byte[]{1, 1, 1, 1})});
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        service.deleteObjectIfEnabled("resumes/resume.pdf");
        verify(s3ArchiveExecutor).execute(task.capture());
        CompletableFuture<Void> inFlight = CompletableFuture.runAsync(task.getValue());

        assertTrue(deleteStarted.await(1, TimeUnit.SECONDS));
        try {
            assertTimeoutPreemptively(Duration.ofMillis(500), service::closeClients);
            verify(s3Client, never()).close();
        } finally {
            allowDeleteToFinish.countDown();
        }
        inFlight.get(3, TimeUnit.SECONDS);
        verify(s3Client).close();
    }

    @Test
    void expiredDnsApprovalIsNotReusedWhenRefreshFails() throws Exception {
        when(systemConfigService.loadConfig(any(String[].class))).thenReturn(enabledConfig());
        when(s3ClientFactory.create(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(s3Client);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(s3ArchiveExecutor).execute(any(Runnable.class));
        AtomicInteger dnsLookups = new AtomicInteger();
        AtomicLong clock = new AtomicLong();
        S3ObjectStorageService service = service(host -> {
            if (dnsLookups.getAndIncrement() == 0) {
                return new InetAddress[]{InetAddress.getByAddress(new byte[]{1, 1, 1, 1})};
            }
            throw new java.net.UnknownHostException(host);
        }, clock::get);

        service.deleteObjectIfEnabled("resumes/first.pdf");
        clock.set(Duration.ofSeconds(65).toNanos());
        service.deleteObjectIfEnabled("resumes/second.pdf");
        clock.set(Duration.ofSeconds(71).toNanos());
        service.deleteObjectIfEnabled("resumes/third.pdf");

        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
        assertEquals(2, dnsLookups.get());
        verify(s3ClientFactory, times(1))
                .create(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void configurationChangeNeverUsesStaleDnsApproval() throws Exception {
        AtomicReference<Map<String, String>> config = new AtomicReference<>(enabledConfig());
        when(systemConfigService.loadConfig(any(String[].class))).thenAnswer(invocation -> config.get());
        when(s3ClientFactory.create(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(s3Client);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(s3ArchiveExecutor).execute(any(Runnable.class));
        S3ObjectStorageService service = service(host -> {
            if (host.equals("s3.example.test")) {
                return new InetAddress[]{InetAddress.getByAddress(new byte[]{1, 1, 1, 1})};
            }
            throw new java.net.UnknownHostException(host);
        });

        service.deleteObjectIfEnabled("resumes/first.pdf");
        config.set(enabledConfig("https://changed.example.test:9000"));
        service.deleteObjectIfEnabled("resumes/second.pdf");

        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void concurrentEndpointLookupsDoNotHoldThePublicationLock() throws Exception {
        when(systemConfigService.loadConfig(any(String[].class))).thenReturn(enabledConfig());
        when(s3ClientFactory.create(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(s3Client);
        List<CompletableFuture<Void>> tasks = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            tasks.add(CompletableFuture.runAsync(invocation.getArgument(0)));
            return null;
        }).when(s3ArchiveExecutor).execute(any(Runnable.class));
        CountDownLatch lookupsStarted = new CountDownLatch(2);
        CountDownLatch releaseLookups = new CountDownLatch(1);
        S3ObjectStorageService service = service(host -> {
            lookupsStarted.countDown();
            try {
                if (!releaseLookups.await(3, TimeUnit.SECONDS)) {
                    throw new java.net.UnknownHostException("timed out waiting for concurrent lookup");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new java.net.UnknownHostException("lookup interrupted");
            }
            return new InetAddress[]{InetAddress.getByAddress(new byte[]{1, 1, 1, 1})};
        });

        service.deleteObjectIfEnabled("resumes/first.pdf");
        service.deleteObjectIfEnabled("resumes/second.pdf");
        try {
            assertTrue(lookupsStarted.await(1, TimeUnit.SECONDS));
        } finally {
            releaseLookups.countDown();
        }
        for (CompletableFuture<Void> task : tasks) {
            task.get(3, TimeUnit.SECONDS);
        }

        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void queueRejectionStillAttemptsDeletionWithoutFailingTheCommittedRequest() {
        S3ObjectStorageService service = new S3ObjectStorageService(systemConfigService, s3ArchiveExecutor);
        doThrow(new TaskRejectedException("full")).when(s3ArchiveExecutor).execute(any(Runnable.class));
        when(systemConfigService.loadConfig(any(String[].class))).thenReturn(Map.of("S3_ENABLED", "false"));

        assertDoesNotThrow(() -> service.deleteObjectIfEnabled("resumes/resume.pdf"));

        verify(systemConfigService).loadConfig(any(String[].class));
    }

    @Test
    void retriesTransientObjectDeletionThreeTimes() throws Exception {
        when(systemConfigService.loadConfig(any(String[].class))).thenReturn(enabledConfig());
        when(s3ClientFactory.create(anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(s3Client);
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("temporary-1"))
                .thenThrow(new RuntimeException("temporary-2"))
                .thenReturn(DeleteObjectResponse.builder().build());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(s3ArchiveExecutor).execute(any(Runnable.class));
        S3ObjectStorageService service = service(host ->
                new InetAddress[]{InetAddress.getByAddress(new byte[]{1, 1, 1, 1})});

        service.deleteObjectIfEnabled("resumes/retry.pdf");

        verify(s3Client, times(3)).deleteObject(any(DeleteObjectRequest.class));
    }

    private S3ObjectStorageService service(S3EndpointValidator.AddressResolver resolver) {
        return service(resolver, () -> 0L);
    }

    private S3ObjectStorageService service(S3EndpointValidator.AddressResolver resolver,
                                           java.util.function.LongSupplier clock) {
        return new S3ObjectStorageService(systemConfigService, s3ArchiveExecutor, s3ClientFactory,
                s3PresignerFactory, resolver, clock);
    }

    private Map<String, String> enabledConfig() {
        return enabledConfig("https://s3.example.test:9000");
    }

    private Map<String, String> enabledConfig(String endpoint) {
        return Map.of(
                "S3_ENABLED", "true",
                "S3_ENDPOINT", endpoint,
                "S3_REGION", "us-east-1",
                "S3_BUCKET", "autohr",
                "S3_ACCESS_KEY_ID", "access-key",
                "S3_SECRET_ACCESS_KEY", "secret-key");
    }
}
