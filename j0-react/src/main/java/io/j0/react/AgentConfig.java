package io.j0.react;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Typed representation of {@code agent.yaml} for a ReAct agent.
 *
 * <pre>
 * agent:
 *   name: hubber.react
 *   name: qwen2.5-3b-instruct-q8_0.gguf
 *   max_iterations: 5
 * prompts:
 *   soul:        system-prompt.md
 *   tools:       tools.md
 *   memory:      memory.md
 *   observation: observation-prompt.md
 *   reflection:  reflection-prompt.md
 *   rejection:   rejection-prompt.md
 * tool_rag:
 *   enabled: true
 *   top_k:   5
 *   discovery_prompt: discovery-prompt.md
 * </pre>
 *
 * <p>The {@code resourceBase} component is not deserialized from YAML — it is
 * computed from the actual classpath URL of the loaded file and used by
 * {@link #resolvePrompt(String)} to avoid any hardcoded paths.</p>
 */
public record AgentConfig(
        AgentMeta agent,
        PromptsConfig prompts,
        ToolRagConfig toolRag,
        ToolLibraryConfig toolLibrary,
        String resourceBase) {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    /**
     * Jackson creator — used during YAML deserialization only.
     * {@code resourceBase} is excluded here and injected afterwards by
     * {@link #fromResource(String)}.
     */
    @JsonCreator
    public static AgentConfig fromYaml(
            @JsonProperty("agent")    AgentMeta agent,
            @JsonProperty("prompts")  PromptsConfig prompts,
            @JsonProperty("tool_rag") ToolRagConfig toolRag,
            @JsonProperty("tool_library") ToolLibraryConfig toolLibrary) {
        return new AgentConfig(agent, prompts, toolRag, toolLibrary, "");
    }

    /**
     * Loads and parses an {@code agent.yaml} from the given classpath resource,
     * deriving the base directory from the resource URL so that
     * {@link #resolvePrompt(String)} needs no hardcoded paths.
     *
     * @param resourcePath classpath resource path (e.g. {@code "/org/hubbers/react/agent.yaml"})
     */
    public static AgentConfig fromResource(String resourcePath) throws IOException {
        URL url = AgentConfig.class.getResource(resourcePath);
        if (url == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        String base = resourcePath.substring(0, resourcePath.lastIndexOf('/') + 1);
        try (InputStream is = url.openStream()) {
            AgentConfig tmp = YAML.readValue(is, AgentConfig.class);
            return new AgentConfig(tmp.agent(), tmp.prompts(), tmp.toolRag(), tmp.toolLibrary(), base);
        }
    }

    /**
     * Loads and parses an {@code agent.yaml} from the given file system path,
     * deriving the base directory from the file location so that
     * {@link #resolvePrompt(String)} can resolve relative paths.
     *
     * @param filePath filesystem path to the agent.yaml file (e.g. {@code "config/agent.yaml"})
     */
    public static AgentConfig fromFile(String filePath) throws IOException {
        Path path = Path.of(filePath).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("Not a regular file: " + path);
        }

        Path baseDir = path.getParent();
        // Use "file://" prefix to clearly mark this as a filesystem path
        String base = "file://" + baseDir.toString().replace("\\", "/");
        if (!base.endsWith("/")) {
            base = base + "/";
        }

        try (InputStream is = Files.newInputStream(path)) {
            AgentConfig tmp = YAML.readValue(is, AgentConfig.class);
            return new AgentConfig(tmp.agent(), tmp.prompts(), tmp.toolRag(), tmp.toolLibrary(), base);
        }
    }

    /**
     * Resolves a prompt filename (relative to the same directory as {@code agent.yaml})
     * to an absolute {@link Path}.
     *
     * <p>Supports both classpath resources and filesystem paths:
     * <ul>
     *   <li>If loaded from resource via {@link #fromResource}, resolves relative to the classpath directory</li>
     *   <li>If loaded from file via {@link #fromFile}, resolves relative to the filesystem directory</li>
     * </ul>
     *
     * <p>For read-only prompt files, the returned path works in both cases. For mutable files
     * (e.g. {@code memory.md}) use {@link #resolveWritable(String)} instead.
     */
    public Path resolvePrompt(String filename) throws IOException {
        // Check if resourceBase is a filesystem path (marked with file:// prefix)
        if (resourceBase.startsWith("file://")) {
            // Filesystem path - strip the file:// prefix
            String basePath = resourceBase.substring("file://".length());
            Path promptPath = Path.of(basePath).resolve(filename).toAbsolutePath();
            if (!Files.exists(promptPath)) {
                throw new IOException("Prompt file not found: " + promptPath);
            }
            return promptPath;
        } else {
            // Classpath resource
            URL url = AgentConfig.class.getResource(resourceBase + filename);
            if (url == null) {
                throw new IOException("Prompt resource not found: " + resourceBase + filename);
            }
            try {
                return Path.of(url.toURI()).toAbsolutePath();
            } catch (URISyntaxException e) {
                throw new IOException("Invalid URI for prompt: " + filename, e);
            }
        }
    }

    /**
     * Resolves a mutable file (e.g. {@code memory.md}) to its <em>source</em> path
     * in {@code src/test/resources}, so writes survive a Maven clean and are
     * immediately visible in the IDE without recompiling.
     *
     * <p>Remaps {@code .../target/test-classes/...} to
     * {@code .../src/test/resources/...}.  If the source tree cannot be located
     * (e.g. running from a JAR), falls back to {@link #resolvePrompt(String)}.
     */
    public Path resolveWritable(String filename) throws IOException {
        Path compiled = resolvePrompt(filename);
        Path remapped = remapToSource(compiled);
        return remapped != null ? remapped : compiled;
    }

    /**
     * Resolves the directory containing the loaded {@code agent.yaml}.
     *
     * <p>When loaded from resource: attempts to remap {@code target/classes} or
     * {@code target/test-classes} back to {@code src/main/resources} or {@code src/test/resources}.
     *
     * <p>When loaded from file: returns the directory directly.
     */
    public Path resolveAgentBaseDirectory() throws IOException {
        // Check if resourceBase is a filesystem path (marked with file:// prefix)
        if (resourceBase.startsWith("file://")) {
            // Filesystem path - strip the file:// prefix
            String basePath = resourceBase.substring("file://".length());
            return Path.of(basePath).toAbsolutePath();
        }

        // Classpath resource
        URL url = AgentConfig.class.getResource(resourceBase);
        if (url == null) {
            throw new IOException("Agent base resource not found: " + resourceBase);
        }
        try {
            Path compiledBase = Path.of(url.toURI()).toAbsolutePath();
            Path remapped = remapToSource(compiledBase);
            return remapped != null ? remapped : compiledBase;
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI for agent base: " + resourceBase, e);
        }
    }

    /**
     * Attempts to remap a path inside {@code target/test-classes} to the
     * corresponding path inside {@code src/test/resources}.
     *
     * @return the source path if it exists, otherwise {@code null}
     */
    private static Path remapToSource(Path compiled) {
        // Walk up looking for a segment named "target"
        Path cursor = compiled;
        while (cursor != null) {
            if ("target".equals(cursor.getFileName() != null ? cursor.getFileName().toString() : "")) {
                Path moduleRoot = cursor.getParent();
                if (moduleRoot != null) {
                    // Strip the "target/classes" or "target/test-classes" prefix and re-root under src/*/resources
                    Path relative = cursor.relativize(compiled); // e.g. classes/io/... or test-classes/io/...
                    int segments = relative.getNameCount();
                    if (segments > 0) {
                        String first = relative.getName(0).toString();
                        String sourceScope;
                        if ("classes".equals(first)) {
                            sourceScope = "main";
                        } else if ("test-classes".equals(first)) {
                            sourceScope = "test";
                        } else {
                            break;
                        }

                        Path source = moduleRoot.resolve("src").resolve(sourceScope).resolve("resources");
                        if (segments > 1) {
                            source = source.resolve(relative.subpath(1, segments));
                        }
                        if (source.toFile().exists()) {
                            return source.toAbsolutePath();
                        }
                    }
                }
                break;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Nested config records
    // -------------------------------------------------------------------------

    public record AgentMeta(String name, String version, String description, String model, int max_iterations) {}

    public record PromptsConfig(
            String soul,
            String tools,
            String memory,
            String observation,
            String reflection,
            String rejection) {}

    public record ToolRagConfig(
            boolean enabled,
            @JsonProperty("top_k") int topK,
            @JsonProperty("discovery_prompt") String discoveryPrompt) {

        /** Returns a disabled default instance used when the section is absent from the YAML. */
        public static ToolRagConfig disabled() {
            return new ToolRagConfig(false, 5, null);
        }
    }

    /** Returns the tool_rag config, falling back to a disabled instance if absent from the YAML. */
    public ToolRagConfig toolRagOrDefault() {
        return toolRag != null ? toolRag : ToolRagConfig.disabled();
    }

    /**
     * Returns a validated tool library configuration when present, otherwise {@code null}.
     */
    public ToolLibraryConfig optionalToolLibrary() throws IOException {
        if (toolLibrary == null) {
            return null;
        }
        return toolLibrary.validated();
    }

    public record ToolLibraryConfig(
            @JsonProperty("group_id") String groupId,
            @JsonProperty("artifact_id") String artifactId,
            String version,
            @JsonProperty("handler_class") String handlerClass,
            @JsonProperty("jar_path") String jarPath,
            String classifier,
            String extension,
            @JsonProperty("repository_path") String repositoryPath) {

        public ToolLibraryConfig validated() throws IOException {
            if (isBlank(handlerClass)) {
                throw new IOException("tool_library.handler_class is required");
            }

            if (!hasDirectJarPath() && !hasMavenCoordinates()) {
                throw new IOException("tool_library must define either jar_path or Maven coordinates (group_id, artifact_id, version)");
            }

            if (!hasDirectJarPath()) {
                if (isBlank(groupId)) {
                    throw new IOException("tool_library.group_id is required when jar_path is not set");
                }
                if (isBlank(artifactId)) {
                    throw new IOException("tool_library.artifact_id is required when jar_path is not set");
                }
                if (isBlank(version)) {
                    throw new IOException("tool_library.version is required when jar_path is not set");
                }
            }

            return this;
        }

        public boolean hasDirectJarPath() {
            return !isBlank(jarPath);
        }

        public boolean hasMavenCoordinates() {
            return !isBlank(groupId) && !isBlank(artifactId) && !isBlank(version);
        }

        public String extensionOrDefault() {
            return isBlank(extension) ? "jar" : extension;
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}
