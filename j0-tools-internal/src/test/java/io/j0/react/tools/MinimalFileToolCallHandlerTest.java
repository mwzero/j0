package io.j0.react.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.j0.react.execution.ExecutionStatus;
import io.j0.react.execution.RunResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MinimalFileToolCallHandler Tests")
class FileToolCallHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Path tempDir;
    private Path memoryFile;
    private FileToolCallHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("j0-tools-test");
        memoryFile = tempDir.resolve("memory.md");
        Files.writeString(memoryFile, "");
        handler = new FileToolCallHandler(memoryFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var stream = Files.walk(tempDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    @Test
    void fileWriteAndReadRoundTrip() throws IOException {
        Path target = tempDir.resolve("hello.txt");
        ObjectNode writeArgs = MAPPER.createObjectNode()
                .put("filename", target.toString())
                .put("content", "ciao");

        RunResult writeResult = handler.handle("file_write", writeArgs);
        assertEquals(ExecutionStatus.SUCCESS, writeResult.getStatus());

        RunResult readResult = handler.handle("file_read", MAPPER.createObjectNode().put("filename", target.toString()));
        assertEquals(ExecutionStatus.SUCCESS, readResult.getStatus());
        assertEquals("ciao", readResult.getOutput().get("content").asText());
    }

    @Test
    void fileExistsReturnsFalseForMissingFile() {
        Path missing = tempDir.resolve("missing.txt");
        RunResult result = handler.handle("file_exists", MAPPER.createObjectNode().put("filename", missing.toString()));

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertFalse(result.getOutput().get("exists").asBoolean());
    }

    @Test
    void dirCreateAndDeleteWork() {
        Path folder = tempDir.resolve("nested");

        RunResult create = handler.handle("dir_create", MAPPER.createObjectNode().put("foldername", folder.toString()));
        assertEquals(ExecutionStatus.SUCCESS, create.getStatus());
        assertTrue(Files.isDirectory(folder));

        RunResult delete = handler.handle("dir_delete", MAPPER.createObjectNode().put("foldername", folder.toString()));
        assertEquals(ExecutionStatus.SUCCESS, delete.getStatus());
        assertFalse(Files.exists(folder));
    }
}
