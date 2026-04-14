package com.caritasem.ruleuler.monitoring;

import com.bstek.urule.debug.DebugWriter;
import com.bstek.urule.debug.MessageItem;
import com.bstek.urule.debug.MsgType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * DebugWriter 实现：解析规则引擎 debug 消息，投递结构化追踪行到队列。
 * 通过 Spring 自动注册到 Utils.getDebugWriters()。
 */
public class TraceDebugWriter implements DebugWriter {

    private static final Logger log = LoggerFactory.getLogger(TraceDebugWriter.class);

    private final BlockingQueue<TraceLogRow> queue;

    public TraceDebugWriter(BlockingQueue<TraceLogRow> queue) {
        this.queue = queue;
    }

    @Override
    public void write(List<MessageItem> items) throws IOException {
        TraceContext.TraceInfo info = TraceContext.get();
        if (info == null) return;

        int seq = 0;
        for (MessageItem item : items) {
            seq++;
            MsgType type = item.getType();
            String msg = item.getMsg();

            Parsed parsed = parseMessage(msg, type);
            TraceLogRow row = new TraceLogRow(
                    info.executionId(), seq, type.name(), msg,
                    parsed.name, parsed.passFail,
                    info.project(), info.packageId(), info.flowId(),
                    System.currentTimeMillis()
            );
            if (!queue.offer(row)) {
                log.warn("追踪队列已满，丢弃: executionId={}, seq={}", info.executionId(), seq);
            }
        }
    }

    /**
     * 解析 debug 消息文本，提取名称和通过/未通过状态。
     * <pre>
     * 条件:      "^^^条件：<id> =>满足, 左值：<v>, 右值：<v>"
     * 命名条件:  "^^^命名条件：<id> =>满足"
     * 规则匹配:  "√√√规则【<name>】成功匹配"
     * 变量赋值:  "###变量赋值：<label>=<value>"
     * </pre>
     */
    static Parsed parseMessage(String msg, MsgType type) {
        if (msg == null) return new Parsed(null, null);

        return switch (type) {
            case Condition -> parseCondition(msg);
            case RuleMatch -> parseRuleMatch(msg);
            case VarAssign -> parseVarAssign(msg);
            default -> new Parsed(null, null);
        };
    }

    private static Parsed parseCondition(String msg) {
        // "^^^条件：<id> =>满足/不满足, ..."
        // "^^^命名条件：<id> =>满足/不满足"
        String name = null;
        String passFail = null;

        if (msg.contains("=>满足")) {
            passFail = "PASS";
        } else if (msg.contains("=>不满足")) {
            passFail = "FAIL";
        }

        // 提取条件名：在 "^^^条件：" / "^^^命名条件：" 之后，" =>" 之前
        int start = msg.indexOf("：");
        if (start >= 0) {
            int end = msg.indexOf(" =>");
            if (end > start) {
                name = msg.substring(start + 1, end);
            }
        }

        return new Parsed(name, passFail);
    }

    private static Parsed parseRuleMatch(String msg) {
        // "√√√规则【<name>】成功匹配"
        int start = msg.indexOf("【");
        int end = msg.indexOf("】");
        if (start >= 0 && end > start) {
            return new Parsed(msg.substring(start + 1, end), "PASS");
        }
        return new Parsed(null, "PASS");
    }

    private static Parsed parseVarAssign(String msg) {
        // "###变量赋值：<label>=<value>"
        int start = msg.indexOf("：");
        int end = msg.indexOf("=");
        if (start >= 0) {
            String label = (end > start) ? msg.substring(start + 1, end) : msg.substring(start + 1);
            return new Parsed(label, null);
        }
        return new Parsed(null, null);
    }

    record Parsed(String name, String passFail) {}
}
