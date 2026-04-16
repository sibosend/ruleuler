package com.caritasem.ruleuler.grayscale;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 灰度指标异步上报器。Client 端投递到队列，后台线程批量 POST 到 server。
 */
public class GrayscaleMetricsReporter {

    private static final Logger log = LoggerFactory.getLogger(GrayscaleMetricsReporter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record MetricEvent(String packageId, String version, boolean success, long execMs) {}

    private final BlockingQueue<MetricEvent> queue = new LinkedBlockingQueue<>(5000);
    private final String serverBaseUrl;
    private volatile boolean running = true;

    public GrayscaleMetricsReporter(String serverBaseUrl) {
        this.serverBaseUrl = serverBaseUrl;
        Thread consumer = new Thread(this::consume, "grayscale-metrics-reporter");
        consumer.setDaemon(true);
        consumer.start();
    }

    public void report(String packageId, String version, boolean success, long execMs) {
        queue.offer(new MetricEvent(packageId, version, success, execMs));
    }

    public void stop() { running = false; }

    private void consume() {
        while (running) {
            try {
                List<MetricEvent> batch = new ArrayList<>();
                MetricEvent first = queue.take(); // 阻塞等待
                batch.add(first);
                queue.drainTo(batch, 100);
                sendBatch(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void sendBatch(List<MetricEvent> batch) {
        for (MetricEvent e : batch) {
            try {
                String json = objectMapper.writeValueAsString(Map.of(
                        "packageId", e.packageId(),
                        "version", e.version(),
                        "success", e.success(),
                        "execMs", e.execMs()));
                post(serverBaseUrl + "/api/grayscale/metrics/report", json);
            } catch (Exception ex) {
                log.warn("上报灰度指标失败: {}", ex.getMessage());
            }
        }
    }

    private boolean post(String urlStr, String jsonBody) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            try (var os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
            return conn.getResponseCode() < 300;
        } catch (Exception e) {
            log.debug("POST 失败: {}", e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
