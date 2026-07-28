package io.j0.react.tools;

import io.j0.react.AgentConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MavenArtifactJarResolverTest {

    @Test
    void resolvesJarFromConfiguredRepositoryPath() throws Exception {
        Path repoRoot = Files.createTempDirectory("m2-test");
        try {
            Path jar = repoRoot
                    .resolve("io")
                    .resolve("j0")
                    .resolve("j0-tools-internal")
                    .resolve("0.1.0-SNAPSHOT")
                    .resolve("j0-tools-internal-0.1.0-SNAPSHOT.jar");
            Files.createDirectories(jar.getParent());
            Files.writeString(jar, "dummy");

            AgentConfig.ToolLibraryConfig cfg = new AgentConfig.ToolLibraryConfig(
                    "io.j0",
                    "j0-tools-internal",
                    "0.1.0-SNAPSHOT",
                    "io.j0.react.tools.MinimalFileToolCallHandler",
                    null,
                    null,
                    null,
                    repoRoot.toString()
            );

                Path resolved = new MavenArtifactJarResolver().resolve(cfg, null);
            assertEquals(jar.toAbsolutePath().normalize(), resolved);
        } finally {
            try (var stream = Files.walk(repoRoot)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    @Test
    void throwsWhenJarDoesNotExist() throws Exception {
        Path repoRoot = Files.createTempDirectory("m2-test-missing");
        try {
            AgentConfig.ToolLibraryConfig cfg = new AgentConfig.ToolLibraryConfig(
                    "io.j0",
                    "j0-tools-internal",
                    "0.1.0-SNAPSHOT",
                    "io.j0.react.tools.MinimalFileToolCallHandler",
                    null,
                    null,
                    null,
                    repoRoot.toString()
            );

            assertThrows(IOException.class, () -> new MavenArtifactJarResolver().resolve(cfg, null));
        } finally {
            Files.deleteIfExists(repoRoot);
        }
    }

    @Test
    void resolvesJarFromAgentRelativePath() throws Exception {
        Path agentBase = Files.createTempDirectory("agent-base");
        try {
            Path libsDir = agentBase.resolve("libs");
            Files.createDirectories(libsDir);
            Path jar = libsDir.resolve("custom-tools.jar");
            Files.writeString(jar, "dummy");

            AgentConfig.ToolLibraryConfig cfg = new AgentConfig.ToolLibraryConfig(
                    null,
                    null,
                    null,
                    "io.j0.react.tools.MinimalFileToolCallHandler",
                    "libs/custom-tools.jar",
                    null,
                    null,
                    null
            );

            Path resolved = new MavenArtifactJarResolver().resolve(cfg, agentBase);
            assertEquals(jar.toAbsolutePath().normalize(), resolved);
        } finally {
            try (var stream = Files.walk(agentBase)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }
}
