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
 * 后台刷写线程：从队列批量消费 ShadowHitLogRow，通过 JDBC batch insert 写入 ClickHouse。
 */
public class ShadowHitLogFlusher {

    private static final Logger log = LoggerFactory.getLogger(ShadowHitLogFlusher.class);

    private static final String INSERT_SQL =
            "INSERT INTO shadow_hit_log " +
            "(execution_id, project, package_id, flow_id, rule_name, " +
            "input_snapshot, output_snapshot, exec_ms, error_msg, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final BlockingQueue<ShadowHitLogRow> queue;
    private final DataSource dataSource;
    private final int batchSize;
    private final long flushIntervalMs;

    private volatile boolean running = true;
    private Thread flushThread;

    public ShadowHitLogFlusher(BlockingQueue<ShadowHitLogRow> queue, DataSource dataSource,
                               int batchSize, long flushIntervalMs) {
        this.queue = queue;
        this.dataSource = dataSource;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
    }

    public void start() {
        flushThread = new Thread(this::flushLoop, "shadow-log-flusher");
        flushThread.setDaemon(true);
        flushThread.start();
        log.info("ShadowHitLogFlusher 已启动, batchSize={}, flushIntervalMs={}", batchSize, flushIntervalMs);
    }

    public void stop() {
        running = false;
        if (flushThread != null) {
            flushThread.interrupt();
        }
        List<ShadowHitLogRow> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            flush(remaining);
            log.info("ShadowHitLogFlusher 关闭前最终 flush {} 条", remaining.size());
        }
    }

    private void flushLoop() {
        List<ShadowHitLogRow> batch = new ArrayList<>();
        long lastFlushTime = System.currentTimeMillis();

        while (running) {
            try {
                ShadowHitLogRow row = queue.poll(100, TimeUnit.MILLISECONDS);
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
        if (!batch.isEmpty()) {
            flush(batch);
        }
    }

    void flush(List<ShadowHitLogRow> batch) {
        if (batch.isEmpty()) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            for (ShadowHitLogRow r : batch) {
                ps.setString(1, r.executionId());
                ps.setString(2, r.project());
                ps.setString(3, r.packageId());
                ps.setString(4, r.flowId());
                ps.setString(5, r.ruleName());
                ps.setString(6, r.inputSnapshot());
                ps.setString(7, r.outputSnapshot());
                ps.setLong(8, r.execMs());
                if (r.errorMsg() != null) {
                    ps.setString(9, r.errorMsg());
                } else {
                    ps.setNull(9, java.sql.Types.VARCHAR);
                }
                ps.setTimestamp(10, new Timestamp(r.createdAt()));
                ps.addBatch();
            }
            ps.executeBatch();
            log.debug("ShadowHitLogFlusher 写入 {} 条", batch.size());
        } catch (Exception e) {
            log.error("ShadowHitLogFlusher 写入 ClickHouse 失败，丢弃 {} 条数据", batch.size(), e);
        }
    }
}
