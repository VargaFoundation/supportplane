package com.odp.supportplane.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight IP geolocation using ip-api.com (free, no API key, 45 req/min).
 * Results are cached in memory to avoid hitting rate limits.
 */
@Service
@Slf4j
public class GeoIpService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Returns "City, Country" for the given IP, or the IP itself if lookup fails.
     */
    public String lookup(String ip) {
        if (ip == null || ip.isBlank() || isPrivateIp(ip)) {
            return null;
        }

        return cache.computeIfAbsent(ip, this::doLookup);
    }

    private String doLookup(String ip) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                    "http://ip-api.com/json/" + ip + "?fields=status,country,city,regionName,isp",
                    Map.class);

            if (response != null && "success".equals(response.get("status"))) {
                String city = (String) response.get("city");
                String region = (String) response.get("regionName");
                String country = (String) response.get("country");
                String isp = (String) response.get("isp");
                String location = (city != null ? city : "") +
                        (region != null ? ", " + region : "") +
                        (country != null ? ", " + country : "");
                if (isp != null && !isp.isBlank()) {
                    location += " (" + isp + ")";
                }
                log.info("GeoIP: {} -> {}", ip, location);
                return location;
            }
        } catch (Exception e) {
            log.warn("GeoIP lookup failed for {}: {}", ip, e.getMessage());
        }
        return null;
    }

    private boolean isPrivateIp(String ip) {
        return ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.16.")
                || ip.startsWith("172.17.") || ip.startsWith("172.18.") || ip.startsWith("172.19.")
                || ip.startsWith("172.2") || ip.startsWith("172.3")
                || ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1");
    }
}
