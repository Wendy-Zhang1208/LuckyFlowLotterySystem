-- KEYS: code, cooldown, target-hour, target-day, ip-hour
-- ARGV: codeHash, codeTtlMs, cooldownMs, hourlyLimit, dayLimit, ipHourlyLimit
if redis.call('EXISTS', KEYS[2]) == 1 then
    return -1
end

local hourCount = tonumber(redis.call('GET', KEYS[3]) or '0')
if hourCount >= tonumber(ARGV[4]) then
    return -2
end

local dayCount = tonumber(redis.call('GET', KEYS[4]) or '0')
if dayCount >= tonumber(ARGV[5]) then
    return -3
end

local ipHourCount = tonumber(redis.call('GET', KEYS[5]) or '0')
if ipHourCount >= tonumber(ARGV[6]) then
    return -4
end

redis.call('HSET', KEYS[1], 'hash', ARGV[1], 'attempts', '0')
redis.call('PEXPIRE', KEYS[1], ARGV[2])
redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])

hourCount = redis.call('INCR', KEYS[3])
if hourCount == 1 then redis.call('PEXPIRE', KEYS[3], 3600000) end
dayCount = redis.call('INCR', KEYS[4])
if dayCount == 1 then redis.call('PEXPIRE', KEYS[4], 86400000) end
ipHourCount = redis.call('INCR', KEYS[5])
if ipHourCount == 1 then redis.call('PEXPIRE', KEYS[5], 3600000) end

return 1
