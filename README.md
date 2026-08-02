# 武汉科技大学学生宿舍智能选择系统

> 仓库：`Wust-Dormitory-Select`  
> 第一阶段：已完成并最终冻结  
> 第二阶段：开发中，房间布局、匹配运营、批次复制、国际化和学生状态管理已完成  
> 最后更新：2026-08-02

本项目面向高校新生宿舍选择与分配场景，为学生提供账号激活、个人偏好、房间推荐、邀请式组队、三维选床和住宿结果查询，为管理人员提供学生、宿舍、床位、批次、匹配规则、统一分配、结果导出、人工调整、学生重置和审计能力。

## 1. 核心业务闭环

```text
管理员维护专业、学生、宿舍、床位类型和房间布局
→ 配置匹配规则并创建或复制选寝批次
→ 准备全部学生资格和宿舍范围
→ 发布并开放选寝活动
→ 学生激活账号并填写个人偏好
→ 学生个人选择、邀请队友或使用随机推荐
→ Redis临时占用候选床位
→ 房间级服务器发送事件推送状态变化
→ MySQL事务确认最终分配
→ 管理员对全部未分配学生执行统一分配
→ 查询、导出、人工调整、学生重置和审计
```

学生是否参与某次活动由`batch_student_eligibility`决定。账号尚未激活的学生仍必须参加管理员统一分配。

## 2. 固定架构

```text
Vue 3 + TypeScript + Three.js
    ↓ REST + 服务器发送事件
OpenAPI 3契约
    ↓ 自动生成Java接口、数据传输对象和TypeScript类型
Spring Boot 4单体后端
    ├── MySQL 8.4：最终业务事实
    └── Redis 7.4：令牌、临时占用和可重建状态
```

固定边界：

- 所有对外接口先修改OpenAPI，再由生成器生成接口和模型；
- Controller只实现生成接口，不手写对外路由；
- Redis不是最终床位归属；
- 正式分配由MySQL事务和唯一约束确认；
- 前端不得自行写入正式分配；
- 个人偏好匹配使用确定性、可解释和可测试的算法；
- 当前按单实例部署，不引入不必要的注册中心和消息队列。

## 3. 已实现能力

### 学生端

- 学生账号激活、登录、退出、首次欢迎和个人资料；
- 中文和英语切换，外国学生按国籍自动选择受支持语言，无法匹配时回退英语；
- 国籍展示和手机号码自助修改；
- 完整个人偏好填写、查看与修改；
- 夏季空调、冬季取暖、熄灯后活动、闹钟和气味接受度等高影响偏好；
- 候选房间排序、楼层与剩余床位筛选；
- 匿名室友偏好、推荐理由和冲突提示；
- Three.js三维床位选择、直接换床和移动端适配；
- 三维场景读取每个房间的自定义床位坐标、旋转角度和床位类型；
- 邀请式组队、主页邀请确认、队友退出、队长移除和系统通知；
- 五人小组上限和未确认邀请失效规则；
- 多床位整体占用、确认和最终住宿结果查询。

### 管理端

- 专业、学生、楼栋、房间和床位资源管理；
- 学生国籍和手机号码维护；
- 学生密码重置和完全状态重置；
- 空闲床位类型维护及占用床位保护；
- 每个房间独立床位布局编辑；
- 画布内拖动定位、类型互斥按钮和顺时针90度旋转；
- 不提供横向坐标、纵向坐标和朝向手工输入；
- 上床下桌拆分为上下铺时新增独立下铺床位，房间容量自动增加，最多8人；
- 布局乐观锁、修改原因和审计；
- 匹配权重方案、不可变修订、启用切换和推荐解释；
- 中英文新生欢迎语配置；
- 批次创建、完整配置复制、准备、发布、开放、暂停、关闭和完成；
- 批次资格与楼栋、房间、床位开放范围；
- 全部未分配学生统一分配预演与提交；
- 未分配学生姓名、学号、失败代码和原因清单；
- 正式分配查询、CSV导出、人工换床和审计。

### 并发与实时

- Redis令牌化临时占用、原子释放和过期；
- MySQL双唯一约束与事务确认；
- 同一学生同一时刻只参加一个活动批次；
- 房间布局使用`room.version`避免管理员互相覆盖；
- 匹配方案使用不可变修订保证历史批次结果稳定；
- 批次复制使用单事务，异常资源会整体阻止复制；
- 房间级服务器发送事件连接、心跳和重连。

## 4. 学生与测试数据边界

学生档案字段：

```text
12位学号
姓名
性别
专业
ISO两位国籍代码
手机号码（可空）
```

本地全量测试数据由`backend-java/docs/sql/reset_and_seed_test_data.sql`生成：

- 5个专业；
- 20名学生，男女各10名；
- 10名国际学生；
- 全部学生账号为待激活状态；
- 全部20名学生具有测试批次资格，用于验证统一分配不依赖账号激活；
- 2栋宿舍楼、8个房间、36个床位；
- 包含四人间和五人间；
- 包含个人偏好、组队邀请、系统通知和统一分配失败原因样例。

