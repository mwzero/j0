package io.j0.react;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.j0.react.execution.ExecutionStatus;
import io.j0.react.execution.RunResult;
import io.j0.react.memory.ConversationMemory;
import io.j0.react.memory.InMemoryMemory;
import io.j0.react.model.Message;
import io.j0.react.model.ModelRequest;
import io.j0.react.model.ModelResponse;
import io.j0.react.model.providers.ModelProvider;
import io.j0.react.tools.ApprovalHandler;
import io.j0.react.tools.ToolCallHandler;
import io.j0.react.tools.ToolEntry;
import io.j0.react.tools.ToolIndex;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ciclo ReAct - Reason/Act
 *  ┌─────────────────────────────────────────┐
    │          Input dell'Utente              │
    └────────────────────┬────────────────────┘
                         ▼
             ┌──────────────────────┐
    ┌───────►│  THINK (Pensiero)    │
    │        └───────────┬──────────┘
    │                    ▼
    │        ┌──────────────────────┐
    │        │   ACT (Azione)       │
    │        └───────────┬──────────┘
    │                    ▼
    │        ┌──────────────────────┐
    │        │ OBSERVE (Osservazione)│
    │        └───────────┬──────────┘
    │                    ▼
    │        ┌──────────────────────┐
    │        │ REFLECT (Riflessione)│
    │        └───────────┬──────────┘
    │                    │
    └────────────────────┴────────────────────► Output Finale

 * <p>Ciclo ReAct interattivo. Tutte le dipendenze sono iniettate via costruttore per
 * permettere il testing unitario e il riuso con provider alternativi.</p>
 *
 * <p>Per l'uso standalone, il metodo {@link #main(String[])} crea le implementazioni
 * concrete via inner class: {@link MinimalOllamaProvider}, {@link FileToolCallHandler},
 * {@link InMemoryMemory}.</p>
 */
@Slf4j
public class Agent {

    private static final Pattern CALL_PATTERN  = Pattern.compile("<call:(.*?)(?:/>|</call>)", Pattern.DOTALL);
    private static final Pattern ATTR_PATTERN  = Pattern.compile("(\\w+)=\"([^\"]*)\"");
    private static final ObjectMapper MAPPER   = new ObjectMapper();

    // =========================================================================
    // DIP-injected dependencies
    // =========================================================================

    private final ModelProvider         modelProvider;
    private final ToolCallHandler        toolCallHandler;
    private final ConversationMemory     conversationMemory;
    private final ArtifactCatalogBuilder artifactCatalogBuilder;
    private final ApprovalHandler        approvalHandler;

    /** Unique identifier for the current conversation session. */
    private final String conversationId = UUID.randomUUID().toString();

    private final String modelName;
    private final int    maxIterations;
    private final Path   soulPath;
    private final Path   memoryPath;
    private final Path   observationPromptPath;
    private final Path   reflectionPromptPath;
    private final Path   rejectionPromptPath;

    // -------------------------------------------------------------------------
    // Tool RAG
    // -------------------------------------------------------------------------

    /** {@code null} means Tool RAG is disabled; all tools are injected unconditionally. */
    private final ToolIndex toolIndex;
    private final int       toolRagTopK;
    private final Path      discoveryPromptPath;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Creates a new ReAct loop with the given platform dependencies.
     *
     * @param modelProvider          routes LLM calls (required)
     * @param toolCallHandler        dispatches tool invocations (required)
     * @param artifactCatalogBuilder builds the tool catalog injected into the system prompt (required)
     * @param conversationMemory     persists conversation history (required)
     * @param soulResource           absolute path to the soul/persona prompt file (required)
     * @param memoryResource         absolute path to the memory file (required)
     * @param modelName              name of the model to use (required)
     * @param maxIterations          maximum number of iterations per user request (required)
     * @param approvalHandler        callback invoked before executing tools with {@code approval="required"} (required)
     * @param observationPromptPath  absolute path to the observation prompt template file (required)
     * @param reflectionPromptPath   absolute path to the memory reflection prompt file (required)
     * @param rejectionPromptPath    absolute path to the approval-rejection message template file (required)
     * @param toolIndex              pre-built tool index for RAG-based discovery; {@code null} disables RAG
     * @param toolRagTopK            maximum number of non-pinned tools returned by RAG search
     * @param discoveryPromptPath    absolute path to the discovery prompt template; may be {@code null} when RAG is disabled
     */
    public Agent(
            ModelProvider modelProvider,
            ToolCallHandler toolCallHandler,
            ArtifactCatalogBuilder artifactCatalogBuilder,
            ConversationMemory conversationMemory,
            Path soulResource,
            Path memoryResource,
            String modelName,
            int maxIterations,
            ApprovalHandler approvalHandler,
            Path observationPromptPath,
            Path reflectionPromptPath,
            Path rejectionPromptPath,
            ToolIndex toolIndex,
            int toolRagTopK,
            Path discoveryPromptPath) {
                            
        this.soulPath   = soulResource;
        this.memoryPath = memoryResource;

        this.modelProvider      = modelProvider;
        this.toolCallHandler    = toolCallHandler;
        this.artifactCatalogBuilder   = artifactCatalogBuilder;
        this.conversationMemory = conversationMemory;

        this.modelName      = modelName;
        this.maxIterations  = maxIterations;
        this.approvalHandler = approvalHandler;
        this.observationPromptPath = observationPromptPath;
        this.reflectionPromptPath  = reflectionPromptPath;
        this.rejectionPromptPath   = rejectionPromptPath;
        this.toolIndex             = toolIndex;
        this.toolRagTopK           = toolRagTopK;
        this.discoveryPromptPath   = discoveryPromptPath;

        this.initSystemPrompt();
    }


