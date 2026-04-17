package com.caritasem.ruleuler.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 后台刷写线程：从队列批量消费 VarLogRow，通过 JDBC batch insert 写入 ClickHouse。
 * <p>
 * 非 Spring Bean，由 MonitoringConfig 创建并管理生命周期。
 * 调用方需在 @PostConstruct 调用 {@link #start()}，@PreDestroy 调用 {@link #stop()}。
 */
public class VarLogFlusher {

    private static final Logger log = LoggerFactory.getLogger(VarLogFlusher.class);

    private static final String INSERT_SQL =
            "INSERT INTO execution_var_log " +
            "(execution_id, project, package_id, flow_id, var_category, var_name, " +
            "var_type, val_num, val_str, io_type, exec_ms, created_at, grayscale_bucket) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final BlockingQueue<VarLogRow> queue;
    private final DataSource dataSource;
    private final int batchSize;
    private final long flushIntervalMs;

    private volatile boolean running = true;
    private Thread flushThread;

    public VarLogFlusher(BlockingQueue<VarLogRow> queue, DataSource dataSource,
                         int batchSize, long flushIntervalMs) {
        this.queue = queue;
        this.dataSource = dataSource;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
    }

    /** 启动后台刷写线程 */
    public void start() {
        flushThread = new Thread(this::flushLoop, "var-log-flusher");
        flushThread.setDaemon(true);
        flushThread.start();
        log.info("VarLogFlusher 已启动, batchSize={}, flushIntervalMs={}", batchSize, flushIntervalMs);
    }

    /** 停止线程并做最后一次 flush */
    public void stop() {
        running = false;
        if (flushThread != null) {
            flushThread.interrupt();
        }
        // 排空队列，最后一次 flush
        List<VarLogRow> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            flush(remaining);
            log.info("VarLogFlusher 关闭前最终 flush {} 条", remaining.size());
        }
    }

    /** 主循环：poll + 攒批 + 定时/定量 flush */
    private void flushLoop() {
        List<VarLogRow> batch = new ArrayList<>();
        long lastFlushTime = System.currentTimeMillis();

        while (running) {
            try {
                VarLogRow row = queue.poll(100, TimeUnit.MILLISECONDS);
                if (row != null) {
                    batch.add(row);
                }
                long now = System.currentTimeMillis();
                boolean sizeTriggered = batch.size() >= batchSize;
                boolean timeTriggered = (now - lastFlushTime) >= flushIntervalMs;

                if (!batch.isEmpty() && (sizeTriggered || timeTriggered)) {
                    flush(batch);
                    batch = new ArrayList<>();
                    lastFlushTime = System.currentTimeMillis();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // 线程退出前 flush 残留
        if (!batch.isEmpty()) {
            flush(batch);
        }
    }

    /** 批量写入 ClickHouse，失败则记日志并丢弃 */
    void flush(List<VarLogRow> batch) {
        if (batch.isEmpty()) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            for (VarLogRow r : batch) {
                ps.setString(1, r.executionId());
                ps.setString(2, r.project());
                ps.setString(3, r.packageId());
                ps.setString(4, r.flowId());
                ps.setString(5, r.varCategory());
                ps.setString(6, r.varName());
                ps.setString(7, r.varType());
                if (r.valNum() != null) {
                    ps.setDouble(8, r.valNum());
                } else {
                    ps.setNull(8, java.sql.Types.DOUBLE);
                }
                ps.setString(9, r.valStr());
                ps.setString(10, r.ioType());
                ps.setLong(11, r.execMs());
                ps.setTimestamp(12, new Timestamp(r.createdAt()));
                ps.setString(13, r.grayscaleBucket() != null ? r.grayscaleBucket() : "BASE");
                ps.addBatch();
            }
            ps.executeBatch();
            log.debug("VarLogFlusher 写入 {} 条", batch.size());
        } catch (Exception e) {
            log.error("VarLogFlusher 写入 ClickHouse 失败，丢弃 {} 条数据", batch.size(), e);
        }
    }
}
