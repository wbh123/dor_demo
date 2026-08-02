# 数据库脚本使用说明

当前正式数据库版本为 **Flyway V16**。

三个入口职责相互独立：

| 文件 | 职责 |
|---|---|
| `scripts/dev/reset-local-environment.sh` | 仅删除本地MySQL、Redis持久化数据并重新启动空容器 |
| `backend-java/docs/sql/schema.sql` | 从仓库根目录按V1至V16顺序执行正式迁移，建立数据库架构 |
| `backend-java/docs/sql/reset_and_seed_test_data.sql` | 在V16架构上导入500人测试数据，并补充选寝模式、分类隔离与在住数据 |

`reset_and_seed_test_data_core.sql`是500人核心数据生成脚本，由测试数据主入口自动调用，不建议单独执行。

## 一、只清空MySQL和Redis

```bash
bash scripts/dev/reset-local-environment.sh --yes
```

该命令不会执行迁移，也不会导入任何账号或测试数据。

## 二、建立V16数据库架构

推荐开发和生产升级使用Flyway。需要从空数据库手动建立架构时，在仓库根目录执行：

```bash
mysql --binary-mode=1 -u<user> -p <database> \
  < backend-java/docs/sql/schema.sql
```

`schema.sql`使用MySQL客户端的`SOURCE`命令，因此必须从仓库根目录运行，不能作为JDBC单条SQL提交。

需要生成完整、无`SOURCE`依赖的独立快照时执行：

```bash
python scripts/db/build_frozen_baseline.py \
  --mode inline \
  --output backend-java/docs/sql/schema-inline-v16.sql
```

## 三、导入500人测试数据

```bash
mysql --binary-mode=1 -u<user> -p <database> \
  < backend-java/docs/sql/reset_and_seed_test_data.sql
```

测试数据规模：

- 学生500人；
- 男生250人、女生250人；
- 国内生400人、国际生100人；
- 男、女生各包含50名国际学生；
- 已激活账号150个、待激活账号350个；
- 五人间100间；
- 国内生专用寝室80间、国际生专用寝室20间；
- 床位500个；
- 450名学生具有完整个人偏好，50名学生保留未填写状态；
- 20个组队样例；
- 50条已有具体床位分配和对应跨批次在住记录；
- 一个开启国内生/国际生分类隔离的`BED`模式测试批次；
- 100条活动批次房间锁；
- 批次授权快照包含`P2_BED_SELECTION_MODE`。

管理员账号不存在时，核心脚本会创建：

```text
用户名：admin
密码：Dormitory@2026
```

V13创建的系统管理员账号会保留，其密码必须已经由V14转换为Spring委托密码编码格式。V15的双模式套餐修订以及V16的在住与学生类别结构会保留并用于测试批次。

> 所有清空和测试数据脚本只能用于开发或测试环境，禁止在生产数据库执行。