    // =========================================================================
    // System prompt initialization
    // =========================================================================

    /**
     * Assembla il prompt di sistema leggendo soul.md e memory.md, poi usa
     * {@link ArtifactCatalogBuilder} per ottenere il catalogo dei tool.
     * Reinizializza la conversazione nel {@link ConversationMemory}.
     */
    private void initSystemPrompt() {
        initSystemPrompt(artifactCatalogBuilder.build());
    }

    private void initSystemPrompt(String toolsContent) {
        String soulContent   = loadResource(soulPath,   "Tu sei Hubber, un agente autonomo.");
        String memoryContent = loadResource(memoryPath, "Nessun dato in memoria.");

        String systemPrompt = soulContent
                .replace("{{TOOLS}}", toolsContent)
                .replace("{{MEMORY}}", memoryContent);

        conversationMemory.clearConversation(conversationId);
        conversationMemory.saveMessage(conversationId, Message.system(systemPrompt));
    }

    // =========================================================================
    // ReAct loop
    // =========================================================================

    public void runLoop(String userRequest) {

        log.info("User Request={}", userRequest);

        // Tool RAG: build a reduced catalog tailored to this specific request.
        // Falls back to the full catalog when RAG is disabled or discovery fails.
        if (toolIndex != null && discoveryPromptPath != null) {
            try {
                String query  = discoverToolQuery(userRequest);
                List<ToolEntry> subset = toolIndex.search(query, toolRagTopK);
                log.info("[ToolRAG] Query='{}' → {} tools selected.", query, subset.size());
                initSystemPrompt(artifactCatalogBuilder.buildSubset(subset));
            } catch (Exception e) {
                log.warn("[ToolRAG] Discovery failed, using full catalog.", e);
                initSystemPrompt();
            }
        } else {
            // Refresh context (re-reads memory.md) before each interaction
            initSystemPrompt();
        }

        conversationMemory.saveMessage(conversationId, Message.user(userRequest));

        int iteration = 0;
        boolean taskComplete = false;

        // Track successful write-type operations for the reflection step.
        // Small models cannot reliably extract this from conversation history.
        List<String> completedActions = new java.util.ArrayList<>();
        
        // Track the last tool call to detect infinite loops (same tool called repeatedly)
        String lastToolCall = null;

        while (!taskComplete && iteration < maxIterations) {

            iteration++;
            log.info("[Loop Iterazione {}] L'agente sta pensando...", iteration);

            List<Message> history = conversationMemory.loadHistory(conversationId);

            ModelRequest request = new ModelRequest();
            request.setModel(modelName);
            request.setThink(false);
            request.setMessages(history);

            ModelResponse response = modelProvider.generate(request);
            String rawResponse = Objects.requireNonNullElse(response.getContent(), "");

            String thought = extractTagContent(rawResponse, "thought");
            if (!thought.isEmpty()) {
                log.debug("Pensiero Agente: {}", thought);
            }

            String toolCall = extractToolCall(rawResponse);
            
            // Detect infinite loop: if the same tool call is repeated, force termination
            if (toolCall.equals(lastToolCall) && !toolCall.isEmpty()) {
                log.warn("[Loop rilevato] Lo stesso tool call si ripete: {}. Interrompo il ciclo.", toolCall);
                String cleanResponse = rawResponse
                        .replaceAll("<thought>.*?</thought>", "")
                        .replaceAll("<call:.*?</call>", "")
                        .trim();
                if (cleanResponse.isEmpty()) {
                    cleanResponse = "Operazione completata con successo.";
                }
                log.info("Risposta Finale Agente: {}", cleanResponse);
                conversationMemory.saveMessage(conversationId, Message.assistant(cleanResponse));
                taskComplete = true;
            } else if (!toolCall.isEmpty()) {
                lastToolCall = toolCall;  // Update last tool call for next iteration
                conversationMemory.saveMessage(conversationId, Message.assistant(rawResponse));
                log.info("Esecuzione Tool rilevata: {}", toolCall);

                String   toolName  = extractToolName(toolCall);
                JsonNode arguments = parseToolArguments(toolCall);

                // Human-in-the-loop: block execution when approval="required"
                boolean needsApproval = arguments.has("approval")
                        && "required".equals(arguments.get("approval").asText());

                if (needsApproval && !approvalHandler.approve(toolName, toolCall)) {
                    log.info("[Approvazione negata] L'utente ha rifiutato l'esecuzione di: {}", toolName);
                    String rejection = loadResource(rejectionPromptPath,
                            "L'utente ha rifiutato l'esecuzione del tool '{{TOOL_NAME}}'. Informa l'utente e chiedi come procedere.")
                            .replace("{{TOOL_NAME}}", toolName);
                    conversationMemory.saveMessage(conversationId, Message.user(rejection));
                    continue;
                }

                RunResult toolResult  = toolCallHandler.handle(toolName, arguments);
                String    observation = buildObservation(toolResult);
                log.info("Osservazione (Risultato): {}", observation);

                boolean needsSideEffect = arguments.has("sideeffect")
                        && "true".equals(arguments.get("sideeffect").asText());

                // Track successful write-type actions for the reflection step
                if (needsSideEffect && toolResult.getStatus() == ExecutionStatus.SUCCESS) {
                    completedActions.add(toolName + " " + arguments);
                }

                // Inject as user message using the externalised observation prompt template.
                // Small models do not reliably handle the "tool" role; a framed user message
                // produces cleaner output and prevents hallucination of unexecuted steps.
                String observationTemplate = loadResource(observationPromptPath,
                        "Osservazione dal tool '{{TOOL_NAME}}': {{OBSERVATION}}");
                String framedObservation = observationTemplate
                        .replace("{{TOOL_NAME}}", toolName)
                        .replace("{{OBSERVATION}}", observation);
                conversationMemory.saveMessage(conversationId, Message.user(framedObservation));
            } else {
                String cleanResponse = rawResponse.replaceAll("<thought>.*?</thought>", "").trim();
                log.info("Risposta Finale Agente: {}", cleanResponse);
                conversationMemory.saveMessage(conversationId, Message.assistant(rawResponse));
                taskComplete = true;
            }
        }

        if (iteration >= maxIterations) {
            log.warn("Loop interrotto: raggiunto il limite massimo di {} iterazioni.", maxIterations);
        }

        reflectAndUpdateMemory(completedActions);
    }

