package com.caritasem.ruleuler.monitoring;

import com.clickhouse.jdbc.ClickHouseDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 监控采集配置。monitoring.enabled=true 时激活，注册队列、生产者、刷写线程。
 */
@Configuration
@ConditionalOnProperty(name = "monitoring.enabled", havingValue = "true")
public class MonitoringConfig {

    @Value("${monitoring.clickhouse.jdbc-url}")
    private String jdbcUrl;

    @Value("${monitoring.clickhouse.batch-size:500}")
    private int batchSize;

    @Value("${monitoring.clickhouse.flush-interval-ms:1000}")
    private long flushIntervalMs;

    @Value("${monitoring.clickhouse.queue-capacity:50000}")
    private int queueCapacity;

    @Bean
    public BlockingQueue<VarLogRow> varLogQueue() {
        return new LinkedBlockingQueue<>(queueCapacity);
    }

    @Bean
    public DataSource clickHouseDataSource() throws SQLException {
        return new ClickHouseDataSource(jdbcUrl, new Properties());
    }

    @Bean
    public VarEventProducer varEventProducer() {
        return new VarEventProducer(varLogQueue());
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public VarLogFlusher varLogFlusher() throws SQLException {
        return new VarLogFlusher(varLogQueue(), clickHouseDataSource(), batchSize, flushIntervalMs);
    }

    // ---- 执行追踪 ----

    @Bean
    public BlockingQueue<TraceLogRow> traceLogQueue() {
        return new LinkedBlockingQueue<>(queueCapacity);
    }

    @Bean
    public TraceDebugWriter traceDebugWriter() {
        return new TraceDebugWriter(traceLogQueue());
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public TraceLogFlusher traceLogFlusher() throws SQLException {
        return new TraceLogFlusher(traceLogQueue(), clickHouseDataSource(), batchSize, flushIntervalMs);
    }
}
