package io.j0.react;

import java.util.Scanner;
import java.io.IOException;
import java.nio.file.Path;

import io.j0.react.tools.*;
import io.j0.react.model.providers.OllamaProvider;
import io.j0.react.memory.InMemoryMemory;
import io.j0.react.model.providers.LlamaCppProvider;

public class AgentBuilder {

    public enum ProviderType {
        ollama,
        llamacpp
    }

    public enum ConfigSource {
        RESOURCE,
        FILE
    }

    /**
     * Creates an Agent from a config resource path (classpath resource).
     */
    public static Agent fromResource(String agentResource, Scanner scanner, ProviderType provider) throws IOException {
        return fromResource(agentResource, scanner, provider, false);
    }

    /**
     * Creates an Agent from a config resource path (classpath resource).
     */
    public static Agent fromResource(String agentResource, Scanner scanner, ProviderType provider, boolean autoApprove) throws IOException {
        return fromConfig(agentResource, scanner, provider, ConfigSource.RESOURCE, autoApprove);
    }

    /**
     * Creates an Agent from a config file path (filesystem path).
     */
    public static Agent fromFile(String agentFilePath, Scanner scanner, ProviderType provider) throws IOException {
        return fromFile(agentFilePath, scanner, provider, false);
    }

    /**
     * Creates an Agent from a config file path (filesystem path).
     */
    public static Agent fromFile(String agentFilePath, Scanner scanner, ProviderType provider, boolean autoApprove) throws IOException {
        return fromConfig(agentFilePath, scanner, provider, ConfigSource.FILE, autoApprove);
    }

    /**
     * Creates an Agent from a config path with explicit source type.
     *
     * @param agentPath path to the agent config (resource or file)
     * @param scanner input scanner for approvals
     * @param provider model provider type
     * @param source config source type
     * @param autoApprove whether to automatically approve all required tools
     */
    private static Agent fromConfig(String agentPath, Scanner scanner, ProviderType provider, ConfigSource source, boolean autoApprove) throws IOException {
        AgentConfig cfg = switch (source) {
            case RESOURCE -> AgentConfig.fromResource(agentPath);
            case FILE -> AgentConfig.fromFile(agentPath);
        };

        return createAgent(cfg, scanner, provider, autoApprove);
    }

    /**
     * Creates the Agent instance with the given config.
     */
    private static Agent createAgent(AgentConfig cfg, Scanner scanner, ProviderType provider, boolean autoApprove) throws IOException {
        Path memoryPath = cfg.resolveWritable(cfg.prompts().memory());
        Path agentBaseDir = cfg.resolveAgentBaseDirectory();
        String toolsPromptFile = resolveToolsPrompt(cfg);
        Path toolsPromptPath = cfg.resolvePrompt(toolsPromptFile);
        AgentConfig.ToolRagConfig ragCfg = cfg.toolRagOrDefault();
        AgentConfig.ToolLibraryConfig toolLibraryCfg = cfg.optionalToolLibrary();

        ToolIndex toolIndex = null;
        Path discoveryPromptPath = null;
        if (ragCfg.enabled() && ragCfg.discoveryPrompt() != null) {
            toolIndex = ToolIndex.from(toolsPromptPath);
            discoveryPromptPath = cfg.resolvePrompt(ragCfg.discoveryPrompt());
        }

        ToolCallHandler toolCallHandler = toolLibraryCfg != null
            ? new DynamicToolHandlerFactory().create(toolLibraryCfg, memoryPath, agentBaseDir)
            : new DisabledToolCallHandler();

        var modelProvider = provider == ProviderType.ollama
                ? new OllamaProvider()
                : new LlamaCppProvider();

        return new Agent(
            modelProvider,
            toolCallHandler,
            new ArtifactCatalogBuilder(toolsPromptPath),
            new InMemoryMemory(),
            cfg.resolvePrompt(cfg.prompts().soul()),
            memoryPath,
            cfg.agent().name(),
            cfg.agent().max_iterations(),
            new MinimalApprovalHandler(scanner, autoApprove),
            cfg.resolvePrompt(cfg.prompts().observation()),
            cfg.resolvePrompt(cfg.prompts().reflection()),
            cfg.resolvePrompt(cfg.prompts().rejection()),
            toolIndex,
            ragCfg.topK(),
            discoveryPromptPath
        );
    }

    private static String resolveToolsPrompt(AgentConfig cfg) {
        String configured = cfg.prompts() != null ? cfg.prompts().tools() : null;
        return (configured == null || configured.isBlank()) ? "tools.md" : configured;
    }

}
