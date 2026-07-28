package io.j0.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JavaSourceUtilsTest {

    @Test
    void extractsCodeFromMarkdownFence() {
        String source = JavaSourceUtils.extractJavaSource("""
                ```java
                public class Demo {
                    public static void main(String[] args) {
                    }
                }
                ```
                """);

        assertEquals("Demo", JavaSourceUtils.detectPublicClassName(source));
    }

    @Test
    void rejectsPackageDeclarations() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> JavaSourceUtils.validateRunnableSource("package demo; public class Demo { public static void main(String[] args) {} }"));

        assertEquals("Generated source must not declare a package.", error.getMessage());
    }
}