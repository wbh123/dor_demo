# 武汉科技大学学生宿舍智能选择系统

> 仓库名称：`Wust-Dormitory-Select`  
> 当前阶段：第一阶段——核心选寝闭环已完成并冻结  
> 下一阶段：第二阶段——质量、匹配与运营能力完善  
> 最后更新：2026-08-01

本项目面向高校新生宿舍选择与分配场景，为学生提供自主选寝、组队选寝、随机推荐和生活习惯匹配，为管理人员提供专业、学生与宿舍资源维护、批次配置、统一分配、结果导出、审计和人工调整能力。

## 1. 第一阶段业务闭环

```text
管理人员维护专业、学生与宿舍资源
→ 创建并准备选寝批次
→ 学生激活账号并填写生活习惯问卷
→ 学生个人选寝、组队选寝或随机推荐
→ Redis临时占用候选床位
→ 房间级服务器发送事件推送状态变化
→ MySQL事务确认最终分配
→ 管理员统一分配未选学生
→ 查询、导出、人工调整和审计
```

## 2. 固定架构边界

```text
Vue 3前端
    ↓ REST + 服务器发送事件
OpenAPI契约
    ↓ 自动生成Java API、数据传输对象和TypeScript类型
Spring Boot单体后端
    ├── MySQL：最终业务事实
    └── Redis：临时占用、令牌和可重建状态
```

固定规则：

- 所有对外接口先编写OpenAPI契约，再由生成器生成`*Api`接口和数据传输对象；
- Controller只实现生成接口，不手写接口路径和请求模型；
- Redis临时占用不是最终床位归属；
- 最终分配由MySQL事务和双唯一约束确认；
- 前端不得自行计算或写入正式分配结果；
- 当前按单实例部署，不引入注册中心、消息队列和复杂分布式事务；
- 生活习惯匹配使用可解释、确定性的规则算法，不使用大语言模型。

## 3. 学生与专业数据边界

学生业务信息只保留：

```text
学号
姓名
性别
专业编号
```

数据库关系：

```text
student.major_id → major.id
app_user.student_id → student.id
batch_student_eligibility.student_id → student.id
```

学生表不保存班级、年级、学院、校区、专业名称快照、永久住宿资格或账号字段。

## 4. 当前宿舍规则

当前测试和第一阶段验收数据采用：

- 男生五人间：3个上床下桌和1组上下铺；
- 女生四人间：4个上床下桌；
- 房间必须明确设置为男寝或女寝；
- 管理员可按批次配置楼栋、房间和床位范围；
- 男女不得混住。

房型、容量、房间性别和床位布局分开建模，后续可扩展男女各自四人间、五人间混合配置。

## 5. 工程结构

```text
backend-java/
├── model/             OpenAPI契约与生成模型
├── client/            生成客户端
├── server/            业务、持久层和Flyway迁移
└── starter/           Spring Boot启动配置

frontend/              Vue 3 + TypeScript + Vite
docker/                MySQL与Redis本地基础设施
scripts/api/            OpenAPI契约测试
scripts/backend/        后端架构和源码约束测试
scripts/db/             测试数据、迁移、兼容性和固化测试
scripts/e2e/            第一阶段完整HTTP主流程验收
scripts/frontend/       前端路由与页面基线测试
docs/                  需求、设计、阶段和冻结文档
records/               开发过程记录
```

## 6. 第一阶段已实现能力

### 6.1 后端与接口

- 认证、账号激活、登录、退出和当前用户；
- 专业、学生、楼栋、房间和床位资源管理；
- 学生批量导入和稳定错误响应；
- 批次创建、准备、发布、开放、暂停、关闭和完成；
- 批次资格、楼栋、房间和床位范围；
- 问卷提交、特征标准化、匹配排序和随机推荐；
- 个人床位占用、释放和最终确认；
- 组队邀请、接受、锁定、多床位整体占用和确认；
- 房间级服务器发送事件连接与心跳；
- 锁定队伍优先的管理员统一分配；
- 正式分配查询、CSV导出、人工换床和审计。

