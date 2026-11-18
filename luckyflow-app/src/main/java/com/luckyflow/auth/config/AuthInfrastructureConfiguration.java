package com.luckyflow.auth.config;

import com.luckyflow.auth.verification.UnconfiguredVerificationSmsSender;
import com.luckyflow.auth.verification.VerificationSmsSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 认证基础设施及未配置短信供应商时的安全失败实现。 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthInfrastructureConfiguration {

    @Bean
    @ConditionalOnMissingBean(VerificationSmsSender.class)
    public VerificationSmsSender unconfiguredVerificationSmsSender() {
        return new UnconfiguredVerificationSmsSender();
    }
}
