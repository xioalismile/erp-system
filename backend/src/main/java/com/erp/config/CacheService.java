package com.erp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 缓存服务 - 自动检测Redis可用性
 * Redis可用时用Redis, 不可用时降级为内存Map
 * 这样开发模式(不装Redis)也能正常运行
 */
@Slf4j
@Service
public class CacheService {

    private final StringRedisTemplate redisTemplate;
    private final Map<String, ExpiryValue> memoryStore = new ConcurrentHashMap<>();
    private boolean redisAvailable;

    public CacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        try {
            redisTemplate.opsForValue().get("__test__");
            this.redisAvailable = true;
            log.info("CacheService: Redis connected.");
        } catch (Exception e) {
            this.redisAvailable = false;
            log.warn("CacheService: Redis not available, using in-memory cache.");
        }
    }

    public void set(String key, String value, long timeout, TimeUnit unit) {
        if (redisAvailable) {
            try {
                redisTemplate.opsForValue().set(key, value, timeout, unit);
                return;
            } catch (Exception e) {
                redisAvailable = false;
            }
        }
        memoryStore.put(key, new ExpiryValue(value, System.currentTimeMillis() + unit.toMillis(timeout)));
    }

    /**
     * 永久保存（无过期时间）：Redis 无 TTL 写入；内存模式过期时间取 Long.MAX_VALUE。
     * 用途：AI 小助手会话记忆永久保留（2026-10-13 用户要求）——服务重启后同会话仍能续上。
     */
    public void setPersistent(String key, String value) {
        if (redisAvailable) {
            try {
                redisTemplate.opsForValue().set(key, value);
                return;
            } catch (Exception e) {
                redisAvailable = false;
            }
        }
        memoryStore.put(key, new ExpiryValue(value, Long.MAX_VALUE));
    }

    public String get(String key) {
        if (redisAvailable) {
            try {
                return redisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                redisAvailable = false;
            }
        }
        ExpiryValue v = memoryStore.get(key);
        if (v == null) return null;
        if (System.currentTimeMillis() > v.expireAt) {
            memoryStore.remove(key);
            return null;
        }
        return v.value;
    }

    public void delete(String key) {
        if (redisAvailable) {
            try { redisTemplate.delete(key); return; } catch (Exception e) { redisAvailable = false; }
        }
        memoryStore.remove(key);
    }

    public Long increment(String key) {
        if (redisAvailable) {
            try { return redisTemplate.opsForValue().increment(key); } catch (Exception e) { redisAvailable = false; }
        }
        ExpiryValue v = memoryStore.compute(key, (k, old) -> {
            long count = old == null ? 1 : Long.parseLong(old.value) + 1;
            long expireAt = old != null ? old.expireAt : System.currentTimeMillis() + 60000;
            return new ExpiryValue(String.valueOf(count), expireAt);
        });
        return Long.parseLong(v.value);
    }

    public void expire(String key, long timeout, TimeUnit unit) {
        if (redisAvailable) {
            try { redisTemplate.expire(key, timeout, unit); return; } catch (Exception e) { redisAvailable = false; }
        }
        ExpiryValue v = memoryStore.get(key);
        if (v != null) v.expireAt = System.currentTimeMillis() + unit.toMillis(timeout);
    }

    private static class ExpiryValue {
        String value;
        long expireAt;
        ExpiryValue(String value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }
}
