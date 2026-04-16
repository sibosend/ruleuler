package com.caritasem.ruleuler.server.grayscale;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 灰度指标异步采集器。接收 client 上报，批量写入 DB。
 */
@Service
@RequiredArgsConstructor
public class GrayscaleMetricsRecorder {

    private static final Logger log = LoggerFactory.getLogger(GrayscaleMetricsRecorder.class);

    private final GrayscaleRuleDao grayscaleRuleDao;

    public record MetricEvent(Long ruleId, String version, boolean success, long execMs) {}

    private final BlockingQueue<MetricEvent> queue = new LinkedBlockingQueue<>(10000);

    public void record(Long ruleId, String version, boolean success, long execMs) {
        queue.offer(new MetricEvent(ruleId, version, success, execMs));
    }

    /** 每 5s 消费队列 */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void flush() {
        if (queue.isEmpty()) return;
        java.util.List<MetricEvent> batch = new java.util.ArrayList<>();
        queue.drainTo(batch, 500);
        for (MetricEvent e : batch) {
            try {
                grayscaleRuleDao.upsertMetrics(e.ruleId(), e.version(), e.success(), e.execMs());
            } catch (Exception ex) {
                log.warn("写入灰度指标失败: ruleId={}, {}", e.ruleId(), ex.getMessage());
            }
        }
    }
}
