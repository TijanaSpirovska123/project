package com.example.marketing.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformRawDataCache {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.cache.campaign.ttl-seconds:300}")
    private long campaignTtl;

    @Value("${app.cache.adset.ttl-seconds:300}")
    private long adSetTtl;

    @Value("${app.cache.ad.ttl-seconds:120}")
    private long adTtl;

    @Value("${app.cache.creative.ttl-seconds:180}")
    private long creativeTtl;

    // Key pattern: rawdata:{type}:{platform}:{adAccountId}:{externalId}
    private String key(String type, String platform, String adAccountId, String externalId) {
        return String.format("rawdata:%s:%s:%s:%s", type, platform, adAccountId, externalId);
    }

    // Key pattern for whole-list caches: rawdata:{type}:{platform}:{adAccountId}
    private String listKey(String type, String platform, String adAccountId) {
        return String.format("rawdata:%s:%s:%s", type, platform, adAccountId);
    }

    public void putCampaign(String platform, String adAccountId,
                            String externalId, Map<String, Object> raw) {
        try {
            redisTemplate.opsForValue().set(
                    key("campaign", platform, adAccountId, externalId),
                    raw, Duration.ofSeconds(campaignTtl));
        } catch (Exception e) {
            log.warn("Redis unavailable, skipping campaign cache write for {}: {}", externalId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCampaign(String platform, String adAccountId, String externalId) {
        try {
            Object val = redisTemplate.opsForValue()
                    .get(key("campaign", platform, adAccountId, externalId));
            return val instanceof Map ? (Map<String, Object>) val : null;
        } catch (Exception e) {
            log.warn("Redis unavailable, cache miss for campaign {}: {}", externalId, e.getMessage());
            return null;
        }
    }

    public void putAdSet(String platform, String adAccountId,
                         String externalId, Map<String, Object> raw) {
        try {
            redisTemplate.opsForValue().set(
                    key("adset", platform, adAccountId, externalId),
                    raw, Duration.ofSeconds(adSetTtl));
        } catch (Exception e) {
            log.warn("Redis unavailable, skipping adset cache write for {}: {}", externalId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAdSet(String platform, String adAccountId, String externalId) {
        try {
            Object val = redisTemplate.opsForValue()
                    .get(key("adset", platform, adAccountId, externalId));
            return val instanceof Map ? (Map<String, Object>) val : null;
        } catch (Exception e) {
            log.warn("Redis unavailable, cache miss for adset {}: {}", externalId, e.getMessage());
            return null;
        }
    }

    public void putAd(String platform, String adAccountId,
                      String externalId, Map<String, Object> raw) {
        try {
            redisTemplate.opsForValue().set(
                    key("ad", platform, adAccountId, externalId),
                    raw, Duration.ofSeconds(adTtl));
        } catch (Exception e) {
            log.warn("Redis unavailable, skipping ad cache write for {}: {}", externalId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAd(String platform, String adAccountId, String externalId) {
        try {
            Object val = redisTemplate.opsForValue()
                    .get(key("ad", platform, adAccountId, externalId));
            return val instanceof Map ? (Map<String, Object>) val : null;
        } catch (Exception e) {
            log.warn("Redis unavailable, cache miss for ad {}: {}", externalId, e.getMessage());
            return null;
        }
    }

    public void putCreativesList(String platform, String adAccountId, List<Map<String, Object>> data) {
        try {
            redisTemplate.opsForValue().set(
                    listKey("creatives-list", platform, adAccountId),
                    data, Duration.ofSeconds(creativeTtl));
        } catch (Exception e) {
            log.warn("Redis unavailable, skipping creatives-list cache write for {}: {}", adAccountId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCreativesList(String platform, String adAccountId) {
        try {
            Object val = redisTemplate.opsForValue().get(listKey("creatives-list", platform, adAccountId));
            return val instanceof List ? (List<Map<String, Object>>) val : null;
        } catch (Exception e) {
            log.warn("Redis unavailable, cache miss for creatives-list {}: {}", adAccountId, e.getMessage());
            return null;
        }
    }

    public void evictCreativesList(String platform, String adAccountId) {
        try {
            redisTemplate.delete(listKey("creatives-list", platform, adAccountId));
        } catch (Exception e) {
            log.warn("Redis unavailable, skipping creatives-list eviction: {}", e.getMessage());
        }
    }

    public void evictCampaigns(String platform, String adAccountId) {
        try {
            Set<String> keys = redisTemplate.keys(
                    "rawdata:campaign:" + platform + ":" + adAccountId + ":*");
            if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        } catch (Exception e) {
            log.warn("Redis unavailable, skipping campaign eviction: {}", e.getMessage());
        }
    }
}
