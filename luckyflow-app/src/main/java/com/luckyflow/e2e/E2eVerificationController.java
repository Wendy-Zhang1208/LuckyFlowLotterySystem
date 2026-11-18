package com.luckyflow.e2e;

import com.luckyflow.auth.model.VerificationChannel;
import com.luckyflow.auth.model.VerificationPurpose;
import com.luckyflow.lottery.constant.PrizeInventoryRedisKeys;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 只在 E2E Profile、回环地址和独立测试密钥同时满足时读取本地验证码。 */
@RestController
@Profile("e2e")
@ConditionalOnProperty(prefix = "luckyflow.e2e", name = "enabled", havingValue = "true")
@RequestMapping("/api/e2e")
public class E2eVerificationController {
    private final E2eVerificationInbox inbox;
    private final StringRedisTemplate redis;
    private final String accessToken;

    public E2eVerificationController(E2eVerificationInbox inbox, StringRedisTemplate redis,
            @Value("${LUCKYFLOW_E2E_ACCESS_TOKEN:}") String accessToken) {
        if (accessToken == null || accessToken.length() < 32) {
            throw new IllegalStateException("LUCKYFLOW_E2E_ACCESS_TOKEN must be set for E2E profile");
        }
        this.inbox = inbox;
        this.redis = redis;
        this.accessToken = accessToken;
    }

    @PostMapping("/verification-code")
    public CodeResponse code(@RequestBody CodeRequest request,
            @RequestHeader(value = "X-LuckyFlow-E2E-Token", required = false) String provided,
            HttpServletRequest servletRequest) {
        requireLocalTestToken(provided, servletRequest);
        String code = inbox.latest(request.channel(), request.purpose(), request.target());
        if (code == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return new CodeResponse(code);
    }

    @GetMapping("/inventory/{activityPrizeId}")
    public InventoryResponse inventory(@PathVariable long activityPrizeId,
            @RequestHeader(value = "X-LuckyFlow-E2E-Token", required = false) String provided,
            HttpServletRequest servletRequest) {
        requireLocalTestToken(provided, servletRequest);
        if (activityPrizeId <= 0) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        return new InventoryResponse(redis.opsForValue().get(
                PrizeInventoryRedisKeys.inventoryKey(activityPrizeId)));
    }

    private void requireLocalTestToken(String provided, HttpServletRequest servletRequest) {
        String remote = servletRequest.getRemoteAddr();
        if (!("127.0.0.1".equals(remote) || "::1".equals(remote))
                || provided == null || !MessageDigest.isEqual(
                        accessToken.getBytes(StandardCharsets.UTF_8),
                        provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    public record CodeRequest(VerificationChannel channel, VerificationPurpose purpose,
            String target) { }
    public record CodeResponse(String code) { }
    public record InventoryResponse(String remaining) { }
}
