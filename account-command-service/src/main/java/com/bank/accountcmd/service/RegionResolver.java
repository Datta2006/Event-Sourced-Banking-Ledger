package com.bank.accountcmd.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline registration-IP → region lookup (v2 §7.1).
 *
 * <p>A sample of the public-domain <a href="https://github.com/sapics/ip-location-db">ip-location-db</a>
 * CIDR→country dataset is shipped as {@code ip-region-sample.csv}; the full dataset can be
 * dropped in without changing code. Private, loopback and unknown addresses fall back to
 * {@code app.region.default} so local demos always resolve.</p>
 */
@Component
public class RegionResolver {

    private static final Logger log = LoggerFactory.getLogger(RegionResolver.class);
    private record Cidr(long network, int prefix, String region) {
    }

    private final List<Cidr> ranges = new ArrayList<>();
    private final String fallback;

    public RegionResolver(@Value("${app.region.default:APAC}") String fallback) {
        this.fallback = fallback;
    }

    @PostConstruct
    void load() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("ip-region-sample.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length < 2) {
                    continue;
                }
                String[] cidr = parts[0].trim().split("/");
                long network = ipToLong(cidr[0]);
                int prefix = Integer.parseInt(cidr[1].trim());
                ranges.add(new Cidr(network, prefix, parts[1].trim().toUpperCase()));
            }
            log.info("Loaded {} offline IP→region ranges", ranges.size());
        } catch (Exception e) {
            log.warn("Could not load ip-region-sample.csv, every address will use {}: {}",
                    fallback, e.getMessage());
        }
    }

    public String resolve(String ip) {
        if (ip == null || ip.isBlank()) {
            return fallback;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isAnyLocalAddress()
                    || !(address instanceof Inet4Address)) {
                return fallback;
            }
            long value = ipToLong(ip);
            for (Cidr range : ranges) {
                long mask = range.prefix() == 0 ? 0L : (-1L << (32 - range.prefix())) & 0xFFFFFFFFL;
                if ((value & mask) == (range.network() & mask)) {
                    return range.region();
                }
            }
        } catch (Exception e) {
            log.debug("Unresolvable registration IP {}: {}", ip, e.getMessage());
        }
        return fallback;
    }

    static long ipToLong(String ip) {
        String[] octets = ip.trim().split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("Not an IPv4 address: " + ip);
        }
        long value = 0;
        for (String octet : octets) {
            int part = Integer.parseInt(octet);
            if (part < 0 || part > 255) {
                throw new IllegalArgumentException("Bad IPv4 octet: " + ip);
            }
            value = (value << 8) | part;
        }
        return value & 0xFFFFFFFFL;
    }
}
