package com.autohr.common.file;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S3EndpointValidatorTest {

    @Test
    void requiresHttpsByDefault() {
        assertFalse(S3EndpointValidator.isAllowed("http://1.1.1.1:9000", false, false));
        assertTrue(S3EndpointValidator.isAllowed("http://1.1.1.1:9000", true, false));
    }

    @Test
    void requiresExplicitPrivateAddressOptIn() {
        assertFalse(S3EndpointValidator.isAllowed("https://127.0.0.1:9000", false, false));
        assertFalse(S3EndpointValidator.isAllowed("https://10.0.0.8:9000", false, false));
        assertTrue(S3EndpointValidator.isAllowed("https://10.0.0.8:9000", false, true));
    }

    @Test
    void rejectsIpv4MappedIpv6Loopback() throws Exception {
        assertFalse(S3EndpointValidator.isAllowed("https://[::ffff:127.0.0.1]:9000", false, false));
        byte[] mappedLoopback = new byte[16];
        mappedLoopback[10] = (byte) 0xff;
        mappedLoopback[11] = (byte) 0xff;
        mappedLoopback[12] = 127;
        mappedLoopback[15] = 1;
        InetAddress rawMappedAddress = Inet6Address.getByAddress(null, mappedLoopback, -1);

        assertFalse(S3EndpointValidator.validate("https://s3.example.test", false, false,
                host -> new InetAddress[]{rawMappedAddress}).allowed());
    }

    @Test
    void rejectsNativeIpv6LocalPrivateLinkLocalAndMulticastAddresses() {
        assertFalse(S3EndpointValidator.isAllowed("https://[::1]:9000", false, false));
        assertFalse(S3EndpointValidator.isAllowed("https://[fc00::1]:9000", false, false));
        assertFalse(S3EndpointValidator.isAllowed("https://[fe80::1]:9000", false, false));
        assertFalse(S3EndpointValidator.isAllowed("https://[ff02::1]:9000", false, false));
    }

    @Test
    void reportsDnsFailureWithoutDependingOnExternalDns() {
        S3EndpointValidator.ValidationResult result = S3EndpointValidator.validate(
                "https://s3.example.test", false, false,
                host -> { throw new UnknownHostException(host); });

        assertFalse(result.allowed());
        assertEquals(S3EndpointValidator.ValidationFailure.DNS_FAILURE, result.failure());
    }

    @Test
    void validatesExplicitPortRangeBeforeDnsResolution() throws Exception {
        S3EndpointValidator.AddressResolver publicAddress = host ->
                new InetAddress[]{InetAddress.getByAddress(new byte[]{1, 1, 1, 1})};

        assertTrue(S3EndpointValidator.validate(
                "https://s3.example.test:9000", false, false, publicAddress).allowed());
        assertFalse(S3EndpointValidator.validate(
                "https://s3.example.test:0", false, false, publicAddress).allowed());
        assertFalse(S3EndpointValidator.validate(
                "https://s3.example.test:65536", false, false, publicAddress).allowed());
    }

    @Test
    void rejectsEmbeddedCredentialsEvenWhenCompatibilitySwitchesAreEnabled() {
        assertFalse(S3EndpointValidator.isAllowed("http://user:secret@127.0.0.1:9000", true, true));
    }
}
