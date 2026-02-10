package com.varshith.ratelimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for the distributed rate limiter.
 *
 * Configures:
 * - Connection pooling with Lettuce client
 * - RedisTemplate with proper serializers
 * - Lua scripts for atomic operations
 * - Transaction support
 * - Timeout and socket options for production resilience
 */
@Configuration
public class RedisConfig {

    /**
     * Configures the Redis connection factory with optimized connection pooling.
     *
     * Connection pooling is critical for performance under load:
     * - Reuses connections instead of creating new ones
     * - Max 10 connections to prevent resource exhaustion
     * - Min 2 idle connections for quick access
     * - 5 second socket timeout to detect network issues
     */
    // Redis connection factory is auto-configured by Spring Boot
    // via application.yml properties (spring.data.redis.host, spring.data.redis.port)
    // No need to manually configure it here

    /**
     * Configures RedisTemplate with proper serializers for String keys and JSON values.
     *
     * Serialization strategy:
     * - Keys: String serializer (human-readable in Redis CLI)
     * - Values: JSON serializer (preserves object structure)
     * - Hash keys/values: String serializer for simple operations
     *
     * This enables both human debugging and complex object storage.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys (readable in Redis CLI)
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Use JSON serializer for values (preserves object structure)
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        // Enable transaction support for multi-command atomicity
        template.setEnableTransactionSupport(true);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Lua script for Token Bucket algorithm.
     *
     * This script ensures atomic execution of:
     * 1. Calculate tokens to add based on elapsed time
     * 2. Add tokens to bucket (capped at max capacity)
     * 3. Check if enough tokens available
     * 4. Consume 1 token if request allowed
     * 5. Return current state
     *
     * Using Lua prevents race conditions in distributed environments where
     * multiple instances might check/update the same rate limit simultaneously.
     *
     * KEYS[1] = Redis key for the bucket (e.g., "ratelimit:bucket:apikey:endpoint")
     * ARGV[1] = max capacity (bucket size)
     * ARGV[2] = refill rate (tokens per second)
     * ARGV[3] = current timestamp (seconds)
     * ARGV[4] = TTL for key (seconds)
     *
     * Returns: {allowed (0/1), remaining_tokens, reset_at}
     */
    @Bean
    public DefaultRedisScript<java.util.List> tokenBucketScript() {
        DefaultRedisScript<java.util.List> script = new DefaultRedisScript<>();

        String luaScript = """
            -- Get current bucket state
            local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'last_refill')
            local tokens = tonumber(bucket[1])
            local last_refill = tonumber(bucket[2])

            local max_capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])

            -- Initialize bucket if it doesn't exist
            if tokens == nil then
                tokens = max_capacity
                last_refill = now
            end

            -- Calculate tokens to add based on elapsed time
            local elapsed = now - last_refill
            local tokens_to_add = elapsed * refill_rate

            -- Add tokens to bucket (capped at max capacity)
            tokens = math.min(max_capacity, tokens + tokens_to_add)

            -- Update last refill time
            last_refill = now

            -- Check if request can be allowed (need at least 1 token)
            local allowed = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            end

            -- Update bucket state in Redis
            redis.call('HMSET', KEYS[1], 'tokens', tokens, 'last_refill', last_refill)
            redis.call('EXPIRE', KEYS[1], ttl)

            -- Calculate reset time (when bucket will be full again)
            local tokens_needed = max_capacity - tokens
            local reset_at = now + (tokens_needed / refill_rate)

            -- Return: {allowed, remaining_tokens, reset_at}
            return {allowed, math.floor(tokens), math.floor(reset_at)}
            """;

        script.setScriptText(luaScript);
        script.setResultType(java.util.List.class);
        return script;
    }

    /**
     * Lua script for Sliding Window algorithm.
     *
     * Uses Redis sorted set with timestamps as scores:
     * 1. Remove expired entries (outside time window)
     * 2. Count remaining entries in window
     * 3. Check if under limit
     * 4. Add current request if allowed
     * 5. Set expiration on the key
     *
     * More accurate than fixed window, prevents burst traffic at boundaries.
     *
     * KEYS[1] = Redis sorted set key
     * ARGV[1] = window size (seconds)
     * ARGV[2] = max requests in window
     * ARGV[3] = current timestamp (milliseconds for precision)
     * ARGV[4] = request ID (unique identifier)
     *
     * Returns: {allowed (0/1), remaining_requests, window_reset_at}
     */
    @Bean
    public DefaultRedisScript<java.util.List> slidingWindowScript() {
        DefaultRedisScript<java.util.List> script = new DefaultRedisScript<>();

        String luaScript = """
            local window_size = tonumber(ARGV[1])
            local max_requests = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local request_id = ARGV[4]

            -- Calculate window start time
            local window_start = now - (window_size * 1000)

            -- Remove entries outside current window
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, window_start)

            -- Count requests in current window
            local current_count = redis.call('ZCARD', KEYS[1])

            local allowed = 0
            local remaining = max_requests - current_count

            -- Check if request can be allowed
            if current_count < max_requests then
                -- Add current request to sorted set
                redis.call('ZADD', KEYS[1], now, request_id)
                allowed = 1
                remaining = remaining - 1
            end

            -- Set expiration (window size + buffer)
            redis.call('EXPIRE', KEYS[1], window_size + 10)

            -- Calculate reset time (end of current window)
            local reset_at = math.floor((now + (window_size * 1000)) / 1000)

            return {allowed, remaining, reset_at}
            """;

        script.setScriptText(luaScript);
        script.setResultType(java.util.List.class);
        return script;
    }

