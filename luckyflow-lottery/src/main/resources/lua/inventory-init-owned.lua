-- 三个 key 的归属登记与库存初始化原子完成；不能留下“已写库存但无恢复线索”的窗口。
if redis.call('EXISTS', KEYS[1]) ~= 0 then return 0 end
local registryType = redis.call('TYPE', KEYS[3]).ok
if registryType ~= 'none' and registryType ~= 'set' then return -1 end
redis.call('SET', KEYS[1], ARGV[1])
redis.call('SET', KEYS[2], ARGV[2])
redis.call('SADD', KEYS[3], ARGV[3])
return 1
