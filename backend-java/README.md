# 可复用 Java 服务框架

**父工程：** https://github.com/wbh123/ServiceModuleBom.git

本项目是一个 Spring 多模块框架模板，基于 Java 21、Spring Boot 4.0.0、Spring Cloud 2025.1.0、Spring AI 2.0.0 BOM、OpenAPI Generator、MyBatis 和 MySQL。

## 模块结构

```text
./
├── client/   OpenAPI 自动生成的 Spring Cloud OpenFeign 客户端
├── model/    OpenAPI 契约与生成的服务端 API/DTO
├── server/   Controller、Service、MyBatis 和业务实现
├── starter/  Spring Boot 启动入口、运行配置和可执行 JAR
├── scripts/  配置驱动的工程适配脚本
└── docs/     适配说明
```

仓库根目录就是 Maven 聚合工程根目录，全部配置和脚本均使用仓库内相对路径。

## 配置驱动的一键适配

先编辑：

```text
scripts/rename-framework.json
```

再执行任一入口：

```bash
python scripts/rename-framework.py
sh scripts/rename-framework.sh
scripts\rename-framework.bat
```

PowerShell：

```powershell
.\scripts\rename-framework.ps1
```

预览而不写入：

```bash
python scripts/rename-framework.py --dry-run
```

脚本规则：

- 所有修改值都在 JSON 配置中维护；
- 配置中的路径只能使用仓库内相对路径；
- 拒绝绝对路径和包含 `..` 的越界路径；
- 自动完成文本替换、Java 包目录移动和启动类文件重命名；
- 不修改 `target`、`.git`、IDE 配置和脚本自身；
- 支持重复执行，已完成的目录移动会被识别并跳过。

## 构建与运行

先安装父 BOM：

```bash
mvn -f ../ServiceModuleBom/pom.xml clean install -DskipTests
```

编译：

```bash
mvn clean compile -DskipTests
```

生成可执行 JAR：

```bash
mvn clean package -DskipTests
```

或安装到本地 Maven 仓库：

```bash
mvn clean install -DskipTests
```

可执行产物固定为：

```text
starter/target/Service.jar
```

运行：

```bash
java -jar starter/target/Service.jar
```

## 脚本测试

```bash
python -m unittest scripts/test_rename_framework.py -v
```

详细说明见 [docs/adaptation-guide.md](docs/adaptation-guide.md)。
