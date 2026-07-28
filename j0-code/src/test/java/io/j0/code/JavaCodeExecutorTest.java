package io.j0.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaCodeExecutorTest {

    @TempDir
    Path tempDir;

    @Test
    void compilesAndRunsGeneratedClass() throws Exception {
        String source = """
                public class SampleGenerated {
                    public static void main(String[] args) {
                        System.out.println("hello " + args[0]);
                    }
                }
                """;

        JavaCodeExecutor.ExecutionResult result = new JavaCodeExecutor().compileAndRun(
                source,
                tempDir,
                Duration.ofSeconds(5),
                List.of("j0")
        );

        assertEquals(0, result.exitCode());
        assertEquals("SampleGenerated", result.className());
        assertTrue(Files.exists(result.sourceFile()));
        assertTrue(Files.exists(result.classesDirectory().resolve("SampleGenerated.class")));
        assertEquals("hello j0\r\n", result.output());

        System.out.println("Output: " + result.output());
    }
}