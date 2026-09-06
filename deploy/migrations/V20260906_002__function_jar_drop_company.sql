-- company_id 从未写入真实值，从唯一键和表中去掉

ALTER TABLE `ruleuler_function_jar` DROP INDEX `uk_func_jar`;
ALTER TABLE `ruleuler_function_jar` DROP COLUMN `company_id`;
ALTER TABLE `ruleuler_function_jar` ADD UNIQUE KEY `uk_func_jar` (`project`, `function_package`, `version`);
