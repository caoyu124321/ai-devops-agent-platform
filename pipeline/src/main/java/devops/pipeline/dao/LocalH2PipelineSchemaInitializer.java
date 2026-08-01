package devops.pipeline.dao;

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

/** 本地 H2 仅用于自动化测试；部署 MySQL 时必须显式执行 V008 迁移。 */
@Configuration
class LocalH2PipelineSchemaInitializer {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    @ConditionalOnProperty(name = "app.pipeline.local-h2-schema-init", havingValue = "true", matchIfMissing = true)
    ApplicationRunner initializeLocalH2PipelineSchema(DataSource dataSource) {
        return arguments -> {
            if (!isH2(dataSource)) {
                return;
            }
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("db/h2-pipeline-schema.sql"));
            populator.setContinueOnError(false);
            DatabasePopulatorUtils.execute(populator, dataSource);
        };
    }

    private boolean isH2(DataSource dataSource) throws Exception {
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            return "H2".equalsIgnoreCase(metadata.getDatabaseProductName());
        }
    }
}
