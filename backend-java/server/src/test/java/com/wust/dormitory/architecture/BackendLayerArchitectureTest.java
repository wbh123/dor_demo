package com.wust.dormitory.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class BackendLayerArchitectureTest {
    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.wust.dormitory");

    @Test
    void controllersMustNotDependOnPersistenceInfrastructure() {
        noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.jdbc..",
                        "org.springframework.data.redis..",
                        "..mapper..")
                .because("Controller 只能调用应用 Service，不得直接访问 JDBC、Redis 或 Mapper")
                .check(productionClasses);
    }
}
