package com.ledgercore.fraud.adapter.redis;

import com.ledgercore.fraud.application.VelocityCounter;
import com.ledgercore.fraud.application.VelocityUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Sliding window over a sorted set scored by epoch millis. The script runs atomically in Redis, so the
 * trim, insert and count cannot interleave with another request for the same account.
 */
@Component
class RedisVelocityCounter implements VelocityCounter {

    private static final RedisScript<Long> SLIDING_WINDOW = RedisScript.of("""
        redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1] - ARGV[2])
        redis.call('ZADD', KEYS[1], ARGV[1], ARGV[3])
        local count = redis.call('ZCARD', KEYS[1])
        redis.call('PEXPIRE', KEYS[1], ARGV[2])
        return count
        """, Long.class);

    private final StringRedisTemplate redis;

    RedisVelocityCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long recordAndCount(String key, String member, Instant at, Duration window) {
        try {
            Long count = redis.execute(SLIDING_WINDOW, List.of(key),
                Long.toString(at.toEpochMilli()), Long.toString(window.toMillis()), member);
            if (count == null) {
                throw new VelocityUnavailableException("Redis returned no result", null);
            }
            return count;
        } catch (DataAccessException e) {
            throw new VelocityUnavailableException("Redis unavailable", e);
        }
    }
}
