# 本地 Docker 基础设施

本目录提供开发阶段需要的 MySQL 和 Redis。Spring Boot 后端与 Vue 前端仍在宿主机或 WSL 中运行，便于断点调试、热更新和快速重启。

## 1. 服务范围

| 服务 | 容器名 | 默认端口 | 数据目录 | 用途 |
|---|---|---:|---|---|
| MySQL | `wust-dormitory-mysql` | 3306 | `data/mysql/` | 最终业务事实、批次、床位、分配和审计 |
| Redis | `wust-dormitory-redis` | 6379 | `data/redis/` | 临时占用、热点状态和可重建缓存 |

本轮不包含 Java 后端、Vue 前端、MinIO、消息队列、注册中心或管理面板。

## 2. 准备配置

所有命令默认在仓库根目录执行。

```bash
cp .env.example .env
```

编辑 `.env`，至少替换以下三项：

```dotenv
WUST_DORMITORY_DB_PASSWORD=设置业务数据库密码
WUST_DORMITORY_DB_ROOT_PASSWORD=设置数据库根密码
WUST_DORMITORY_REDIS_PASSWORD=设置Redis密码
```

要求：

- 业务数据库账号不能使用 `root`；
- 业务数据库密码与数据库根密码不能相同；
- Redis 密码至少 8 个字符；
- `.env` 已被 Git 忽略，不得提交真实密码。

执行配置校验：

```bash
bash scripts/dev/validate-env.sh
```

## 3. 启动

推荐使用统一脚本：

```bash
bash scripts/dev/start-infra.sh up
```

脚本会依次：

1. 检查 Docker 与 Docker Compose 插件；
2. 校验根目录 `.env`；
3. 创建 `data/mysql/` 和 `data/redis/`；
4. 执行 `docker compose config`；
5. 启动 MySQL 和 Redis；
6. 等待两个服务健康；
7. 输出容器状态。

也可以直接使用 Compose：

```bash
docker compose --env-file .env -f docker/docker-compose.yml config
docker compose --env-file .env -f docker/docker-compose.yml up -d
```

## 4. 常用命令

```bash
# 查看状态
bash scripts/dev/start-infra.sh status

# 持续查看日志
bash scripts/dev/start-infra.sh logs

# 重启两个服务
bash scripts/dev/start-infra.sh restart

# 展开并检查最终 Compose 配置
bash scripts/dev/start-infra.sh config

# 停止并删除容器，保留数据
bash scripts/dev/start-infra.sh down
```

单独查看日志：

```bash
docker compose --env-file .env -f docker/docker-compose.yml logs -f mysql
docker compose --env-file .env -f docker/docker-compose.yml logs -f redis
```

## 5. 启动后端

Spring Boot 的 `application.yaml` 会从当前工作目录导入根目录 `.env`。因此应在项目根目录构建和启动：

```bash
mvn -f backend-java/pom.xml clean package -DskipTests
java -jar backend-java/starter/target/Service.jar
```

默认地址：

```text
后端：http://localhost:8080
健康检查：http://localhost:8080/actuator/health
MySQL：localhost:3306
Redis：localhost:6379
```

若从其他目录启动 JAR，需要显式指定配置文件位置：

```bash
java -jar backend-java/starter/target/Service.jar \
  --spring.config.import=optional:file:/项目绝对路径/.env[.properties]
```

## 6. 数据持久化

```text
data/mysql/    MySQL 数据文件
data/redis/    Redis 追加文件与持久化数据
```

执行 `down` 不会删除上述目录。容器重建后仍会读取原数据。

需要彻底重置开发数据时，先停止容器，再由开发者明确删除目录：

```bash
bash scripts/dev/start-infra.sh down
rm -rf data/mysql data/redis
bash scripts/dev/start-infra.sh up
```

该操作不可恢复，存在需要保留的数据时禁止执行。

## 7. 验证

运行静态和脚本测试：

```bash
python -m unittest scripts/dev/test_infra_config.py -v
```

真实容器验证：

```bash
bash scripts/dev/start-infra.sh up
docker compose --env-file .env -f docker/docker-compose.yml ps
```

预期 MySQL 和 Redis 的健康状态均为 `healthy`。

验证 MySQL：

```bash
docker exec -it wust-dormitory-mysql \
  mysql -u"$(grep '^WUST_DORMITORY_DB_USER=' .env | cut -d= -f2-)" \
  -p "$(grep '^WUST_DORMITORY_DB_NAME=' .env | cut -d= -f2-)"
```

验证 Redis：

```bash
docker exec -it wust-dormitory-redis sh -c \
  'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping'
```

Redis 应返回：

```text
PONG
```

## 8. 常见问题

### 端口被占用

修改 `.env` 中的宿主机端口：

```dotenv
WUST_DORMITORY_DB_PORT=13306
WUST_DORMITORY_REDIS_PORT=16379
```

Spring Boot 使用同一份 `.env`，不需要再修改 `application.yaml`。

### 修改密码后无法登录

MySQL 的初始化账号和密码只在空数据目录首次启动时生效。开发环境需要使用新密码重新初始化时，应先确认没有需要保留的数据，再删除 `data/mysql/`。

Redis 密码每次启动都会从 `.env` 生成运行配置，修改后重启 Redis 即可：

```bash
bash scripts/dev/start-infra.sh restart
```

### WSL 中找不到 Docker

使用 Docker Desktop 时，需要开启 WSL 2 后端，并在 Docker Desktop 的 WSL 集成设置中启用当前 Ubuntu 发行版。以下命令必须能够正常返回：

```bash
docker info
docker compose version
```
