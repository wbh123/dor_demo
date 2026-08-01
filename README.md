# 武汉科技大学学生宿舍智能选择系统

> 仓库：`Wust-Dormitory-Select`  
> 第一阶段：已完成并最终冻结  
> 第二阶段：开发中  
> 最后更新：2026-08-01

本项目面向高校新生宿舍选择与分配场景，为学生提供问卷、个人选寝、邀请式组队选寝、随机推荐和三维床位选择，为管理人员提供专业、学生、宿舍、批次、统一分配、结果导出、人工调整和审计能力。

## 1. 第一阶段最终闭环

```text
管理人员维护专业、学生与宿舍资源
→ 创建、准备并开放选寝活动
→ 学生激活账号并填写生活习惯问卷
→ 学生个人选择、邀请队友或使用随机推荐
→ Redis临时占用候选床位
→ 房间级服务器发送事件推送状态变化
→ MySQL事务确认最终分配
→ 管理员统一分配未选学生
→ 查询、导出、人工调整和审计
```

第一阶段：已完成并最终冻结。第二阶段：开发中。

## 2. 固定架构

```text
Vue 3 + TypeScript + Three.js
    ↓ REST + 服务器发送事件
OpenAPI 3契约
    ↓ 自动生成Java API、数据传输对象和TypeScript类型
Spring Boot 4单体后端
    ├── MySQL 8.4：最终业务事实
    └── Redis 7.4：令牌、临时占用和可重建状态
```

固定边界：

- 对外接口先写OpenAPI，再由生成器生成接口和模型；
- Controller只实现生成接口，不手写对外路由；
- Redis不是最终床位归属；
- 正式分配由MySQL事务和唯一约束确认；
- 前端不得自行写入正式分配；
- 生活习惯匹配使用确定性、可解释和可测试的算法；
- 当前按单实例部署，不引入不必要的注册中心和消息队列。

## 3. 第一阶段能力

### 学生端

- 学生账号激活、登录、退出和个人资料；
- 三态吸烟偏好等生活习惯问卷；
- 问卷结果查看与修改；
- 房间匹配排序、匿名室友偏好和随机推荐；
- Three.js三维床位选择、下拉框联动和移动端适配；
- 个人床位临时占用、直接换床和最终确认；
- 邀请式组队：输入12位学号直接邀请，系统内部自动建立小组；
- 队伍名称和内部编号不在学生端展示；
- 多床位整体占用与确认；
- 最终住宿结果查询。

### 管理端

- 专业、学生、楼栋、房间和床位资源管理；
- 学生批量导入；
- 批次创建、准备、发布、开放、暂停、关闭和完成；
- 批次资格与楼栋、房间、床位开放范围；
- 未选学生统一分配预演与提交；
- 正式分配查询、CSV导出、人工换床和审计。

### 并发与实时

- Redis令牌化临时占用、原子释放和过期；
- MySQL双唯一约束与事务确认；
- 同一学生同一时刻只参加一个活动批次；
- 房间级服务器发送事件连接、心跳和重连。

## 4. 当前宿舍与数据边界

学生业务信息仅保留：

```text
学号
姓名
性别
专业编号
```

当前开发数据：

- 5个专业；
- 520名合成学生，男女各260名；
- 8栋宿舍楼、32层；
- 64个男生五人间、80个女生四人间；
- 144个房间、640个床位。

当前男生五人间为3个上床下桌和1组上下铺；女生四人间为4个上床下桌。房型可扩展，但每个房间必须固定为男寝或女寝。

## 5. 数据库基线

正式迁移：

```text
backend-java/server/src/main/resources/db/migration/
├── V1__create_phase1_schema.sql
├── V2__enforce_fixed_room_gender.sql
├── V3__normalize_major_and_minimize_student.sql
└── V4__refine_questionnaire_and_active_batch_rules.sql
```

固化结构：

```text
backend-java/docs/sql/schema.sql
```

固化命令：

```bash
python scripts/db/build_frozen_baseline.py
```

正式应用只加载正式迁移。开发测试数据不会写入Flyway历史，而是由本地数据库重建脚本通过MySQL客户端导入。

## 6. 本地开发

### 6.1 配置环境

```bash
cp .env.example .env
# 填写数据库业务密码、根密码和Redis密码
```

`.env`中必须填写真实值，不能写成自引用表达式，例如：

```properties
WUST_DORMITORY_DB_USER=wust_dormitory_dev
```

### 6.2 从零创建MySQL、Redis和测试数据

下面命令只操作数据库与Docker容器，不构建或启动前后端：

```bash
bash scripts/dev/reset-local-environment.sh
```

无人值守执行：

```bash
bash scripts/dev/reset-local-environment.sh --yes
```

该脚本会删除本地`data/mysql`和`data/redis`，执行Flyway V1至V4，再直接导入520名学生和640个床位的开发数据。

### 6.3 启动后端

```bash
mvn -f backend-java/pom.xml clean package
java -jar backend-java/starter/target/Service.jar
```

### 6.4 启动前端

```bash
cd frontend
npm install
npm run dev
```

管理员开发账号：

```text
用户名：admin
密码：Dormitory@2026
```

学生示例：学号`202600000001`、姓名`测试男生001`，首次使用需自行设置密码并激活。

## 7. 自动化验证

```bash
python -m unittest scripts/dev/test_infra_config.py -v
python -m unittest scripts/db/test_database_baseline.py scripts/db/test_mysql_compatibility.py scripts/db/test_frozen_schema.py -v
python -m unittest scripts/api/test_openapi_contract.py -v
python -m unittest scripts/backend/test_phase1_source.py -v
python -m unittest scripts/frontend/test_frontend_baseline.py -v
python -m unittest scripts/ux/test_ux_refinement.py -v
mvn -f backend-java/pom.xml clean verify
cd frontend && npm run build
```

GitHub Actions还会在全新MySQL和Redis中执行Flyway、Spring Boot健康检查和完整HTTP业务闭环。

## 8. 第二阶段

第二阶段：开发中。按照文档优先实施：

1. 每个房间独立床位布局和上下铺位置配置；
2. 匹配权重、冲突解释与运营界面；
3. 批次复制、规则模板和复杂组队异常处理；
4. 导入安全、数据质量、统计、性能与恢复能力；
5. 分配优化和公平性评估。

第一阶段冻结边界不得被破坏：OpenAPI先行、Flyway只增不改、Redis不作为最终事实、关键分配事务化并可审计。

## 9. 文档入口

- [`docs/README.md`](docs/README.md)：文档总索引；
- [`docs/03_开发阶段/README.md`](docs/03_开发阶段/README.md)：阶段总览；
- [`docs/03_开发阶段/01_第一阶段/README.md`](docs/03_开发阶段/01_第一阶段/README.md)：第一阶段最终状态；
- [`docs/03_开发阶段/01_第一阶段/07_第一阶段冻结说明.md`](docs/03_开发阶段/01_第一阶段/07_第一阶段冻结说明.md)：冻结与验收证据；
- [`backend-java/docs/database-dictionary.md`](backend-java/docs/database-dictionary.md)：数据库字典；
- [`AGENTS.md`](AGENTS.md)：项目开发约束。
