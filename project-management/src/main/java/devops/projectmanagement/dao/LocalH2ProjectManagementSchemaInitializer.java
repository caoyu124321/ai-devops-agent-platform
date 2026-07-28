package devops.projectmanagement.dao;

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

/** 本地 H2 开发库只初始化项目管理表，外部 MySQL 必须执行正式迁移。 */
@Configuration
class LocalH2ProjectManagementSchemaInitializer {
    private static final String H2_PRODUCT_NAME = "H2";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    @ConditionalOnProperty(name = "app.project-management.local-h2-schema-init", havingValue = "true", matchIfMissing = true)
    ApplicationRunner initializeLocalH2ProjectManagementSchema(DataSource dataSource) {
        return arguments -> {
            if (!isH2(dataSource)) {
                return;
            }
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                    new ClassPathResource("db/h2-project-management-schema.sql"));
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
