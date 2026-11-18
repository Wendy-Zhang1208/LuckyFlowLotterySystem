package com.luckyflow.auth.verification;

import com.luckyflow.auth.config.AuthProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/** Lua 保证验证码签发限流和一次性消费在并发下均为单个 Redis 原子操作。 */
@Component
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final String PREFIX = "luckyflow:auth:{verification}:";

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;
    private final DefaultRedisScript<Long> issueScript;
    private final DefaultRedisScript<Long> consumeScript;
    private final DefaultRedisScript<Long> revokeScript;

    public RedisVerificationCodeStore(StringRedisTemplate redisTemplate, AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.issueScript = script("auth/verification-issue.lua");
        this.consumeScript = script("auth/verification-consume.lua");
        this.revokeScript = script("auth/verification-revoke.lua");
    }

    @Override
    public IssueResult issue(String scopeHash, String codeHash, String clientHash) {
        AuthProperties.Verification config = properties.getVerification();
        Long result = redisTemplate.execute(
                issueScript,
                List.of(
                        codeKey(scopeHash),
                        PREFIX + "cooldown:" + scopeHash,
                        PREFIX + "target-hour:" + scopeHash,
                        PREFIX + "target-day:" + scopeHash,
                        PREFIX + "ip-hour:" + clientHash
                ),
                codeHash,
                Long.toString(config.getCodeTtl().toMillis()),
                Long.toString(config.getSendInterval().toMillis()),
                Integer.toString(config.getHourlyLimit()),
                Integer.toString(config.getDailyLimit()),
                Integer.toString(config.getIpHourlyLimit())
        );
        if (result == null) {
            throw new IllegalStateException("Verification issue script returned no result");
        }
        return switch (result.intValue()) {
            case 1 -> IssueResult.ISSUED;
            case -1 -> IssueResult.SEND_INTERVAL;
            case -2 -> IssueResult.TARGET_HOURLY_LIMIT;
            case -3 -> IssueResult.TARGET_DAILY_LIMIT;
            case -4 -> IssueResult.IP_HOURLY_LIMIT;
            default -> throw new IllegalStateException("Unknown verification issue result: " + result);
        };
    }

    @Override
    public ConsumeResult consume(String scopeHash, String suppliedCodeHash) {
        Long result = redisTemplate.execute(
                consumeScript,
                List.of(codeKey(scopeHash)),
                suppliedCodeHash,
                Integer.toString(properties.getVerification().getMaxAttempts())
        );
        if (result == null) {
            throw new IllegalStateException("Verification consume script returned no result");
        }
        return switch (result.intValue()) {
            case 1 -> ConsumeResult.CONSUMED;
            case 0 -> ConsumeResult.EXPIRED;
            case -1 -> ConsumeResult.INVALID;
            case -2 -> ConsumeResult.LOCKED;
            default -> throw new IllegalStateException("Unknown verification consume result: " + result);
        };
    }

    @Override
    public void revoke(String scopeHash, String codeHash) {
        redisTemplate.execute(revokeScript, List.of(codeKey(scopeHash)), codeHash);
    }

    private String codeKey(String scopeHash) {
        return PREFIX + "code:" + scopeHash;
    }

    private DefaultRedisScript<Long> script(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }
}
