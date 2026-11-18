-- marker 必须等于本次 attempt；新发布或其他合法库存绝不可被旧恢复任务删除。
local ids = redis.call('SMEMBERS', KEYS[1])
for _, id in ipairs(ids) do
    local ownerKey = ARGV[2] .. id
    if redis.call('GET', ownerKey) == ARGV[1] then
        if ARGV[4] == '1' then redis.call('DEL', ARGV[3] .. id) end
        redis.call('DEL', ownerKey)
    end
end
redis.call('DEL', KEYS[1])
return #ids
