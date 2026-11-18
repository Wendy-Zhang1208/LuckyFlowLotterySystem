-- 仅撤销当前发送尝试写入的验证码，避免删除随后签发的新验证码。
if redis.call('HGET', KEYS[1], 'hash') == ARGV[1] then
    return redis.call('DEL', KEYS[1])
end
return 0
