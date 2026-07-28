package io.j0.react.tools;

import io.j0.react.AgentConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves a local JAR path from Maven coordinates declared in agent.yaml.
 */
public final class MavenArtifactJarResolver {

    public Path resolve(AgentConfig.ToolLibraryConfig cfg, Path agentBaseDir) throws IOException {
        AgentConfig.ToolLibraryConfig validated = cfg.validated();

        if (validated.hasDirectJarPath()) {
            return resolveDirectJarPath(validated.jarPath(), agentBaseDir);
        }

        Path repositoryRoot = resolveRepositoryRoot(validated.repositoryPath());
        String groupPath = validated.groupId().replace('.', '/');
        String extension = validated.extensionOrDefault();

        StringBuilder fileName = new StringBuilder()
                .append(validated.artifactId())
                .append('-')
                .append(validated.version());

        if (validated.classifier() != null && !validated.classifier().isBlank()) {
            fileName.append('-').append(validated.classifier());
        }
        fileName.append('.').append(extension);

        Path jarPath = repositoryRoot
                .resolve(groupPath)
                .resolve(validated.artifactId())
                .resolve(validated.version())
                .resolve(fileName.toString())
                .normalize();

        if (!Files.exists(jarPath) || !Files.isRegularFile(jarPath)) {
            throw new IOException("Tool library JAR not found: " + jarPath);
        }

        return jarPath;
    }

    private Path resolveDirectJarPath(String rawJarPath, Path agentBaseDir) throws IOException {
        Path raw = Paths.get(rawJarPath);
        Path candidate;
        if (raw.isAbsolute()) {
            candidate = raw.normalize();
        } else if (agentBaseDir != null) {
            candidate = agentBaseDir.resolve(raw).normalize();
        } else {
            candidate = raw.toAbsolutePath().normalize();
        }

        if (!Files.exists(candidate) || !Files.isRegularFile(candidate)) {
            throw new IOException("Tool library JAR not found (jar_path): " + candidate);
        }
        return candidate;
    }

    private Path resolveRepositoryRoot(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Paths.get(configuredPath).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.home"), ".m2", "repository")
                .toAbsolutePath()
                .normalize();
    }
}
