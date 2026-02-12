export const tokenBucketScript = `
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
`;

export const slidingWindowScript = `
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
`;

export const fixedWindowScript = `
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
`;

export const leakyBucketScript = `
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
`;
