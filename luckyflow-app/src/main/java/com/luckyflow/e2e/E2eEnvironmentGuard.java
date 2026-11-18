package com.luckyflow.e2e;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 测试入口启用前核对真实连接目标，避免误把 E2E 认证能力带入常规环境。 */
@Component
@Profile("e2e")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class E2eEnvironmentGuard implements ApplicationRunner {
    private final Environment environment;
    private final JdbcTemplate jdbc;

    public E2eEnvironmentGuard(Environment environment, JdbcTemplate jdbc) {
        this.environment = environment;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        if (profiles.size() != 1 || !profiles.contains("e2e")) {
            throw new IllegalStateException("E2E cannot be combined with another Spring profile");
        }
        if (!"true".equals(environment.getProperty("luckyflow.e2e.enabled"))) {
            throw new IllegalStateException("LUCKYFLOW_E2E_ENABLED=true is required");
        }
        if (!"127.0.0.1".equals(environment.getProperty("server.address"))
                || !"never".equals(environment.getProperty("spring.sql.init.mode"))) {
            throw new IllegalStateException("E2E requires loopback HTTP and disabled SQL initialization");
        }
        if (!"15".equals(environment.getProperty("spring.data.redis.database"))) {
            throw new IllegalStateException("E2E requires isolated Redis DB 15");
        }
        if (!"luckyflow".equals(environment.getProperty("spring.rabbitmq.virtual-host"))) {
            throw new IllegalStateException("E2E requires the dedicated RabbitMQ vhost");
        }
        // 以实际 JDBC 会话为准；URL 文本正确但连接到错误库时也必须停止管理员初始化。
        if (!"lottery_e2e_20261015".equals(jdbc.queryForObject("SELECT DATABASE()", String.class))) {
            throw new IllegalStateException("E2E requires the isolated MySQL database");
        }
    }
}
