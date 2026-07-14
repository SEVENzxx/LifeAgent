-- V1 已在验证环境执行，使用新迁移删除脚手架探针表，避免修改历史迁移的校验和。
DROP TABLE IF EXISTS system_settings;
