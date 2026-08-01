# 本地基础设施 Docker Compose 设计

> 日期：2026-08-01  
> 状态：已确认  
> 方案：A——仅容器化基础设施

## 1. 目标

为本项目提供可重复启动、可检查、可持久化的本地开发基础设施。Docker Compose 只运行 MySQL 和 Redis，Spring Boot 与 Vue 继续在宿主机或 WSL 中运行，以保留最快的调试循环。

## 2. 架构

```text
Vue 3（宿主机）
        ↓
Spring Boot（宿主机）
        ├── localhost:3306 → MySQL 容器
        └── localhost:6379 → Redis 容器
```

本轮不容器化后端与前端，不增加 MinIO、消息队列、注册中心或管理面板。

## 3. 配置原则

- 根目录 `.env` 是本地运行参数的唯一持久化入口；
- `.env.example` 提供完整模板，但不得包含真实密码；
- Docker Compose 通过 `--env-file .env` 读取配置；
- Spring Boot 通过 `spring.config.import` 读取同一份 `.env`；
- `application.yaml` 不再保存明文数据库密码；
- `.env` 和 `data/` 必须被 Git 忽略。

## 4. MySQL

- 使用 MySQL 8.4 长期支持版本镜像；
- 字符集固定为 `utf8mb4`；
- 使用独立业务账号，不允许后端使用 `root`；
- 数据挂载到 `data/mysql/`；
- 提供 `mysqladmin ping` 健康检查；
- 数据库名、账号、密码、端口和镜像均由环境变量提供。

## 5. Redis

- 使用 Redis 7.4 Alpine 镜像；
- 开启追加文件持久化；
- 必须启用密码认证；
- 数据挂载到 `data/redis/`；
- 提供带认证的 `redis-cli ping` 健康检查；
- Redis 只用于临时占用、热点状态和可重建缓存，不保存最终床位归属。

## 6. 开发脚本

- `validate-env.sh`：以不执行 Shell 展开的方式读取 `.env`，检查必填项、占位值、端口、账号和密码约束；
- `start-infra.sh`：支持 `up`、`down`、`restart`、`status`、`logs` 和 `config`；
- 启动脚本负责创建数据目录、校验 Compose 配置并等待健康检查；
- 服务失败或等待超时时输出对应容器日志。

## 7. Spring Boot 接入

- 数据源配置全部改为环境变量；
- 增加 Spring Data Redis 依赖；
- Redis 主机、端口、密码、数据库编号和超时均由环境变量控制；
- 保留 Actuator 健康检查入口，供后续本地联调和持续集成使用。

## 8. 测试与验收

自动化静态测试至少验证：

1. Compose 只包含 MySQL 与 Redis；
2. 两个服务均有健康检查和持久化目录；
3. `.env.example` 包含全部必填变量；
4. Spring Boot 使用统一环境变量且不存在模板明文密码；
5. 后端包含 Redis Starter；
6. 环境校验脚本接受合法配置并拒绝占位值；
7. Bash、YAML 和 XML 文件语法有效。

完整运行验收需要在安装 Docker 的本地环境执行 Compose 启动，并确认 MySQL、Redis 均为 `healthy`。