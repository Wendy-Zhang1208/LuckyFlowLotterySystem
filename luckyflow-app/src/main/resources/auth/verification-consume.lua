-- KEYS: code
-- ARGV: suppliedHash, maxAttempts
if redis.call('EXISTS', KEYS[1]) == 0 then
    return 0
end

local expected = redis.call('HGET', KEYS[1], 'hash')
if expected == ARGV[1] then
    redis.call('DEL', KEYS[1])
    return 1
end

local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
if attempts >= tonumber(ARGV[2]) then
    redis.call('DEL', KEYS[1])
    return -2
end
return -1
