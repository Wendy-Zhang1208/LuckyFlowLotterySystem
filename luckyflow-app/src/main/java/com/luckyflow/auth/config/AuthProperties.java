package com.luckyflow.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** 身份令牌、验证码和浏览器来源的外部化配置。 */
@ConfigurationProperties(prefix = "luckyflow.auth")
public class AuthProperties {

    private final Jwt jwt = new Jwt();
    private final Verification verification = new Verification();
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));

    public Jwt getJwt() {
        return jwt;
    }

    public Verification getVerification() {
        return verification;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public static class Jwt {
        private String issuer = "luckyflow";
        private String secretBase64;
        private Duration accessTokenTtl = Duration.ofMinutes(30);

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getSecretBase64() {
            return secretBase64;
        }

        public void setSecretBase64(String secretBase64) {
            this.secretBase64 = secretBase64;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }
    }

    public static class Verification {
        private String pepper;
        private Duration codeTtl = Duration.ofMinutes(5);
        private Duration sendInterval = Duration.ofSeconds(60);
        private int maxAttempts = 5;
        private int hourlyLimit = 5;
        private int dailyLimit = 20;
        private int ipHourlyLimit = 30;

        public String getPepper() {
            return pepper;
        }

        public void setPepper(String pepper) {
            this.pepper = pepper;
        }

        public Duration getCodeTtl() {
            return codeTtl;
        }

        public void setCodeTtl(Duration codeTtl) {
            this.codeTtl = codeTtl;
        }

        public Duration getSendInterval() {
            return sendInterval;
        }

        public void setSendInterval(Duration sendInterval) {
            this.sendInterval = sendInterval;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getHourlyLimit() {
            return hourlyLimit;
        }

        public void setHourlyLimit(int hourlyLimit) {
            this.hourlyLimit = hourlyLimit;
        }

        public int getDailyLimit() {
            return dailyLimit;
        }

        public void setDailyLimit(int dailyLimit) {
            this.dailyLimit = dailyLimit;
        }

        public int getIpHourlyLimit() {
            return ipHourlyLimit;
        }

        public void setIpHourlyLimit(int ipHourlyLimit) {
            this.ipHourlyLimit = ipHourlyLimit;
        }
    }
}
