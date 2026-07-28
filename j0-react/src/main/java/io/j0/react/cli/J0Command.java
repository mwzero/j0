package io.j0.react.cli;

import java.util.concurrent.Callable;
import java.util.Scanner;


import io.j0.react.Agent;
import io.j0.react.AgentBuilder;
import io.j0.react.AgentBuilder.ProviderType;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Slf4j
@Command(
    name = "j0",
    description = "j0  CLI - Execute agents, tools and skills"
)
public class J0Command implements Callable<Integer> {
    
    @CommandLine.Option(
        names = "--agent",
        description = "Path to the agent folder"
    )
    String agentPath;

    @CommandLine.Option(
        names = "--agentresource",
        description = "Path to the agent folder"
    )
    String agentResourcePath;

    @CommandLine.Option(
        names = "--userprompt",
        description = "Path to the user prompt file"
    )
    String userPrompt;

    @CommandLine.Option(
        names = "--provider",
        defaultValue = "llamacpp",
        description = "Model provider: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})"
    )
    ProviderType provider;

    @CommandLine.Option(
        names = {"--single-command", "--once"},
        defaultValue = "false",
        description = "Execute a single command and exit"
    )
    boolean singleCommand;

    @CommandLine.Option(
        names = "--auto-approve",
        defaultValue = "false",
        description = "Automatically approve all tools that require confirmation"
    )
    boolean autoApprove;

    public Integer call() throws Exception {

        if (this.singleCommand && (this.userPrompt == null || this.userPrompt.isBlank())) {
            throw new CommandLine.ParameterException(
                new CommandLine(this),
                "Option '--userprompt' is required when '--single-command' is set."
            );
        }

        Scanner scanner     = new Scanner(System.in);
        boolean userPromptConsumed = false;

        Agent agent = this.agentPath != null
            ? AgentBuilder.fromFile(this.agentPath, scanner, this.provider, this.autoApprove)
            : AgentBuilder.fromResource(this.agentResourcePath, scanner, this.provider, this.autoApprove);
        System.out.println("🤖 Hubber Agent Loop Pronto (Modello: " + agent.getModelName() + ").");

        if (this.singleCommand)  {
            agent.runLoop(this.userPrompt);
            return 0;
        }

        while (true) {
            String userInput;
            if (!userPromptConsumed && this.userPrompt != null && !this.userPrompt.isBlank()) {
                userInput = this.userPrompt;
                userPromptConsumed = true;
                log.info("\n👤 Utente: " + userInput);
            } else {
                System.out.print("\n👤 Utente: ");
                userInput = scanner.nextLine();
            }
            if (userInput.equalsIgnoreCase("exit")) break;
            agent.runLoop(userInput);
        }
        scanner.close();

        return 0;
    }

}
