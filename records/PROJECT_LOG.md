# 项目开发总记录

> 项目：武汉科技大学学生宿舍智能选择系统  
> 创建日期：2026-08-01

本文件保存项目级关键节点。详细开发过程可在后续建立 `records/DAILY/`、`records/DECISIONS/` 和 `records/TESTS/` 子目录。

## 2026-08-01：仓库初始化检查与文档基线

### 用户要求

- 阅读 `wbh123/Wust-Dormitory-Select` 当前仓库；
- 参照“城安智序”项目的开发和文档管理方式；
- 添加项目文档；
- 修复 Java 包名与目录不一致问题；
- 将本轮改动最终压缩为一次提交。

### 已确认工程状态

- 仓库已包含 Java 21 多模块后端；
- 后端模块为 `model`、`client`、`server` 和 `starter`；
- 前端为 Vue 3、TypeScript 和 Vite；
- 当前主要内容仍是可复用模板和示例接口；
- 正式业务数据库、OpenAPI、页面和测试尚未建立；
- 初始化适配脚本的目录目标配置错误，导致部分 Java 文件目录与包声明不一致。

### 本轮决策

- 维持 Spring Boot 单体后端和 Vue 前端；
- 当前采用 MySQL 保存最终业务事实，Redis 保存临时占用和可重建缓存；
- 使用房间级服务器发送事件推送床位变化；
- 学院管理员与宿舍管理员现阶段合并；
- 系统按单实例设计；
- 匹配算法使用硬约束、特征向量和加权相似度，不使用大语言模型；
- 正式开发分为准备阶段、第一阶段核心功能、第二阶段完善和第三阶段可选功能；
- 文档结构参照“城安智序”采用需求、设计、阶段、规范和记录分层。

### 本轮文档

- 根目录 `README.md`；
- 根目录 `AGENTS.md`；
- `docs/README.md`；
- 项目范围与目标；
- 面向开发人员的需求设计；
- 面向甲方的需求说明；
- 业务架构与核心规则；
- 开发阶段总计划；
- 开发与记录规范。

### 工程修复范围

- 将 `server` 手写 Java 源码移动到与 `com.wust.dormitory.*` 包声明一致的目录；
- 将启动类移动到 `com.wust.dormitory` 根包目录；
- 将 MyBatis 映射文件移动到运行配置指定目录；
- 修正 `rename-framework.json` 中导致重复 `com` 和多余 `service` 层级的目标路径。

### 验证说明

本轮通过 GitHub 仓库内容和提交差异检查完成静态验证。由于当前执行环境未连接仓库本地工作区且缺少 GitHub 命令行工具，本轮不声称 Maven、前端构建或运行测试已经通过。后续应在本地或持续集成环境执行：

```bash
python -m unittest backend-java/scripts/test_rename_framework.py -v
mvn -f backend-java/pom.xml clean package -DskipTests
cd frontend && npm ci && npm run build
```

父级物料清单工程和本地数据库依赖需要按实际环境准备。

### 下一步

- 清理模板示例和前端默认页面；
- 确认父级物料清单是否能够稳定获取；
- 设计第一阶段数据库和 OpenAPI；
- 建立根目录配置示例、Docker 开发基础设施和自动化测试；
- 编写第一阶段详细开发计划。
