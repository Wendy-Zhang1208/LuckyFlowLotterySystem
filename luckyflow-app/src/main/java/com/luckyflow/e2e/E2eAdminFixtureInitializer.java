package com.luckyflow.e2e;

import com.luckyflow.user.entity.User;
import com.luckyflow.user.model.RegisterUserCommand;
import com.luckyflow.user.service.UserAccountService;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 仅在空白隔离库内由服务端创建管理员；公开注册仍只能获得 USER。 */
@Component
@Profile("e2e")
@ConditionalOnProperty(prefix = "luckyflow.e2e", name = "enabled", havingValue = "true")
// 人工验收复用已有管理员，关闭夹具初始化可避免启动时覆盖其密码。
@ConditionalOnProperty(prefix = "luckyflow.e2e", name = "admin-fixture-enabled",
        havingValue = "true", matchIfMissing = true)
public class E2eAdminFixtureInitializer implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(E2eAdminFixtureInitializer.class);
    public static final String ADMIN_ACCOUNT = "lf_e2adm";
    private final JdbcTemplate jdbc;
    private final UserAccountService users;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transaction;
    private final String password;

    public E2eAdminFixtureInitializer(JdbcTemplate jdbc, UserAccountService users,
            PasswordEncoder encoder, PlatformTransactionManager manager,
            @Value("${LUCKYFLOW_E2E_ADMIN_PASSWORD:}") String password) {
        this.jdbc = jdbc;
        this.users = users;
        this.encoder = encoder;
        this.transaction = new TransactionTemplate(manager);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        if (!"lottery_e2e_20261015".equals(database)) {
            throw new IllegalStateException("E2E profile requires lottery_e2e_20261015");
        }
        if (password == null || password.length() < 16
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("LUCKYFLOW_E2E_ADMIN_PASSWORD must be 16-72 bytes");
        }
        Integer intentTable = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'activity_publication_intent'",
                Integer.class);
        if (intentTable == null || intentTable != 1) {
            throw new IllegalStateException("E2E publication-intent schema is missing");
        }
        transaction.executeWithoutResult(ignored -> {
            Integer existing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM `user` WHERE account = ?", Integer.class, ADMIN_ACCOUNT);
            if (existing != null && existing == 1) {
                Integer updated = jdbc.update("UPDATE `user` SET password_hash = ?, update_time = NOW(3) "
                                + "WHERE account = ? AND role = 'ADMIN'",
                        encoder.encode(password), ADMIN_ACCOUNT);
                if (updated != 1) { throw new IllegalStateException("E2E admin role is invalid"); }
                return;
            }
            Integer usersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class);
            if (usersBefore == null || usersBefore != 0) {
                throw new IllegalStateException("Refuse to initialize E2E admin in a nonempty user table");
            }
            User admin = users.registerVerifiedUser(new RegisterUserCommand(
                    "e2e-admin@luckyflow.local", null, encoder.encode(password),
                    "E2E 运营管理员", null, "CLOUD_STAR"));
            int promoted = jdbc.update("UPDATE `user` SET account = ?, role = 'ADMIN', "
                    + "update_time = NOW(3) WHERE id = ? AND role = 'USER'",
                    ADMIN_ACCOUNT, admin.getId());
            if (promoted != 1) { throw new IllegalStateException("Failed to initialize E2E admin"); }
        });
        LOG.info("E2E admin fixture ready in isolated database");
    }
}
