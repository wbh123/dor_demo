# Local Infrastructure Compose Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为本地开发提供只包含 MySQL 与 Redis 的 Docker Compose 基础设施，并让 Spring Boot 使用同一份根目录环境配置。

**Architecture:** MySQL 和 Redis 在 Docker 中运行，Spring Boot 与 Vue 在宿主机或 WSL 中运行。根目录 `.env` 同时作为 Docker Compose 和 Spring Boot 的配置入口，数据库与 Redis 数据通过绑定目录持久化。

**Tech Stack:** Docker Compose、MySQL 8.4、Redis 7.4、Bash、Python 标准库单元测试、Spring Boot 4、Spring Data Redis。

## Global Constraints

- Compose 只包含 MySQL 和 Redis。
- 后端与前端不容器化。
- 根目录 `.env` 不提交版本库。
- MySQL 最终保存业务事实；Redis 只保存临时状态和可重建缓存。
- 不引入 MinIO、消息队列、注册中心或复杂微服务。

---

### Task 1: 建立测试基线

**Files:**
- Create: `scripts/dev/test_infra_config.py`

**Interfaces:**
- Consumes: 仓库根目录、Compose、环境模板、Spring 配置与 Maven 配置。
- Produces: `InfrastructureConfigurationTest`，可通过 `python -m unittest scripts/dev/test_infra_config.py -v` 执行。

- [x] **Step 1: 编写失败测试**
  - 验证 Compose 服务范围、健康检查和持久化目录；
  - 验证环境变量模板；
  - 验证 Spring Boot 环境变量接入；
  - 验证 Redis Starter；
  - 验证 Bash 脚本语法和环境校验行为。
- [x] **Step 2: 执行测试并确认因生产文件缺失而失败**
- [x] **Step 3: 后续任务完成后重新执行并确认全部通过**

### Task 2: 建立 Compose 与统一配置

**Files:**
- Create: `.env.example`
- Create: `docker/docker-compose.yml`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: `WUST_DORMITORY_*` 环境变量。
- Produces: `mysql`、`redis` 两个服务和 `wust-dormitory-network` 网络。

- [x] **Step 1: 添加 MySQL 8.4 服务**
- [x] **Step 2: 添加 Redis 7.4 服务**
- [x] **Step 3: 配置健康检查、密码和数据目录**
- [x] **Step 4: 忽略 `.env` 与 `data/`**

### Task 3: 建立开发脚本

**Files:**
- Create: `scripts/dev/validate-env.sh`
- Create: `scripts/dev/start-infra.sh`

**Interfaces:**
- Consumes: 根目录 `.env` 与 `docker/docker-compose.yml`。
- Produces: `up|down|restart|status|logs|config` 命令入口。

- [x] **Step 1: 实现不执行 Shell 展开的环境配置解析**
- [x] **Step 2: 校验必填项、占位值、端口和密码约束**
- [x] **Step 3: 实现 Compose 启停与健康等待**
- [x] **Step 4: 失败时输出服务日志**

### Task 4: 接入 Spring Boot

**Files:**
- Modify: `backend-java/starter/src/main/resources/application.yaml`
- Modify: `backend-java/server/pom.xml`

**Interfaces:**
- Consumes: `.env` 中的数据库和 Redis 配置。
- Produces: `DataSource`、`RedisConnectionFactory` 和 Actuator 健康端点。

- [x] **Step 1: 导入根目录 `.env`**
- [x] **Step 2: 移除明文数据库账号和密码**
- [x] **Step 3: 添加 Redis 连接配置**
- [x] **Step 4: 添加 Spring Data Redis Starter**

### Task 5: 文档、记录与最终验证

**Files:**
- Create: `docker/README.md`
- Modify: `README.md`
- Modify: `docs/README.md`
- Modify: `records/PROJECT_LOG.md`
- Create: `records/DAILY/2026-08-01_15-25-00_本地基础设施DockerCompose.md`

**Interfaces:**
- Produces: 从零启动、检查、日志、停止、数据保留和本地验证说明。

- [x] **Step 1: 记录部署步骤和运行边界**
- [x] **Step 2: 执行 Python 单元测试**
- [x] **Step 3: 执行 Bash 语法检查**
- [x] **Step 4: 执行 YAML 与 XML 静态解析**
- [ ] **Step 5: 在具备 Docker 的本地环境执行真实容器健康验证**
