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
 * 后台刷写线程：从队列批量消费 TraceLogRow，通过 JDBC batch insert 写入 ClickHouse。
 */
public class TraceLogFlusher {

    private static final Logger log = LoggerFactory.getLogger(TraceLogFlusher.class);

    private static final String INSERT_SQL =
            "INSERT INTO execution_trace_log " +
            "(execution_id, seq, msg_type, msg_text, parsed_name, pass_fail, " +
            "project, package_id, flow_id, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final BlockingQueue<TraceLogRow> queue;
    private final DataSource dataSource;
    private final int batchSize;
    private final long flushIntervalMs;

    private volatile boolean running = true;
    private Thread flushThread;

    public TraceLogFlusher(BlockingQueue<TraceLogRow> queue, DataSource dataSource,
                           int batchSize, long flushIntervalMs) {
        this.queue = queue;
        this.dataSource = dataSource;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
    }

    public void start() {
        flushThread = new Thread(this::flushLoop, "trace-log-flusher");
        flushThread.setDaemon(true);
        flushThread.start();
        log.info("TraceLogFlusher 已启动, batchSize={}, flushIntervalMs={}", batchSize, flushIntervalMs);
    }

    public void stop() {
        running = false;
        if (flushThread != null) {
            flushThread.interrupt();
        }
        List<TraceLogRow> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            flush(remaining);
            log.info("TraceLogFlusher 关闭前最终 flush {} 条", remaining.size());
        }
    }

    private void flushLoop() {
        List<TraceLogRow> batch = new ArrayList<>();
        long lastFlushTime = System.currentTimeMillis();

        while (running) {
            try {
                TraceLogRow row = queue.poll(100, TimeUnit.MILLISECONDS);
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

    void flush(List<TraceLogRow> batch) {
        if (batch.isEmpty()) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            for (TraceLogRow r : batch) {
                ps.setString(1, r.executionId());
                ps.setInt(2, r.seq());
                ps.setString(3, r.msgType());
                ps.setString(4, r.msgText());
                ps.setString(5, r.parsedName());
                ps.setString(6, r.passFail());
                ps.setString(7, r.project());
                ps.setString(8, r.packageId());
                ps.setString(9, r.flowId());
                ps.setTimestamp(10, new Timestamp(r.createdAt()));
                ps.addBatch();
            }
            ps.executeBatch();
            log.debug("TraceLogFlusher 写入 {} 条", batch.size());
        } catch (Exception e) {
            log.error("TraceLogFlusher 写入 ClickHouse 失败，丢弃 {} 条数据", batch.size(), e);
        }
    }
}
