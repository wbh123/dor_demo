# 框架适配指南

## 1. 适配目标

模板保持以下多模块结构：

```text
./
├── client/
├── model/
├── server/
├── starter/
├── scripts/
└── docs/
```

仓库根目录就是 Maven 聚合工程根目录。所有配置、移动和扫描路径都必须使用相对路径，不允许使用绝对路径或通过 `..` 跳出工程根目录。

## 2. 配置文件

默认配置：

```text
scripts/rename-framework.json
```

核心结构：

```json
{
  "schema_version": 1,
  "project_root": ".",
  "text": {
    "roots": ["pom.xml", "client", "model", "server", "starter", "README.md", "docs"],
    "exclude_dirs": [".git", "target", ".idea", ".vscode", "__pycache__"],
    "exclude_files": ["scripts/rename-framework.py"],
    "include_extensions": [".xml", ".java", ".yaml", ".yml", ".properties", ".md", ".json"],
    "include_names": ["Dockerfile"]
  },
  "replacements": [
    {"from": "旧内容", "to": "新内容"}
  ],
  "moves": [
    {"source": "相对源路径", "target": "相对目标路径", "required": true}
  ],
  "build": {
    "command": "mvn clean install -DskipTests",
    "jar": "starter/target/Service.jar"
  }
}
```

### 配置职责

- `text.roots`：需要扫描的文件或目录；
- `exclude_dirs`：跳过的目录名；
- `exclude_files`：跳过的仓库相对文件；
- `include_extensions`：允许进行 UTF-8 文本替换的扩展名；
- `replacements`：按数组顺序执行的文本替换；
- `moves`：文本替换后执行的文件或目录移动；
- `required=false`：源路径不存在时允许跳过；
- `build`：执行完成后显示的验证命令和预期 JAR。

所有项目名、Maven 坐标、包名、数据库名、Docker 名称和启动类名称都应写入 `replacements` 与 `moves`，不要重新硬编码进 Python、Shell、批处理或 PowerShell 脚本。

## 3. 一键执行

Linux、macOS、WSL：

```bash
sh scripts/rename-framework.sh
```

Windows 批处理：

```bat
scripts\rename-framework.bat
```

PowerShell：

```powershell
.\scripts\rename-framework.ps1
```

直接运行 Python：

```bash
python scripts/rename-framework.py
```

指定其他配置文件：

```bash
python scripts/rename-framework.py --config scripts/another-config.json
```

预览：

```bash
python scripts/rename-framework.py --dry-run
```

## 4. 路径安全

脚本以自身所在的 `scripts/` 目录推导仓库根目录。配置中的所有路径必须：

1. 是相对于仓库根目录或 `project_root` 的相对路径；
2. 不以 `/`、盘符或网络路径开头；
3. 不包含 `..`；
4. 解析后仍位于工程根目录内部。

违反上述规则时脚本立即停止，不执行修改。

## 5. 替换顺序

更具体的替换放在前面，通用包名替换放在后面。例如：

```json
[
  {
    "from": "<groupId>com.wust.dormitory</groupId>",
    "to": "<groupId>com.example</groupId>"
  },
  {
    "from": "<artifactId>WustDormitorySelect</artifactId>",
    "to": "<artifactId>example-service</artifactId>"
  },
  {
    "from": "com.wust.dormitory",
    "to": "com.example.service"
  }
]
```

这样可以避免 Maven `groupId` 被通用 Java 包名替换为错误值。

## 6. Java 包目录移动

模板中的手写 Java 与 Mapper XML 通过 `moves` 移动：

```json
[
  {
    "source": "server/src/main/java/com/wust/dormitory",
    "target": "server/src/main/java/com/example/service"
  },
  {
    "source": "starter/src/main/java/com/wust/dormitory/WustDormitorySelectApplication.java",
    "target": "starter/src/main/java/com/example/service/ExampleServiceApplication.java"
  },
  {
    "source": "server/src/main/resources/com/wust/dormitory",
    "target": "server/src/main/resources/com/example/service",
    "required": false
  }
]
```

生成代码位于 `target/generated-sources`，不会被脚本处理。适配后重新运行 Maven 构建即可生成新的 OpenAPI API、DTO 和 Feign 客户端。

## 7. 可执行 JAR

`starter` 是最终应用入口。`starter/pom.xml` 使用：

```xml
<finalName>Service</finalName>
```

并绑定 `spring-boot-maven-plugin:repackage`。因此：

```bash
mvn clean package -DskipTests
mvn clean install -DskipTests
```

都会生成：

```text
starter/target/Service.jar
```

运行：

```bash
java -jar starter/target/Service.jar
```

普通 `mvn compile` 只编译，不负责打包。

## 8. 验证清单

```bash
python -m unittest scripts/test_rename_framework.py -v
python scripts/rename-framework.py --dry-run
mvn clean install -DskipTests
java -jar starter/target/Service.jar
```

至少确认：

- 脚本单元测试通过；
- 配置中没有绝对路径和越界路径；
- 代码中不存在旧 Maven 坐标和旧 Java 包名；
- `client` 仍不包含手写 Java 源码；
- OpenAPI 代码可以重新生成；
- `starter/target/Service.jar` 存在；
- JAR 包可以由 `java -jar` 启动。
