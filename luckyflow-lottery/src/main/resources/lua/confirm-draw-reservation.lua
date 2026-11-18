-- KEYS[1]：全局 reservation key。
-- ARGV[1..5]：RESERVED、CONFIRMED、REJECTED、RELEASED、CANCELLED 完整协议值。
local marker = redis.call('GET', KEYS[1])

if not marker then
    -- MySQL SUCCESS 是唯一成功事实。marker 因 Redis 数据丢失而缺失时只重建终止凭证，
    -- 绝不修改资源数值，从而阻止迟到的旧 reserve 再次扣减。
    redis.call('SET', KEYS[1], ARGV[2])
    return 1
end

if marker == ARGV[2] then
    -- CONFIRMED 在没有可证明的在途命令时间上界前保持持久，不能用固定 TTL 换取内存回收。
    redis.call('PERSIST', KEYS[1])
    return 0
end

if marker == ARGV[1] then
    redis.call('SET', KEYS[1], ARGV[2])
    return 1
end

if marker == ARGV[3] or marker == ARGV[4] or marker == ARGV[5] then
    return -4
end

-- 相同 reservationId 被绑定到其他资源，或 marker 包含未知协议值。
return -2
