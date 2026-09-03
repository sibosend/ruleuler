CREATE TABLE IF NOT EXISTS `ruleuler_migrator_log` (
  `name` varchar(100) NOT NULL,
  `ran_at` bigint NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
