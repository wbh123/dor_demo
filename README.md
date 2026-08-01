# 武汉科技大学学生宿舍智能选择系统

> 仓库名称：`Wust-Dormitory-Select`  
> 当前阶段：第一阶段——核心选寝闭环开发  
> 当前子阶段：数据库与测试数据基线  
> 最后更新：2026-08-01

本项目面向高校新生宿舍选择与分配场景，为学生提供自主选寝、组队选寝、随机选择和生活习惯匹配，为管理人员提供学生与宿舍资源维护、批次配置、系统分配、过程监管、结果导出和异常调整能力。

## 1. 核心业务闭环

```text
管理人员导入学生与宿舍资源
→ 创建选寝批次并配置可选房间、时间和规则
→ 学生填写生活习惯问卷
→ 学生个人选寝、组队选寝或随机选择
→ Redis临时占用候选床位
→ 房间级服务器发送事件推送状态变化
→ 数据库事务确认最终分配
→ 未选学生统一分配或人工补充
→ 结果导出、审计和异常调整
```

## 2. 当前宿舍规则与可扩展边界

当前学校需求：

- 男生宿舍为五人间；
- 男生五人间包含3个上床下桌和1组上下铺；
- 女生宿舍为四人间；
- 女生四人间包含4个上床下桌。

该房型组合不是永久限制。数据库已经将房型、容量、床位布局和宿舍性别分开建模，后续可以支持男生、女生分别存在四人间和五人间混合的情况。

固定边界：

- 男女不混住；
- 每个房间必须明确设置为男寝或女寝；
- 宿舍楼可以在未来按房间分别配置性别；
- 管理员通过批次楼栋、房间和床位范围精确设置哪些宿舍可选；
- 学生性别必须与目标房间性别一致。

## 3. 总体架构

```text
Vue 3前端（宿主机）
    ↓ REST + 服务器发送事件
Spring Boot单体后端（宿主机）
    ├── MySQL容器：最终业务事实
    └── Redis容器：临时占用和可重建缓存
```

固定规则：

- Redis临时占用不是最终床位归属；
- 最终分配由数据库事务和唯一约束确认；
- 前端不得自行计算正式分配结果；
- 系统当前按单实例部署；
- 不引入注册中心、消息队列和复杂分布式事务；
- 生活习惯匹配采用可解释的确定性算法，不使用大语言模型。

## 4. 工程结构

```text
backend-java/
├── model/             OpenAPI契约和生成模型
├── client/            生成客户端
├── server/            业务、持久层和Flyway迁移
└── starter/           Spring Boot启动和运行配置

frontend/              Vue 3 + TypeScript + Vite
docker/                MySQL与Redis本地基础设施
scripts/dev/            基础设施启动与校验
scripts/db/             测试数据生成、数据库测试和固化工具
docs/                  需求、设计、阶段和规范文档
records/               开发过程记录
```

## 5. 第一阶段数据库基线

当前已经建立：

- 30张第一阶段正式业务表；
- Flyway V1和V2正式结构迁移；
- 520名合成学生，男女各260名；
- 8栋宿舍楼、32层；
- 64个男生五人间；
- 80个女生四人间；
- 144个房间、640个独立床位；
- 男女床位各320个；
- 学号范围 `202600000001` 至 `202600000520`；
- 问卷、匹配权重、草稿批次、资格和标准化特征测试数据。

正式迁移：

```text
backend-java/server/src/main/resources/db/migration/
├── V1__create_phase1_schema.sql
└── V2__enforce_fixed_room_gender.sql
```

开发测试数据：

```text
backend-java/server/src/test/resources/db/dev-migration/
└── R__development_test_data.sql
```

## 6. 数据库演进与固化

开发期间只通过Flyway新增迁移：

```text
修改设计
→ 新增版本迁移
→ 空库和升级验证
→ 更新文档
```

第一阶段全部功能开发并验收完成后执行：

```bash
python scripts/db/build_frozen_baseline.py
```

生成可独立执行的固化脚本：

```text
backend-java/docs/sql/schema.sql
```

开发测试数据不会进入固化结构脚本。

## 7. 本地启动

### 7.1 启动MySQL和Redis

```bash
cp .env.example .env
# 修改数据库业务密码、数据库根密码和Redis密码
bash scripts/dev/start-infra.sh up
```

查看状态：

```bash
bash scripts/dev/start-infra.sh status
```

### 7.2 正式结构迁移

`.env`默认配置：

```properties
WUST_DORMITORY_FLYWAY_LOCATIONS=classpath:db/migration
```

从仓库根目录启动后端时，Flyway自动执行正式迁移。

### 7.3 加载本地开发测试数据

仅在专用本地开发数据库中，将 `.env` 改为：

```properties
WUST_DORMITORY_FLYWAY_LOCATIONS=classpath:db/migration,filesystem:backend-java/server/src/test/resources/db/dev-migration
```

生产环境不得加入 `db/dev-migration`。

### 7.4 构建后端

```bash
mvn -f backend-java/pom.xml clean package -DskipTests
java -jar backend-java/starter/target/Service.jar
```

### 7.5 构建前端

```bash
cd frontend
npm ci
npm run build
```

## 8. 自动化验证

```bash
python -m unittest scripts/dev/test_infra_config.py -v
python -m unittest scripts/db/test_database_baseline.py -v
```

数据库测试覆盖：

- 12位学号格式和唯一性；
- 男女学生数量；
- 男五人间、女四人间当前布局；
- 男女床位容量；
- 房型后续混合扩展能力；
- 房间固定男寝或女寝；
- 批次按房间精确配置能力；
- 最终分配唯一约束；
- Flyway配置；
- 测试数据可重复生成；
- 固化脚本只合并正式版本迁移。

## 9. 开发阶段

| 阶段 | 状态 | 目标 |
|---|---|---|
| 准备阶段 | 已完成 | 工程修复、文档和MySQL/Redis环境 |
| 第一阶段 | 开发中 | 核心选寝业务闭环 |
| 第二阶段 | 待开发 | 质量、匹配和运营能力完善 |
| 第三阶段 | 待评估 | 换寝、通知和分析等可选功能 |

第一阶段后续顺序：

1. 统一响应、错误码和认证；
2. 学生、组织和宿舍资源接口；
3. 批次、资格、问卷和匹配；
4. 个人临时占用与最终提交；
5. 房间级服务器发送事件；
6. 组队选寝；
7. 随机分配、人工调整、导出和审计；
8. 全链路验收与冻结。

## 10. 文档入口

- [`docs/README.md`](docs/README.md)：项目文档索引；
- [`docs/03_开发阶段/01_第一阶段/README.md`](docs/03_开发阶段/01_第一阶段/README.md)：第一阶段入口；
- [`docs/03_开发阶段/01_第一阶段/02_数据库与数据口径设计.md`](docs/03_开发阶段/01_第一阶段/02_数据库与数据口径设计.md)：数据库设计；
- [`docs/03_开发阶段/01_第一阶段/03_测试数据使用说明.md`](docs/03_开发阶段/01_第一阶段/03_测试数据使用说明.md)：测试数据说明；
- [`backend-java/docs/database-dictionary.md`](backend-java/docs/database-dictionary.md)：数据库字典；
- [`docker/README.md`](docker/README.md)：本地基础设施；
- [`AGENTS.md`](AGENTS.md)：项目级开发约束。