    /**
     * Post-loop memory reflection step.
     * <p>Asks the model to persist relevant facts using {@code memory_append}.
     * To keep the context minimal (critical for small models), we do NOT pass the
     * full conversation history — only a dedicated system prompt explaining the
     * {@code memory_append} syntax and a single user message listing the completed
     * actions. This avoids context overload that causes the 3B model to produce
     * "NESSUNA_MEMORIA" instead of the required tool-call tag.
     */
    private void reflectAndUpdateMemory(List<String> completedActions) {
        log.debug("[Memory Reflection] Avvio consolidamento memoria...");

        if (completedActions.isEmpty()) {
            log.debug("[Memory Reflection] Nessuna azione completata, riflessione saltata.");
            return;
        }

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        String raw = loadResource(reflectionPromptPath,
                "Sei un assistente che salva note in memoria. Usa memory_append per salvare fatti. Se non hai nulla da salvare, rispondi: NESSUNA_MEMORIA");
        String[] parts = raw.split("(?m)^---$", 2);
        String systemPrompt = parts[0].trim();
        String userTemplate = parts.length > 1 ? parts[1].trim()
                : "{{ACTIONS}}\n\nSalva le note rilevanti con memory_append.";

        String actionsList = String.join("\n", completedActions.stream().map(a -> "- " + a).toList());
        String userMessage = userTemplate
                .replace("{{TODAY}}", today)
                .replace("{{ACTIONS}}", actionsList);

        List<Message> reflectionMessages = List.of(
                Message.system(systemPrompt),
                Message.user(userMessage));

        ModelRequest request = new ModelRequest();
        request.setModel(modelName);
        request.setThink(false);
        request.setMessages(reflectionMessages);

        ModelResponse response = modelProvider.generate(request);
        String rawResponse = Objects.requireNonNullElse(response.getContent(), "");
        log.debug("[Memory Reflection] Risposta modello: {}", rawResponse);

        // The model may emit multiple memory_append calls — process all of them.
        // group(1) is the content after "<call:" — consistent with extractToolCall().
        Matcher m = CALL_PATTERN.matcher(rawResponse);
        boolean any = false;
        while (m.find()) {
            String   toolCall  = m.group(1);
            String   toolName  = extractToolName(toolCall);
            JsonNode arguments = parseToolArguments(toolCall);
            if ("memory_append".equals(toolName)) {
                RunResult result = toolCallHandler.handle(toolName, arguments);
                log.info("[Memory Reflection] Memoria aggiornata: {}", buildObservation(result));
                any = true;
            }
        }
        if (!any) {
            log.debug("[Memory Reflection] Nessun fatto nuovo da memorizzare.");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String buildObservation(RunResult result) {
        ObjectNode observation = MAPPER.createObjectNode();
        
        if (result.getOutput() != null) {
            // Success: include status and merge the tool output
            observation.put("status", "success");
            if (result.getOutput().isObject()) {
                // Merge tool output fields into the observation
                result.getOutput().fields().forEachRemaining(entry ->
                    observation.set(entry.getKey(), entry.getValue()));
            } else {
                observation.set("data", result.getOutput());
            }
        } else {
            // Error: include status and error message
            observation.put("status", "error");
            observation.put("message", result.getError() != null ? result.getError() : "Errore sconosciuto.");
        }
        
        return observation.toString();
    }

    /**
     * Extracts the tool name from the raw call token (the part after {@code <call:}).
     * Example: {@code "file_write filename=\"foo\">content"} → {@code "file_write"}.
     */
    private String extractToolName(String toolCall) {
        int spaceIdx = toolCall.indexOf(' ');
        int gtIdx    = toolCall.indexOf('>');
        if (spaceIdx > 0 && (gtIdx < 0 || spaceIdx < gtIdx)) {
            return toolCall.substring(0, spaceIdx);
        }
        if (gtIdx > 0) {
            return toolCall.substring(0, gtIdx);
        }
        return toolCall;
    }

    /**
     * Parses named attributes and body content from a tool call token into a {@link JsonNode}.
     * Attributes like {@code filename="foo"} become object fields; text after {@code >}
     * becomes the {@code "content"} field.
     */
    private JsonNode parseToolArguments(String toolCall) {
        ObjectNode args = MAPPER.createObjectNode();
        Matcher attrMatcher = ATTR_PATTERN.matcher(toolCall);
        while (attrMatcher.find()) {
            args.put(attrMatcher.group(1), attrMatcher.group(2));
        }
        int gtIdx = toolCall.indexOf('>');
        if (gtIdx >= 0 && gtIdx < toolCall.length() - 1) {
            args.put("content", toolCall.substring(gtIdx + 1).trim());
        }
        return args;
    }

    
    private String loadResource(Path path, String fallback) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            log.warn("Impossibile leggere {}. Usando fallback.", path);
            return fallback;
        }
    }

