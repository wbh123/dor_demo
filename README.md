# 武汉科技大学学生宿舍智能选择系统

> 仓库：`Wust-Dormitory-Select`  
> 第一阶段：已完成并最终冻结  
> 第二阶段：开发中，房间布局、匹配运营与批次复制已完成  
> 最后更新：2026-08-02

本项目面向高校新生宿舍选择与分配场景，为学生提供账号激活、个人偏好、房间推荐、邀请式组队、三维选床和住宿结果查询，为管理人员提供学生、宿舍、床位、批次、匹配规则、统一分配、结果导出、人工调整和审计能力。

## 1. 核心业务闭环

```text
管理员维护专业、学生、宿舍、床位类型和房间布局
→ 配置匹配规则并创建或复制选寝批次
→ 准备、发布并开放选寝活动
→ 学生激活账号并填写个人偏好
→ 学生个人选择、邀请队友或使用随机推荐
→ Redis临时占用候选床位
→ 房间级服务器发送事件推送状态变化
→ MySQL事务确认最终分配
→ 管理员统一分配未选学生
→ 查询、导出、人工调整和审计
```

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

- 所有对外接口先修改OpenAPI，再由生成器生成接口和模型；
- Controller只实现生成接口，不手写对外路由；
- Redis不是最终床位归属；
- 正式分配由MySQL事务和唯一约束确认；
- 前端不得自行写入正式分配；
- 生活习惯匹配使用确定性、可解释和可测试的算法；
- 当前按单实例部署，不引入不必要的注册中心和消息队列。

## 3. 已实现能力

### 学生端

- 学生账号激活、登录、退出、首次欢迎和个人资料；
- 完整个人偏好填写、查看与修改；
- 夏季空调、冬季取暖、熄灯后活动、闹钟和气味接受度等高影响偏好；
- 候选房间排序、楼层与剩余床位筛选；
- 匿名室友偏好、推荐理由和冲突提示；
- Three.js三维床位选择、下拉框联动、直接换床和移动端适配；
- 三维场景读取每个房间的自定义床位坐标、朝向和床位类型；
- 邀请式组队：输入12位学号直接邀请，系统内部自动建立小组；
- 小组名称和内部编号不在学生端展示；
- 多床位整体占用、确认和最终住宿结果查询。

### 管理端

- 专业、学生、楼栋、房间和床位资源管理；
- 空闲床位类型维护及占用床位保护；
- 每个房间独立床位布局编辑；
- 俯视拖拽、0.25单位吸附、90度旋转、数值输入和上下铺整体移动；
- 布局乐观锁、修改原因和审计；
- 匹配权重方案、不可变修订、启用切换和推荐解释；
- 新生欢迎语配置；
- 批次创建、完整配置复制、准备、发布、开放、暂停、关闭和完成；
- 批次资格与楼栋、房间、床位开放范围；
- 未选学生统一分配预演与提交；
- 正式分配查询、CSV导出、人工换床和审计。

### 并发与实时

- Redis令牌化临时占用、原子释放和过期；
- MySQL双唯一约束与事务确认；
- 同一学生同一时刻只参加一个活动批次；
- 房间布局使用`room.version`避免管理员互相覆盖；
- 匹配方案使用不可变修订保证历史批次结果稳定；
- 批次复制使用单事务，异常资源会整体阻止复制；
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

男生五人间当前为3个上床下桌和1组上下铺，女生四人间为4个上床下桌。房型和床位类型允许后续调整，但每个房间必须固定为男寝或女寝。

## 5. 数据库基线

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
└── V8__expand_personal_preferences.sql
```

批次复制复用现有批次与范围表，不新增数据库结构。固化结构位于：

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

`.env`中必须填写真实值，不能写成自引用表达式：

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

## 7. 批次复制规则

管理员可以复制除“已取消”外的批次。复制时必须重新填写：

```text
唯一批次编码
批次名称
开始时间
结束时间
复制原因
```

新批次固定为草稿。系统复制问卷版本、匹配方案精确修订、选寝规则以及楼栋、房间、床位开放范围，但不复制学生资格、队伍、临时占用、分配结果和运行状态。只要源范围包含停用、维护或不可用资源，复制会整体失败并返回异常资源信息。

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
python -m unittest scripts/phase2/test_matching_operations.py -v
python -m unittest scripts/phase2/test_batch_copy.py -v
mvn -f backend-java/pom.xml clean verify
cd frontend && npm run build
```

GitHub Actions会在全新MySQL和Redis中执行Flyway V1至V8、Spring Boot健康检查以及第一、第二阶段完整HTTP流程。

## 9. 第二阶段进度

1. **已完成：** 每个房间独立床位布局和上下铺位置配置；
2. **已完成：** 匹配权重管理、不可变修订、冲突解释和推荐理由；
3. **已完成：** 学生个人偏好扩展、首次欢迎、房间筛选和床位类型维护；
4. **已完成：** 完整批次配置复制；
5. **当前开发：** 规则模板与复杂组队异常处理；
6. 待开发：导入安全、数据质量、统计、性能与恢复能力；
7. 待开发：分配优化和公平性评估。

第一阶段冻结边界不得被破坏：OpenAPI先行、Flyway只增不改、Redis不作为最终事实、关键分配事务化并可审计。

## 10. 文档入口

- [`docs/README.md`](docs/README.md)：文档总索引；
- [`docs/03_开发阶段/README.md`](docs/03_开发阶段/README.md)：阶段总览；
- [`docs/03_开发阶段/01_第一阶段/README.md`](docs/03_开发阶段/01_第一阶段/README.md)：第一阶段最终状态；
- [`docs/03_开发阶段/02_第二阶段/README.md`](docs/03_开发阶段/02_第二阶段/README.md)：第二阶段进度；
- [`backend-java/docs/database-dictionary.md`](backend-java/docs/database-dictionary.md)：数据库字典；
- [`AGENTS.md`](AGENTS.md)：项目开发约束。
