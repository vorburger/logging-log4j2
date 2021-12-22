package org.apache.logging.log4j;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

public class ArchitectureTest {

    @Test public void rule1() {
        JavaClasses importedClasses = new ClassFileImporter().withImportOption(new DoNotIncludeTests())
                .importPackages("org.apache.logging.log4j");

        ArchRule rule = classes().that().resideInAPackage("org.apache.logging.log4j").should().onlyAccessClassesThat()
                .resideInAnyPackage("java.lang..", "java.util..", "org.apache.logging.log4j");

        rule.check(importedClasses);
    }
}
