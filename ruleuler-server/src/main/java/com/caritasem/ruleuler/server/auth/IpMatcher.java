package com.caritasem.ruleuler.server.auth;

import java.net.InetAddress;
import java.util.List;

public final class IpMatcher {

    private IpMatcher() {}

    public static boolean matches(String remoteAddr, List<String> allowedPatterns) {
        if (remoteAddr == null || allowedPatterns == null || allowedPatterns.isEmpty()) {
            return false;
        }
        for (String pattern : allowedPatterns) {
            if (pattern.contains("/")) {
                if (cidrMatches(remoteAddr, pattern)) {
                    return true;
                }
            } else {
                if (remoteAddr.equals(pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean cidrMatches(String ipStr, String cidr) {
        try {
            String[] parts = cidr.split("/");
            InetAddress network = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            InetAddress ip = InetAddress.getByName(ipStr);

            byte[] networkBytes = network.getAddress();
            byte[] ipBytes = ip.getAddress();

            if (networkBytes.length != ipBytes.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (networkBytes[i] != ipBytes[i]) {
                    return false;
                }
            }

            if (remainingBits > 0 && fullBytes < networkBytes.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((networkBytes[fullBytes] & mask) != (ipBytes[fullBytes] & mask)) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