    private String extractTagContent(String text, String tag) {
        Pattern pattern = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String extractToolCall(String text) {
        Matcher matcher = CALL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    /**
     * Controlla se il pensiero dell'agente contiene frasi di terminazione
     * che indicano che l'agente vuole presentare i risultati e terminare il ciclo.
     *
     * @param thought il pensiero dell'agente
     * @return true se il pensiero contiene frasi di terminazione
     */
    private boolean isTerminationThought(String thought) {
        if (thought == null || thought.isEmpty()) {
            return false;
        }
        String lower = thought.toLowerCase();
        return lower.contains("presento il risultato")
                || lower.contains("ora presento")
                || lower.contains("mostro i dati")
                || lower.contains("ho ricevuto i dati")
                || lower.contains("ecco la risposta")
                || lower.contains("ecco il risultato")
                || lower.contains("presento i file")
                || lower.contains("eccoli");
    }

    /**
     * Makes a minimal LLM call to extract a short keyword description of the
     * user's intent, which is then used to query the {@link ToolIndex}.
     *
     * @param userRequest the raw user input
     * @return a short keyword string suitable for token-overlap scoring
     */
    private String discoverToolQuery(String userRequest) {
        String template = loadResource(discoveryPromptPath,
                "Descrivi in massimo 5 parole: {{REQUEST}}");
        String prompt = template.replace("{{REQUEST}}", userRequest);

        ModelRequest req = new ModelRequest();
        req.setModel(modelName);
        req.setThink(false);
        req.setMessages(List.of(Message.user(prompt)));

        try {
            ModelResponse resp = modelProvider.generate(req);
            String content = Objects.requireNonNullElse(resp.getContent(), "").trim();
            if (content.isEmpty()) {
                log.debug("[ToolRAG] Discovery returned empty string, using raw request.");
                return userRequest;
            }
            return content;
        } catch (Exception e) {
            log.warn("[ToolRAG] Discovery LLM call failed, falling back to raw request.", e);
            return userRequest;
        }
    }


    public String getModelName() {
        return modelName;
        
    }

}