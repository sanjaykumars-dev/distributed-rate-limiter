--Variables
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])

--Removes all the entries that are outside the timeframe
redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)

--Count the tonumber of entries inf the current timeframe
local count = redis.call('ZCARD', key)

--If the total tonumber of requests is less than the given timeframe
if count < limit then

    -- Add a new entry
    redis.call('ZADD', key, now, now)

    --If it is new entry for that key, set EXPIRE for that key
    if count == 0 then
        redis.call('EXPIRE',key,window)
    end
    return 1

else
    return 0
end
