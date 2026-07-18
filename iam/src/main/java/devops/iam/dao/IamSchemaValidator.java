package devops.iam.dao;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class IamSchemaValidator {
    @Bean
    @ConditionalOnProperty(name = "app.iam.schema-validation", havingValue = "true")
    ApplicationRunner validateIamSchema(DataSource dataSource) {
        return arguments -> {
            Set<String> existing = new HashSet<>();
            try (var connection = dataSource.getConnection()) {
                DatabaseMetaData metadata = connection.getMetaData();
                try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                    while (tables.next()) {
                        existing.add(tables.getString("TABLE_NAME").toLowerCase());
                    }
                }
            }
            for (IamTable table : IamTable.values()) {
                if (!existing.contains(table.tableName())) {
                    throw new IllegalStateException("缺少 IAM 数据表：" + table.tableName());
                }
            }
        };
    }
}
