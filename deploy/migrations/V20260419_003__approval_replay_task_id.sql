-- 审批表新增 replay_task_id 字段，关联审批时自动触发的流量回放任务
ALTER TABLE ruleuler_publish_approval
    ADD COLUMN replay_task_id BIGINT DEFAULT NULL COMMENT '关联流量回放任务 ID';
