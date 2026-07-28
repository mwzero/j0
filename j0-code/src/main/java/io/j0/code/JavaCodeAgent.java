package io.j0.code;

import java.util.List;

import io.j0.react.model.Message;
import io.j0.react.model.ModelRequest;
import io.j0.react.model.ModelResponse;
import io.j0.react.model.providers.LlamaCppProvider;
import io.j0.react.model.providers.ModelProvider;
import io.j0.react.model.providers.OllamaProvider;

public class JavaCodeAgent {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You generate runnable Java source code only.
            Return raw Java source with no markdown fences and no explanation.
            Constraints:
            - Generate exactly one top-level public class.
            - Do not declare a package.
            - The class must include public static void main(String[] args) throws Exception.
            - Use only JDK standard library APIs.
            - The preferred class name is %s.
            """;

    private final ModelProvider modelProvider;
    private final String modelName;

    public JavaCodeAgent(ModelProvider modelProvider, String modelName) {
        this.modelProvider = modelProvider;
        this.modelName = modelName;
    }

    public static JavaCodeAgent create(ProviderType providerType, String modelName) {
        ModelProvider provider = providerType == ProviderType.ollama
                ? new OllamaProvider()
                : new LlamaCppProvider();
        return new JavaCodeAgent(provider, modelName);
    }

    public GeneratedJavaSource generate(String userPrompt, String preferredClassName) {
        ModelRequest request = new ModelRequest();
        request.setModel(modelName);
        request.setThink(false);
        request.setMessages(List.of(
                Message.system(SYSTEM_PROMPT_TEMPLATE.formatted(preferredClassName)),
                Message.user(userPrompt)
        ));

        ModelResponse response = modelProvider.generate(request);
        String source = JavaSourceUtils.extractJavaSource(response.getContent());
        JavaSourceUtils.validateRunnableSource(source);
        String className = JavaSourceUtils.detectPublicClassName(source);
        return new GeneratedJavaSource(className, source);
    }

    public record GeneratedJavaSource(String className, String source) {
    }
}