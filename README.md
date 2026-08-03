# 高校宿舍智能选择平台

本仓库是经过脱敏处理的公开开发仓库，用于功能开发和 GitHub Actions 验证。

## 仓库边界

允许保存：

- 前后端业务源码；
- OpenAPI 契约和生成配置；
- 单元测试、静态测试与持续集成配置；
- 不包含真实信息的环境变量示例。

禁止保存：

- 数据库建表、迁移、初始化和测试数据脚本；
- 数据字典、实体关系图及内部设计文档；
- Docker Compose、生产部署、服务器、反向代理和运维脚本；
- 真实学校、校区、学生、账号、地址、密钥和生产配置。

Java 包名、`WUST_DORMITORY_*` 环境变量前缀和 `wust_dormitory` 数据库名属于技术性历史标识，可以保留，不代表真实机构信息。

## 可替换展示信息

机构和校区展示名称通过 `.env.example` 中的变量集中维护。默认使用虚构值：

```text
示例大学
示例校区
```

## 持续集成

每次推送和拉取请求均执行：

```bash
python scripts/ci/validate_public_repository.py .
mvn -f backend-java/pom.xml --batch-mode --no-transfer-progress clean verify
npm ci --prefix frontend --no-audit --no-fund
npm run --prefix frontend build
```

公开仓库测试通过后，功能再迁移到私有仓库，由私有仓库补充数据库迁移、部署配置和完整文档。
