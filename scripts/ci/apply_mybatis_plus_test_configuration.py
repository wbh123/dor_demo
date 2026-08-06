#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FILES = [
    ROOT / "backend-java/server/src/test/java/com/wust/dormitory/config/MybatisConfigurationTest.java",
    ROOT / "backend-java/server/src/test/java/com/wust/dormitory/mapper/MybatisMySqlIntegrationTest.java",
    ROOT / "backend-java/server/src/test/java/com/wust/dormitory/mapper/StudentAdminMapperMySqlIntegrationTest.java",
    ROOT / "backend-java/server/src/test/java/com/wust/dormitory/mapper/RecentAuditLogMapperMySqlIntegrationTest.java",
    ROOT / "backend-java/server/src/test/java/com/wust/dormitory/mapper/AdminDashboardMapperMySqlIntegrationTest.java",
    ROOT / "backend-java/server/src/test/java/com/wust/dormitory/mapper/BatchCatalogMapperMySqlIntegrationTest.java",
]

for path in FILES:
    text = path.read_text(encoding="utf-8")
    if "import com.baomidou.mybatisplus.core.MybatisConfiguration;" not in text:
        package_end = text.index("\n\n", text.index("package "))
        text = (
            text[:package_end]
            + "\n\nimport com.baomidou.mybatisplus.core.MybatisConfiguration;"
            + text[package_end:]
        )
    text = text.replace(
        "org.apache.ibatis.session.Configuration configuration =\n                new org.apache.ibatis.session.Configuration();",
        "MybatisConfiguration configuration = new MybatisConfiguration();",
    )
    text = text.replace(
        "org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();",
        "MybatisConfiguration configuration = new MybatisConfiguration();",
    )
    text = text.replace(
        "new MybatisConfig()\n                .mybatisConfigurationCustomizer(new ObjectMapper())\n                .customize(new org.apache.ibatis.session.Configuration());",
        "MybatisConfiguration configuration = new MybatisConfiguration();\n        new MybatisConfig()\n                .mybatisConfigurationCustomizer(new ObjectMapper())\n                .customize(configuration);",
    )
    if "new org.apache.ibatis.session.Configuration()" in text:
        raise RuntimeError(f"仍存在原生 Configuration：{path}")
    path.write_text(text, encoding="utf-8")

print("MyBatis-Plus test configurations migrated")
