package com.caritasem.ruleuler.monitoring;

import com.alibaba.fastjson2.JSON;
import com.bstek.urule.runtime.shadow.ShadowHitInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * 影子命中日志生产者：将 ShadowHitInfo 转为 ShadowHitLogRow 并投递到队列。
 */
public class ShadowHitLogProducer {

    private static final Logger log = LoggerFactory.getLogger(ShadowHitLogProducer.class);

    private final BlockingQueue<ShadowHitLogRow> queue;

    public ShadowHitLogProducer(BlockingQueue<ShadowHitLogRow> queue) {
        this.queue = queue;
    }

    public void produce(String executionId, String project, String packageId,
                        String flowId, List<ShadowHitInfo> hits) {
        long now = System.currentTimeMillis();
        for (ShadowHitInfo hit : hits) {
            ShadowHitLogRow row = new ShadowHitLogRow(
                    executionId,
                    project,
                    packageId,
                    flowId,
                    hit.getRuleName(),
                    JSON.toJSONString(hit.getInputSnapshot()),
                    JSON.toJSONString(hit.getOutputSnapshot()),
                    hit.getExecMs(),
                    hit.getErrorMsg(),
                    now
            );
            if (!queue.offer(row)) {
                log.warn("影子日志队列已满，丢弃: executionId={}, ruleName={}", executionId, hit.getRuleName());
            }
        }
    }
}