    /**
     * Lua script for Fixed Window algorithm.
     *
     * Simple counter with TTL that resets at fixed intervals:
     * 1. Get or initialize counter
     * 2. Check if under limit
     * 3. Increment if allowed
     * 4. Set TTL on first request
     *
     * Simplest implementation, but allows burst traffic at window boundaries.
     *
     * KEYS[1] = Redis key for counter
     * ARGV[1] = max requests
     * ARGV[2] = window size (seconds)
     *
     * Returns: {allowed (0/1), remaining_requests, window_reset_at}
     */
    @Bean
    public DefaultRedisScript<java.util.List> fixedWindowScript() {
        DefaultRedisScript<java.util.List> script = new DefaultRedisScript<>();

        String luaScript = """
            local max_requests = tonumber(ARGV[1])
            local window_size = tonumber(ARGV[2])

            -- Get current count
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')

            local allowed = 0
            local remaining = max_requests - current

            -- Check if request can be allowed
            if current < max_requests then
                -- Increment counter
                current = redis.call('INCR', KEYS[1])
                allowed = 1
                remaining = max_requests - current

                -- Set TTL only on first request (when count was 0)
                if current == 1 then
                    redis.call('EXPIRE', KEYS[1], window_size)
                end
            end

            -- Get TTL to calculate reset time
            local now = tonumber(ARGV[3] or redis.call('TIME')[1])
            local ttl = redis.call('TTL', KEYS[1])
            local reset_at = now + window_size
            if ttl > 0 then
                reset_at = now + ttl
            end

            return {allowed, remaining, reset_at}
            """;

        script.setScriptText(luaScript);
        script.setResultType(java.util.List.class);
        return script;
    }

    /**
     * Lua script for Leaky Bucket algorithm.
     *
     * Bucket holds requests like water, which leaks at constant rate:
     * 1. Calculate leaked water based on elapsed time
     * 2. Update water level
     * 3. Check if bucket can accept 1 more unit
     * 4. Add 1 unit if allowed
     * 5. Set expiration
     *
     * Provides strict rate enforcement with constant outflow.
     *
     * KEYS[1] = Redis key for bucket
     * ARGV[1] = capacity (max water level)
     * ARGV[2] = leak rate (units per second)
     * ARGV[3] = current timestamp (seconds)
     * ARGV[4] = TTL (seconds)
     *
     * Returns: {allowed (0/1), remaining_capacity, reset_at}
     */
    @Bean
    public DefaultRedisScript<java.util.List> leakyBucketScript() {
        DefaultRedisScript<java.util.List> script = new DefaultRedisScript<>();

        String luaScript = """
            -- Get current bucket state
            local bucket = redis.call('HMGET', KEYS[1], 'water_level', 'last_leak_time')
            local water_level = tonumber(bucket[1])
            local last_leak_time = tonumber(bucket[2])

            local capacity = tonumber(ARGV[1])
            local leak_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])

            -- Initialize bucket if it doesn't exist
            if water_level == nil then
                water_level = 0
                last_leak_time = now
            end

            -- Calculate leaked water based on elapsed time
            local elapsed = now - last_leak_time
            local leaked = elapsed * leak_rate

            -- Update water level (can't go below 0)
            water_level = math.max(0, water_level - leaked)
            last_leak_time = now

            -- Check if bucket can accept 1 more unit
            local allowed = 0
            local remaining = capacity - water_level

            if water_level < capacity then
                -- Add 1 unit to bucket
                water_level = water_level + 1
                allowed = 1
                remaining = capacity - water_level
            else
                remaining = 0
            end

            -- Update bucket state in Redis
            redis.call('HMSET', KEYS[1], 'water_level', water_level, 'last_leak_time', last_leak_time)
            redis.call('EXPIRE', KEYS[1], ttl)

            -- Calculate reset time (when bucket will be empty)
            local time_to_empty = water_level / leak_rate
            local reset_at = now + time_to_empty

            -- Return: {allowed, remaining_capacity, reset_at}
            return {allowed, math.floor(remaining), math.floor(reset_at)}
            """;

        script.setScriptText(luaScript);
        script.setResultType(java.util.List.class);
        return script;
    }
}
