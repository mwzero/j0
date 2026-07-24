package io.j0.react;


import lombok.extern.slf4j.Slf4j;

import org.hubbers.react.provider.MinimalLlamaCppProvider;
import org.hubbers.react.ArtifactCatalogBuilder;
import org.hubbers.react.HubberAgentLoop;
import org.hubbers.react.ReactAgentConfig;
import org.hubbers.react.memory.InMemoryMemory;
import org.hubbers.react.tool.MinimalApprovalHandler;
import org.hubbers.react.tool.MinimalFileToolCallHandler;
import org.hubbers.react.tool.ToolIndex;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Scanner;

@Slf4j
public class AgentExecutor {

    private static final String AGENT_RESOURCE = "/org/hubbers/react/qwen2.5-7b-instruct-q4_k_m/agent.yaml";

    // =========================================================================
    // Interactive main
    // =========================================================================

    public static void main(String[] args) throws IOException {

        Scanner scanner     = new Scanner(System.in);

        HubberAgentLoop agent = fromConfig(AGENT_RESOURCE, scanner);
        System.out.println("🤖 Hubber Agent Loop Pronto (Modello: " + agent.getModelName() + ").");

        while (true) {
            System.out.print("\n👤 Utente: ");
            String userInput = scanner.nextLine();
            if (userInput.equalsIgnoreCase("exit")) break;
            agent.runLoop(userInput);
        }
        scanner.close();
    }

    public static HubberAgentLoop fromConfig(String agentResource, Scanner scanner) throws IOException {
        ReactAgentConfig cfg = ReactAgentConfig.fromResource(agentResource);
        Path memoryPath = cfg.resolveWritable(cfg.prompts().memory());
        ReactAgentConfig.ToolRagConfig ragCfg = cfg.toolRagOrDefault();

        ToolIndex toolIndex = null;
        Path discoveryPromptPath = null;
        if (ragCfg.enabled() && ragCfg.discoveryPrompt() != null) {
            toolIndex = ToolIndex.from(cfg.resolvePrompt(cfg.prompts().tools()));
            discoveryPromptPath = cfg.resolvePrompt(ragCfg.discoveryPrompt());
        }

        return new HubberAgentLoop(
            new MinimalLlamaCppProvider(),
            new MinimalFileToolCallHandler(memoryPath),
            new ArtifactCatalogBuilder(cfg.resolvePrompt(cfg.prompts().tools())),
            new InMemoryMemory(),
            cfg.resolvePrompt(cfg.prompts().soul()),
            memoryPath,
            cfg.model().name(),
            cfg.config().maxIterations(),
            new MinimalApprovalHandler(scanner),
            cfg.resolvePrompt(cfg.prompts().observation()),
            cfg.resolvePrompt(cfg.prompts().reflection()),
            cfg.resolvePrompt(cfg.prompts().rejection()),
            toolIndex,
            ragCfg.topK(),
            discoveryPromptPath
        );
    }
}