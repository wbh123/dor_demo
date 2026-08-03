# Navicat 数据库脚本

本目录中的全部脚本统一使用数据库：

```text
wust_dormitory
```

## 目录结构

```text
navicat/
├── 01_数据库架构/
│   ├── 00_删除并创建数据库.sql
│   ├── 01_V1...sql ～ 16_V16...sql
│   └── 99_写入Flyway基线.sql
├── 02_1000人干净测试数据/
│   ├── 00_使用统一数据库.sql
│   └── 01_清空并导入1000人干净数据.sql
├── 03_1000人真实业务数据/
│   ├── 00_使用统一数据库.sql
│   └── 01_清空并导入1000人真实业务数据.sql
└── generated/
    ├── 01_数据库架构.sql
    ├── 02_1000人干净测试数据.sql
    └── 03_1000人真实业务数据.sql
```

## Navicat 直接执行

### 初始化数据库架构

在同一个 Navicat 连接中，按文件名顺序执行 `01_数据库架构` 目录内的全部 SQL。

第一份脚本会执行：

```sql
DROP DATABASE IF EXISTS `wust_dormitory`;
CREATE DATABASE `wust_dormitory` ...;
USE `wust_dormitory`;
```

最后一份脚本会写入 Flyway V16 基线，使应用后续可以从 V17 继续迁移。

### 导入1000人干净数据

先完成数据库架构，再按顺序执行 `02_1000人干净测试数据` 中的两份 SQL。

主数据脚本内部会调用 `clear_1000_test_data()`，清空当前学校业务数据后重新生成：

- 1000名学生；
- 260间五人寝；
- 1300张床；
- 无批次、无在住、无床位分配。

### 导入1000人真实业务数据

先完成数据库架构，再按顺序执行 `03_1000人真实业务数据` 中的两份 SQL。

主数据脚本同样会先清空学校业务数据，再生成正常业务状态下的乱序数据。

## 生成三个完全独立单文件

在仓库根目录运行：

```bash
python scripts/db/generate_navicat_sql.py
```

生成位置：

```text
backend-java/docs/sql/navicat/generated/
```

校验已生成文件是否最新：

```bash
python scripts/db/generate_navicat_sql.py --check
```

三个生成文件均不包含 MySQL 客户端 `SOURCE` 命令，可直接通过 Navicat 的“运行 SQL 文件”功能导入。

## 安全警告

- 数据库架构脚本会删除整个 `wust_dormitory` 数据库；
- 两套测试数据会清空该数据库中的学校业务数据；
- 只能在开发或测试环境使用；
- 不要对生产数据库执行。