### 6.2 前端

- 学生登录、激活、首页、问卷、房间列表和房间详情；
- 个人与组队选寝、实时状态和倒计时；
- 分配结果查看；
- 管理员工作台、专业学生、宿舍资源、批次和分配调整页面；
- 构建前自动从同一OpenAPI生成TypeScript类型。

## 7. 数据库基线

第一阶段正式迁移：

```text
backend-java/server/src/main/resources/db/migration/
├── V1__create_phase1_schema.sql
├── V2__enforce_fixed_room_gender.sql
└── V3__normalize_major_and_minimize_student.sql
```

已固化独立结构快照：

```text
backend-java/docs/sql/schema.sql
```

该文件由以下命令生成：

```bash
python scripts/db/build_frozen_baseline.py
```

持续集成会验证固化文件与Flyway迁移完全一致。开发测试数据位于`src/test/resources`，不会写入正式结构快照。

第一阶段开发数据：

- 5个专业；
- 520名合成学生，男女各260名；
- 8栋宿舍楼、32层；
- 64个男生五人间、80个女生四人间；
- 144个房间、640个独立床位；
- 男女床位各320个。

## 8. 本地启动

### 8.1 启动MySQL和Redis

```bash
cp .env.example .env
# 修改数据库业务密码、根密码和Redis密码
bash scripts/dev/start-infra.sh up
```

### 8.2 启动后端

```bash
mvn -f backend-java/pom.xml clean package
java -jar backend-java/starter/target/Service.jar
```

默认只执行正式Flyway迁移。加载开发数据时，在专用开发环境设置：

```properties
WUST_DORMITORY_FLYWAY_LOCATIONS=classpath:db/migration,filesystem:backend-java/server/src/test/resources/db/dev-migration
```

生产环境不得加入开发数据目录。

### 8.3 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端启动和构建前会自动生成OpenAPI类型。

## 9. 自动化验证

```bash
python -m unittest scripts/dev/test_infra_config.py -v
python -m unittest \
  scripts/db/test_database_baseline.py \
  scripts/db/test_mysql_compatibility.py \
  scripts/db/test_frozen_schema.py -v
python -m unittest scripts/api/test_openapi_contract.py -v
python -m unittest scripts/backend/test_phase1_source.py -v
python -m unittest scripts/frontend/test_frontend_baseline.py -v
mvn -f backend-java/pom.xml clean verify
cd frontend && npm run build
```

GitHub Actions还会在MySQL 8.4和Redis 7.4中执行：

- Flyway V1至V3和开发数据迁移；
- Spring Boot健康检查；
- 管理员登录与受保护接口；
- 学生激活、登录、问卷和候选房间；
- Redis床位临时占用；
- 最终分配事务；
- 管理员分配查询、人工调整与审计。

## 10. 开发阶段

| 阶段 | 状态 | 目标 |
|---|---|---|
| 准备阶段 | 已完成 | 工程修复、文档和MySQL/Redis环境 |
| 第一阶段 | 已完成并冻结 | 核心选寝业务闭环 |
| 第二阶段 | 待开发 | 质量、匹配和运营能力完善 |
| 第三阶段 | 待评估 | 换寝、通知和分析等可选功能 |

## 11. 文档入口

- [`docs/README.md`](docs/README.md)：项目文档索引；
- [`docs/03_开发阶段/01_第一阶段/README.md`](docs/03_开发阶段/01_第一阶段/README.md)：第一阶段入口；
- [`docs/03_开发阶段/01_第一阶段/07_第一阶段冻结说明.md`](docs/03_开发阶段/01_第一阶段/07_第一阶段冻结说明.md)：第一阶段冻结与验收证据；
- [`backend-java/docs/database-dictionary.md`](backend-java/docs/database-dictionary.md)：数据库字典；
- [`backend-java/docs/sql/schema.sql`](backend-java/docs/sql/schema.sql)：第一阶段固化数据库结构；
- [`docker/README.md`](docker/README.md)：本地基础设施；
- [`AGENTS.md`](AGENTS.md)：项目级开发约束。
