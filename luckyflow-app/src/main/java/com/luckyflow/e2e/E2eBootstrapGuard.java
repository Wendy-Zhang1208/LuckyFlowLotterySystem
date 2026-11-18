package com.luckyflow.e2e;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 在 Rabbit/Redis 连接和 Web 端点创建前拒绝共享基础设施配置。 */
@Component
@Profile("e2e")
public class E2eBootstrapGuard implements BeanFactoryPostProcessor, EnvironmentAware {
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        if (profiles.size() != 1 || !profiles.contains("e2e")
                || !"true".equals(environment.getProperty("luckyflow.e2e.enabled"))
                || !"127.0.0.1".equals(environment.getProperty("server.address"))
                || !"never".equals(environment.getProperty("spring.sql.init.mode"))
                || !"jdbc:mysql://127.0.0.1:3306/lottery_e2e_20261015".equals(
                        environment.getProperty("spring.datasource.url"))
                || !"15".equals(environment.getProperty("spring.data.redis.database"))
                || !"luckyflow".equals(environment.getProperty("spring.rabbitmq.virtual-host"))) {
            throw new IllegalStateException("E2E requires explicit isolated bootstrap configuration");
        }
    }
}
