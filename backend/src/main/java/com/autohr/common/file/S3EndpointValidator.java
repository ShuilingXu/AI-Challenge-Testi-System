package com.autohr.common.file;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

public final class S3EndpointValidator {

    private S3EndpointValidator() {
    }

    public static boolean isAllowed(String value, boolean allowHttp, boolean allowPrivateAddresses) {
        return validate(value, allowHttp, allowPrivateAddresses, InetAddress::getAllByName).allowed();
    }

    static ValidationResult validate(String value, boolean allowHttp, boolean allowPrivateAddresses,
                                     AddressResolver addressResolver) {
        URI endpoint;
        try {
            endpoint = URI.create(value == null ? "" : value.trim());
        } catch (IllegalArgumentException ex) {
            return ValidationResult.reject(ValidationFailure.INVALID_URL);
        }
        String scheme = endpoint.getScheme();
        int port = endpoint.getPort();
        if (endpoint.getHost() == null
                || !("https".equalsIgnoreCase(scheme) || (allowHttp && "http".equalsIgnoreCase(scheme)))
                || endpoint.getRawUserInfo() != null
                || endpoint.getRawFragment() != null
                || port == 0
                || port > 65535) {
            return ValidationResult.reject(ValidationFailure.INVALID_URL);
        }
        if (allowPrivateAddresses) {
            return ValidationResult.permit();
        }
        try {
            InetAddress[] addresses = addressResolver.resolve(stripIpv6Brackets(endpoint.getHost()));
            if (addresses == null || addresses.length == 0) {
                return ValidationResult.reject(ValidationFailure.DNS_FAILURE);
            }
            for (InetAddress address : addresses) {
                if (isPrivateOrLocal(address)) {
                    return ValidationResult.reject(ValidationFailure.PRIVATE_ADDRESS);
                }
            }
            return ValidationResult.permit();
        } catch (UnknownHostException ex) {
            return ValidationResult.reject(ValidationFailure.DNS_FAILURE);
        } catch (RuntimeException ex) {
            return ValidationResult.reject(ValidationFailure.DNS_FAILURE);
        }
    }

    private static String stripIpv6Brackets(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static boolean isPrivateOrLocal(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isPrivateOrLocalIpv4(bytes);
        }
        if (bytes.length != 16) {
            return true;
        }
        if (isIpv4MappedIpv6(bytes)) {
            return isPrivateOrLocalIpv4(Arrays.copyOfRange(bytes, 12, 16));
        }
        return (bytes[0] & 0xfe) == 0xfc;
    }

    private static boolean isIpv4MappedIpv6(byte[] bytes) {
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private static boolean isPrivateOrLocalIpv4(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 0
                || first == 10
                || first == 127
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || first >= 224;
    }

    @FunctionalInterface
    interface AddressResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    enum ValidationFailure {
        NONE,
        INVALID_URL,
        PRIVATE_ADDRESS,
        DNS_FAILURE
    }

    record ValidationResult(boolean allowed, ValidationFailure failure) {
        private static ValidationResult permit() {
            return new ValidationResult(true, ValidationFailure.NONE);
        }

        private static ValidationResult reject(ValidationFailure failure) {
            return new ValidationResult(false, failure);
        }
    }
}
