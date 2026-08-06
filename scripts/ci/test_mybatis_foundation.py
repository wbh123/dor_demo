#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


application = ROOT / "backend-java/starter/src/main/java/com/wust/dormitory/WustDormitorySelectApplication.java"
mybatis_config = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/config/MybatisConfig.java"
application_yaml = ROOT / "backend-java/starter/src/main/resources/application.yaml"
old_mapper = ROOT / "backend-java/server/src/main/resources/com/wust/dormitory/mappers/TestMapper.xml"
handler_dir = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/common/persistence/typehandler"
configuration_test = ROOT / "backend-java/server/src/test/java/com/wust/dormitory/config/MybatisConfigurationTest.java"
runtime_wiring_test = ROOT / "backend-java/server/src/test/java/com/wust/dormitory/config/MybatisRuntimeWiringTest.java"
server_pom = ROOT / "backend-java/server/pom.xml"
bom_pom = ROOT / "backend-java/build-support/general-bom3/pom.xml"

require(application.exists(), "缺少 Spring Boot 启动类")
require(mybatis_config.exists(), "缺少正式 MybatisConfig")
require(application_yaml.exists(), "缺少 application.yaml")
require(not old_mapper.exists(), "空 TestMapper.xml 必须删除")
require(configuration_test.exists(), "缺少 MybatisConfigurationTest")
require(runtime_wiring_test.exists(), "缺少 MyBatis-Plus 运行时装配测试")

application_text = application.read_text(encoding="utf-8")
config_text = mybatis_config.read_text(encoding="utf-8")
yaml_text = application_yaml.read_text(encoding="utf-8")
runtime_test_text = runtime_wiring_test.read_text(encoding="utf-8")
server_pom_text = server_pom.read_text(encoding="utf-8")
bom_pom_text = bom_pom.read_text(encoding="utf-8")

require("MapperScan" not in application_text, "Mapper 扫描必须集中到 MybatisConfig")
require("@MapperScan" in config_text, "MybatisConfig 必须声明 MapperScan")
require('basePackages = "com.wust.dormitory"' in config_text, "MapperScan 必须覆盖按业务域分包")
require("annotationClass = Mapper.class" in config_text, "MapperScan 必须只注册 @Mapper 接口")
require(
    "com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer" in config_text,
    "MybatisConfig 必须使用 MyBatis-Plus ConfigurationCustomizer",
)
require(
    "org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer" not in config_text,
    "不得继续使用纯 MyBatis Starter 的 ConfigurationCustomizer",
)
require(
    "registry.register(JsonNode.class" in config_text,
    "JsonNode JSON 类型处理器必须保留",
)
require(
    "registry.register(List.class" not in config_text,
    "List 不得全局注册为 JSON 类型，否则普通 List 结果会被误解析",
)
require(
    "registry.register(Map.class" not in config_text,
    "Map 不得全局注册为 JSON 类型，否则 resultType=map 会从首列按 JSON 解析",
)

require("mybatis-plus:" in yaml_text, "必须使用 mybatis-plus 配置前缀")
require("mapper-locations: classpath*:mapper/**/*.xml" in yaml_text, "MyBatis XML 必须统一到 mapper 目录")
require("classpath*:com/wust/dormitory/mappers" not in yaml_text, "旧 MyBatis XML 路径不得保留")
require("map-underscore-to-camel-case: true" in yaml_text, "必须启用下划线到驼峰映射")

require(
    "<artifactId>mybatis-plus-spring-boot4-starter</artifactId>" in server_pom_text,
    "server 必须使用 MyBatis-Plus Spring Boot 4 Starter",
)
require(
    "<artifactId>mybatis-spring-boot-starter</artifactId>" not in server_pom_text,
    "引入 MyBatis-Plus 后不得同时保留纯 MyBatis Starter",
)
require("<mybatis-plus.version>3.5.17</mybatis-plus.version>" in bom_pom_text, "MyBatis-Plus 版本必须固定")
require(
    "MybatisPlusAutoConfiguration.class" in runtime_test_text,
    "运行时测试必须加载 MyBatis-Plus 自动配置",
)
require(
    "MybatisConfiguration.class" in runtime_test_text,
    "运行时测试必须确认使用 MyBatis-Plus Configuration",
)

handler_names = {
    "AbstractJacksonJsonTypeHandler.java",
    "JsonNodeTypeHandler.java",
    "StringListJsonTypeHandler.java",
    "StringMapJsonTypeHandler.java",
}
require(handler_dir.exists(), "缺少 JSON 类型处理器目录")
existing_handlers = {path.name for path in handler_dir.glob("*.java")}
require(handler_names.issubset(existing_handlers), "JSON 类型处理器不完整")
for name in handler_names:
    text = (handler_dir / name).read_text(encoding="utf-8")
    require("JdbcTemplate" not in text and "RedisTemplate" not in text, f"{name} 不得依赖基础设施客户端")

namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(server_pom).getroot()
plugins = root.findall(".//m:plugin", namespace)
generator_plugins = [
    plugin for plugin in plugins
    if plugin.findtext("m:artifactId", default="", namespaces=namespace) == "mybatis-generator-maven-plugin"
]
require(len(generator_plugins) == 1, "MyBatis Generator 必须保留为唯一显式插件")
require(generator_plugins[0].find("m:executions", namespace) is None, "MyBatis Generator 不得绑定 Maven 生命周期自动覆盖文件")

print("MyBatis-Plus foundation contract passed")
