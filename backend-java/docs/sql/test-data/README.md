# V16 数据库结构与 1000 人测试数据

## 1. 文件说明

| 文件 | 用途 |
|---|---|
| `../schema.sql` | 最新 V1—V16 数据库结构导入入口，按顺序执行正式 Flyway 迁移 |
| `1000_students_base.sql` | 两套千人数据共用基础生成脚本 |
| `1000_students_clean.sql` | 1000 人干净基础数据，无批次、无在住和无分配结果 |
| `1000_students_realistic_mixed_state.sql` | 1000 人乱序真实业务状态，包含双模式批次、在住、待确认床位、通知和偏好特征 |

脚本仅用于开发和测试数据库，会清理当前数据库中的学校业务数据。平台套餐、订阅、功能与配额目录会保留。

## 2. 导入最新结构

从项目根目录执行：

```bash
mysql -h127.0.0.1 -P3306 -uroot -p \
  --default-character-set=utf8mb4 \
  wust_dormitory < backend-java/docs/sql/schema.sql
```

`schema.sql` 使用 MySQL `SOURCE` 指令加载：

```text
V1 → V2 → ... → V15 → V16
```

因此应使用 MySQL 命令行客户端执行，不要使用不支持 `SOURCE` 的简单 SQL 执行器。

需要生成完全内联、无 `SOURCE` 指令的独立结构文件时执行：

```bash
python scripts/db/build_frozen_baseline.py \
  --mode inline \
  --output backend-java/docs/sql/schema_v16_inline.sql
```

## 3. 导入 1000 人干净数据

必须先切换到数据脚本目录，使公共 `SOURCE` 路径可解析：

```bash
cd backend-java/docs/sql/test-data
mysql -h127.0.0.1 -P3306 -uroot -p \
  --default-character-set=utf8mb4 \
  wust_dormitory < 1000_students_clean.sql
```

数据规模：

```text
学生：1000
国内生：850
国际生：150
男生：500
女生：500
转学生来源档案：50
房间：260 间五人寝
床位：1300
总余量：300 个床位
批次：0
有效在住：0
```

适合：

- 批次创建和发布；
- 转学生仅建档、直接入住和加入批次；
- 国内生与国际生隔离；
- 性别和宿舍属性筛选；
- 1000 人导入和列表性能测试。

学生账号均为 `PENDING`，需要走学生激活流程。

管理员测试账号：

```text
用户名：admin
密码：Dormitory@2026
```

## 4. 导入 1000 人真实乱序状态

```bash
cd backend-java/docs/sql/test-data
mysql -h127.0.0.1 -P3306 -uroot -p \
  --default-character-set=utf8mb4 \
  wust_dormitory < 1000_students_realistic_mixed_state.sql
```

数据状态：

- 学生和账号状态按互质步长打乱，不按学号连续分组；
- 840 名有效在住学生；
- 160 名 `ROOM` 模式学生只有寝室归属，实际床位待确认；
- 680 名学生已经确认实际床位；
- 一个 `ROOM` 活动批次，占用 40 间混住宿舍；
- 一个 `BED` 活动批次，占用 72 间专用宿舍；
- 两个活动批次的房间和学生范围完全互斥；
- `BED` 批次不包含任何实际床位未确认的寝室；
- 440 份学生匹配特征；
- 300 条已读和未读通知；
- 仍保留 160 名未入住学生和 460 张物理空床，用于继续测试选择、转学生和容量不足场景。

已形成在住记录的学生测试密码：

```text
Student@2026
```

学生用户名为 12 位学号，例如：

```text
202600000001
```

## 5. 脚本内置校验

两个入口脚本都会主动检查：

- 数据库是否已经包含 V16 字段；
- 唯一系统管理员是否存在；
- 学生、房间和床位数量；
- 寝室是否超容量；
- 实际床位是否重复占用；
- 国内生/国际生与宿舍属性是否冲突；
- 活动批次房间是否重复锁定；
- `BED` 批次是否错误包含待确认床位的在住学生。

任何不变量失败时，脚本使用 `SIGNAL SQLSTATE '45000'` 立即终止。

## 6. 静态检查

```bash
python -m unittest scripts.db.test_1000_student_sql -v
```

重新检查结构导入文件：

```bash
python scripts/db/build_frozen_baseline.py --check
```
