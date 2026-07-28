package io.j0.code.cli;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import io.j0.code.JavaCodeAgent;
import io.j0.code.JavaCodeExecutor;
import io.j0.code.ProviderType;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "j0-code",
    description = "Generate Java source only, compile it, and execute it."
)
public class J0CodeCommand implements Callable<Integer> {

    @CommandLine.Option(
        names = "--userprompt",
        required = true,
        description = "Prompt sent to the provider."
    )
    String userPrompt;

    @CommandLine.Option(
        names = "--provider",
        defaultValue = "llamacpp",
        description = "Model provider: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})"
    )
    ProviderType provider;

    @CommandLine.Option(
        names = "--model",
        defaultValue = "j0-java-generator",
        description = "Model name sent to the provider."
    )
    String modelName;

    @CommandLine.Option(
        names = "--class-name",
        defaultValue = "GeneratedMain",
        description = "Preferred public class name requested to the provider."
    )
    String preferredClassName;

    @CommandLine.Option(
        names = "--output-dir",
        defaultValue = "generated/j0-code",
        description = "Directory used to store source and classes."
    )
    Path outputDirectory;

    @CommandLine.Option(
        names = "--timeout-seconds",
        defaultValue = "10",
        description = "Maximum runtime allowed for the generated program."
    )
    long timeoutSeconds;

    @CommandLine.Option(
        names = "--arg",
        description = "Program argument passed to the generated main method."
    )
    List<String> programArgs = new ArrayList<>();

    protected JavaCodeAgent createAgent() {
        return JavaCodeAgent.create(provider, modelName);
    }

    protected JavaCodeExecutor createExecutor() {
        return new JavaCodeExecutor();
    }

    @Override
    public Integer call() throws Exception {
        JavaCodeAgent.GeneratedJavaSource generated = createAgent()
                .generate(userPrompt, preferredClassName);

        JavaCodeExecutor.ExecutionResult result = createExecutor().compileAndRun(
                generated.source(),
                outputDirectory,
                Duration.ofSeconds(timeoutSeconds),
                programArgs
        );

        System.out.println(result.sourceFile());
        System.out.println();
        System.out.println(generated.source());
        System.out.println();
        System.out.println(result.output());
        return result.exitCode();
    }
}