房型和容量由独立可选床位数量自动同步。每个房间必须固定为男寝或女寝。

## 5. 数据库基线与数据字典

正式迁移：

```text
backend-java/server/src/main/resources/db/migration/
├── V1__create_phase1_schema.sql
├── V2__enforce_fixed_room_gender.sql
├── V3__normalize_major_and_minimize_student.sql
├── V4__refine_questionnaire_and_active_batch_rules.sql
├── V5__add_room_bed_layout.sql
├── V6__version_matching_weight_schemes.sql
├── V7__add_student_welcome_settings.sql
├── V8__expand_personal_preferences.sql
└── V9__add_student_contact_and_notifications.sql
```

完整数据字典：

```text
backend-java/docs/database-dictionary.md
```

固化结构：

```text
backend-java/docs/sql/schema.sql
```

每次新增或修改Flyway迁移时，必须同时更新数据字典和固化结构。固化命令：

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

`.env`中必须填写真实值，不能写成自引用表达式。

### 6.2 从零创建MySQL、Redis和测试数据

下面命令会清空项目本地MySQL和Redis数据，执行正式V1至最新版本迁移，建立测试管理员并导入V9全量测试数据：

```bash
bash scripts/dev/reset-local-environment.sh
```

无人值守执行：

```bash
bash scripts/dev/reset-local-environment.sh --yes
```

管理员开发账号：

```text
用户名：admin
密码：Dormitory@2026
```

学生示例：学号`202600000001`、姓名`张明宇`，首次使用需自行设置密码并激活。

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

## 7. 批次复制规则

管理员可以复制除“已取消”外的批次。复制时必须重新填写唯一批次编码、批次名称、开始时间、结束时间和复制原因。

新批次固定为草稿。系统复制个人偏好版本、匹配方案精确修订、选寝规则以及楼栋、房间、床位开放范围，但不复制学生资格、队伍、临时占用、分配结果和运行状态。只要源范围包含停用、维护或不可用资源，复制会整体失败并返回异常资源信息。

## 8. 自动化验证

```bash
python -m unittest scripts/dev/test_infra_config.py -v
python -m unittest scripts/db/test_database_baseline.py scripts/db/test_mysql_compatibility.py scripts/db/test_frozen_schema.py -v
python -m unittest scripts/api/test_openapi_contract.py -v
python -m unittest scripts/backend/test_phase1_source.py -v
python -m unittest scripts/frontend/test_frontend_baseline.py -v
python -m unittest scripts/ux/test_ux_refinement.py -v
python -m unittest scripts/experience/test_student_experience.py -v
python -m unittest scripts/phase2/test_room_layout.py -v
python -m unittest scripts/phase2/test_room_bed_type_and_preference_ui.py -v
python -m unittest scripts/phase2/test_team_i18n_layout_refinement.py -v
python -m unittest scripts/phase2/test_admin_layout_allocation_student_reset.py -v
python -m unittest scripts/phase2/test_matching_operations.py -v
python -m unittest scripts/phase2/test_batch_copy.py -v
mvn -f backend-java/pom.xml clean verify
cd frontend && npm run build
```

运行时完整流程还包括：

```bash
python scripts/e2e/admin_allocation_and_student_reset_smoke.py
```

GitHub Actions应在全新MySQL和Redis中执行Flyway V1至V9、Spring Boot健康检查以及第一、第二阶段完整HTTP流程。当前仓库若出现任务零步骤失败且没有日志，属于GitHub Actions运行器启动问题，不代表上述命令已经执行。

## 9. 第二阶段进度

1. **已完成：** 每个房间独立床位布局、床具类型和容量同步；
2. **已完成：** 匹配权重管理、不可变修订、冲突解释和推荐理由；
3. **已完成：** 学生个人偏好扩展、首次欢迎、国际化和房间筛选；
4. **已完成：** 邀请式组队、成员通知和五人上限；
5. **已完成：** 完整批次配置复制；
6. **已完成：** 全部学生统一分配、失败清单和学生账号/状态重置；
7. 待开发：导入安全、数据质量、统计、性能与恢复能力；
8. 待开发：分配优化和公平性评估。

第一阶段冻结边界不得被破坏：OpenAPI先行、Flyway只增不改、Redis不作为最终事实、关键分配事务化并可审计。

## 10. 文档入口

- [`docs/README.md`](docs/README.md)：文档总索引；
- [`docs/03_开发阶段/README.md`](docs/03_开发阶段/README.md)：阶段总览；
- [`docs/03_开发阶段/01_第一阶段/README.md`](docs/03_开发阶段/01_第一阶段/README.md)：第一阶段最终状态；
- [`docs/03_开发阶段/02_第二阶段/README.md`](docs/03_开发阶段/02_第二阶段/README.md)：第二阶段进度；
- [`backend-java/docs/database-dictionary.md`](backend-java/docs/database-dictionary.md)：V9数据库表与字段数据字典；
- [`backend-java/docs/sql/reset_and_seed_test_data.sql`](backend-java/docs/sql/reset_and_seed_test_data.sql)：全量测试数据重置脚本；
- [`AGENTS.md`](AGENTS.md)：项目开发约束。
