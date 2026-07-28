package devops.iam.dao;

import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * 默认 H2 仅用于本机开发，启动时创建与 IAM 持久化映射一致的完整表结构，避免 OAuth 首次注册因空库失败。
 * 外部数据库必须继续执行版本化迁移，不能由应用在运行时改写其结构。
 */
@Configuration
class LocalH2SchemaInitializer {
    private static final String H2_PRODUCT_NAME = "H2";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnProperty(name = "app.iam.local-h2-schema-init", havingValue = "true", matchIfMissing = true)
    ApplicationRunner initializeLocalH2Schema(DataSource dataSource) {
        return arguments -> {
            if (!isH2(dataSource)) {
                return;
            }
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("db/h2-iam-schema.sql"));
            populator.setContinueOnError(false);
            DatabasePopulatorUtils.execute(populator, dataSource);
        };
    }

    private boolean isH2(DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            return H2_PRODUCT_NAME.equalsIgnoreCase(metadata.getDatabaseProductName());
        }
    }
}
