-- 去掉 uk_active 唯一索引，该索引包含 status 列导致同一 project+package 无法有两条相同终态记录
-- 唯一性由代码 findActive 查询保证（创建时校验无 ACTIVE 记录）
ALTER TABLE `ruleuler_grayscale_rule` DROP INDEX `uk_active`;
