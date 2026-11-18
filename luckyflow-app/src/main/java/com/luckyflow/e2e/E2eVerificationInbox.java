package com.luckyflow.e2e;

import com.luckyflow.auth.model.VerificationChannel;
import com.luckyflow.auth.model.VerificationPurpose;
import com.luckyflow.auth.verification.VerificationMessageSender;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 隔离 E2E 专用发送器：验证码仍由原服务签发、限流和一次性消费，不投递到外部。 */
@Component
@Primary
@Profile("e2e")
@ConditionalOnProperty(prefix = "luckyflow.e2e", name = "enabled", havingValue = "true")
public class E2eVerificationInbox implements VerificationMessageSender {
    private final ConcurrentHashMap<Key, Entry> codes = new ConcurrentHashMap<>();

    @Override
    public void send(VerificationChannel channel, VerificationPurpose purpose,
            String normalizedTarget, String code, Duration validity) {
        codes.put(new Key(channel, purpose, normalizedTarget),
                new Entry(code, Instant.now().plus(validity)));
    }

    public String latest(VerificationChannel channel, VerificationPurpose purpose, String target) {
        Entry entry = codes.get(new Key(channel, purpose, target));
        if (entry == null || !Instant.now().isBefore(entry.expiresAt())) {
            return null;
        }
        return entry.code();
    }

    private record Key(VerificationChannel channel, VerificationPurpose purpose, String target) { }
    private record Entry(String code, Instant expiresAt) { }
}
