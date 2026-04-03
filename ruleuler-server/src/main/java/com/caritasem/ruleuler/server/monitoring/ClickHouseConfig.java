package com.caritasem.ruleuler.server.monitoring;

import com.clickhouse.jdbc.ClickHouseDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

/**
 * ClickHouse 数据源配置，独立于 MySQL DataSource。
 * 仅在 monitoring.enabled=true 时激活。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "monitoring.enabled", havingValue = "true")
public class ClickHouseConfig {

    @Value("${monitoring.clickhouse.jdbc-url}")
    private String jdbcUrl;

    @Bean("clickHouseDataSource")
    public DataSource clickHouseDataSource() throws SQLException {
        return new ClickHouseDataSource(jdbcUrl, new Properties());
    }
}
