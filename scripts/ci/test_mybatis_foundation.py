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
server_pom = ROOT / "backend-java/server/pom.xml"

require(application.exists(), "缺少 Spring Boot 启动类")
require(mybatis_config.exists(), "缺少正式 MybatisConfig")
require(application_yaml.exists(), "缺少 application.yaml")
require(not old_mapper.exists(), "空 TestMapper.xml 必须删除")
require(configuration_test.exists(), "缺少 MybatisConfigurationTest")

application_text = application.read_text(encoding="utf-8")
config_text = mybatis_config.read_text(encoding="utf-8")
yaml_text = application_yaml.read_text(encoding="utf-8")

require("MapperScan" not in application_text, "Mapper 扫描必须集中到 MybatisConfig")
require("@MapperScan" in config_text, "MybatisConfig 必须声明 MapperScan")
require('basePackages = "com.wust.dormitory"' in config_text, "MapperScan 必须覆盖按业务域分包")
require("annotationClass = Mapper.class" in config_text, "MapperScan 必须只注册 @Mapper 接口")
require("ConfigurationCustomizer" in config_text, "MybatisConfig 必须集中注册类型处理器")

require("mapper-locations: classpath*:mapper/**/*.xml" in yaml_text, "MyBatis XML 必须统一到 mapper 目录")
require("classpath*:com/wust/dormitory/mappers" not in yaml_text, "旧 MyBatis XML 路径不得保留")
require("map-underscore-to-camel-case: true" in yaml_text, "必须启用下划线到驼峰映射")

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

print("MyBatis foundation contract passed")
