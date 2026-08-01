# 数据库字典模板

当前框架只保留最小 MySQL/MyBatis 示例表。迁移到目标系统时，请替换为目标系统自己的数据库结构。

## example_table

该示例表用于 MyBatis Generator 模板验证。

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| id | BIGINT | 是 | AUTO_INCREMENT | 主键 |
| example_code | VARCHAR(64) | 是 | 无 | 示例编码 |
| example_name | VARCHAR(128) | 是 | 无 | 显示名称 |
| enabled | TINYINT | 是 | 1 | 是否启用：1 启用，0 禁用 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

## 索引

| 索引 | 类型 | 字段 | 说明 |
|---|---|---|---|
| PRIMARY | 主键 | id | 主键索引 |
| uk_example_code | 唯一索引 | example_code | 保证示例编码唯一 |
| idx_enabled | 普通索引 | enabled | 按启用状态查询 |

## 适配说明

复用该框架时：

1. 将 `docs/sql/schema.sql` 替换为目标系统的 DDL。
2. 将本文档替换为目标系统的数据表说明。
3. 更新 `server/src/main/resources/mybatis-generator/generatorConfig.xml` 中的表映射。
4. 更新 `server/src/main/resources/mybatis-generator/generator.properties` 中的数据库连接配置。
