package io.j0.code.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.j0.code.JavaCodeAgent;
import io.j0.code.JavaCodeExecutor;
import io.j0.code.ProviderType;
import picocli.CommandLine;

class J0CodeCommandTest {

    private Path generatedDir;

    @BeforeEach
    void setUp() throws Exception {
        // Create the generated directory if it doesn't exist
        // Use absolute path from project root
        generatedDir = Paths.get("generated/j0-code");
        Files.createDirectories(generatedDir);
    }

    @Test
    public void testJ0CodeCommand() throws Exception {

        J0CodeCommand command = new J0CodeCommand();
        command.userPrompt = """
            comprimi tutti i files della cartella c:/temp in un file zip chiamato c:/temp/archivio.zip. comprimi solo i files con dimensione maggiore a 10k
                """;
        command.provider = ProviderType.llamacpp;
        command.preferredClassName = "TestTwo";
        command.modelName = "qwen2.5-3b-instruct-q8_0.gguf";
        command.outputDirectory = generatedDir;
        command.timeoutSeconds = 120;
        
        int exitCode = command.call();
        
        /*
        assertEquals(0, exitCode, "Program should execute successfully");
        Path generatedFile = generatedDir.resolve("TestTwo.java");
        assertTrue(Files.exists(generatedFile), "Generated source file should exist at " + generatedFile);
        */

    }


}
