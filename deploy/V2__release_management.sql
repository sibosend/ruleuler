-- V2: 上线管理 - 新增 publisher/published_at 字段
ALTER TABLE ruleuler_publish_approval ADD COLUMN publisher VARCHAR(100);
ALTER TABLE ruleuler_publish_approval ADD COLUMN published_at BIGINT;
