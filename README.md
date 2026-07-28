# J0

j0 è un framework Java minimale per agenti **ReAct** (Reason/Act/Observe) pensato per LLM locali (llama.cpp, Ollama). Multi-modulo Maven:

- **Root** (`io.j0:j0-parent`, packaging `pom`, versione `0.1.0-SNAPSHOT`, Java 25 source/target). Moduli: j0-react, j0-tools-internal. Dipendenze gestite: Jackson 2.17.2, PicoCLI 4.7.6, SLF4J 2.0.16, Logback 1.5.8, Lombok 1.18.44, JUnit 5.11.3.
- **j0-react**: core loop, CLI, provider modello, tool RAG. Fat JAR via `maven-shade-plugin` (entry point `io.j0.react.Main`). Profilo Maven `native` con `native-maven-plugin` per build GraalVM nativa (`--no-fallback`, init SLF4J/Logback a build-time, Netty a runtime).
- **j0-tools-internal**: implementazione concreta dei tool filesystem (`FileToolCallHandler`), caricata **dinamicamente** a runtime via JAR esterna (non è una dipendenza compile-time del loop).
- **j0-code**: modulo separato per generare **solo** sorgente Java tramite provider LLM, salvarlo su disco, compilarlo con il JDK corrente ed eseguirlo. Nessun loop ReAct, nessun catalogo tool.
- **j0-mcp**: server MCP stdio minimale con un primo tool che riceve sorgente Java, lo compila con `JavaCodeExecutor` e ne esegue il `main`.

Filosofia dichiarata nel README: "keep this module small and explicit (manual wiring, no Spring)", "preserve deterministic test behavior", nessun auto-discovery/framework DI.

### 2. Architettura core (`io.j0.react`)

**`Agent.java`** — cuore del loop ReAct. Dipendenze iniettate: `ModelProvider`, `ToolCallHandler`, `ArtifactCatalogBuilder`, `ConversationMemory`, `ApprovalHandler`, `ToolIndex` (nullable).

- `initSystemPrompt(toolsContent)`: legge system-prompt (soul) + memory.md, sostituisce `{{TOOLS}}`/`{{MEMORY}}`, resetta history.
- `runLoop(userRequest)`:
  1. **Tool RAG** (se abilitato): genera query via `discovery-prompt.md` → `ToolIndex.search(query, topK)` (token-overlap scoring, `memory_append` sempre pinnato) → ricostruisce system prompt con sottoinsieme tool (`buildSubset`). Eseguito **una sola volta** all'inizio del loop, non ad ogni iterazione.
  2. Loop fino a `max_iterations`: chiama `modelProvider.generate()`, estrae `<thought>...</thought>` e `<call:tool attr="v">content</call>` via regex `<call:(.*?)(?:/>|</call>)`.
  3. Se c'è una call: check `approval="required"` → `ApprovalHandler.approve()`; se ok, `ToolCallHandler.handle()` → `RunResult`; osservazione JSON incorniciata da `observation-prompt.md` e reinserita come user message. Se rifiutata, messaggio da `rejection-prompt.md`.
  4. Se non c'è call → task considerato completo, esce dal loop.
  5. **Protezione infinite-loop**: rilevazione azione ripetuta identica → forza uscita.
  6. **Post-loop reflection**: `reflectAndUpdateMemory()` — prompt minimale (solo system+user, non history completa) per estrarre `<call:memory_append>` da salvare in memory.md.

**`AgentBuilder.java`**: factory (`fromResource`/`fromFile`) — carica `AgentConfig`, risolve path prompt, costruisce `ToolIndex` (se RAG on), istanzia `ToolCallHandler` (via `DynamicToolHandlerFactory` se `tool_library` configurato, altrimenti `DisabledToolCallHandler`), istanzia `ModelProvider`.

**`AgentConfig.java`**: schema di `agent.yaml` — `AgentMeta` (name, version, model, max_iterations), `PromptsConfig` (soul/tools/memory/observation/reflection/rejection filenames), `ToolRagConfig` (enabled/topK/discovery_prompt), `ToolLibraryConfig` (handler_class + jar_path oppure coordinate Maven group/artifact/version/classifier).

**`ArtifactCatalogBuilder.java`**: `build()` (catalogo completo da tools.md) / `buildSubset(entries)` (sottoinsieme per RAG).

**`Main.java`**: `new CommandLine(new J0Command()).execute(args)`.

### 3. Sottopacchetti

- **`cli/`** — `J0Command.java` (picocli): flag `--agent <path>` | `--agentresource <path>` (uno obbligatorio), `--userprompt <text>`, `--provider {ollama|llamacpp}` (default llamacpp), `--single-command`, `--auto-approve`. Senza `--single-command` → loop interattivo stdin/stdout.
- **`execution/`** — `RunResult` (status `RUNNING|PENDING|SUCCESS|FAILED`, output JsonNode, error, factory `success()`/`failed()`/`pending()`), `ExecutionStatus`, più DTO di tracing opzionali non usati nel loop base.
- **`memory/`** — `ConversationMemory` interface (`saveMessage`, `loadHistory`, `saveFact`, `getFact`, `searchFacts`, `clearConversation`); `InMemoryMemory` (ConcurrentHashMap); `Fact` record.
- **`model/`** — `Message` (role/content, factory `system/user/assistant/tool`), `ModelRequest` (systemPrompt, userPrompt, model, temperature, think, messages, functions), `ModelResponse` (content, functionCalls, finishReason, token usage). `FunctionDefinition`/`FunctionCall` presenti ma non usati nel loop attuale (no native function-calling, solo tag XML).
- **`model/providers/`**:
  - `ModelProvider` (astratta): `generate(ModelRequest)`, tracing helper (`traceRequest/traceResponse/traceError`).
  - `OllamaProvider`: POST `http://localhost:11434/api/generate`, payload `{model, prompt, stream:false}`, estrae campo `response`; trace in `ollama-trace.log`.
  - `LlamaCppProvider`: POST `http://localhost:8080/v1/chat/completions` (OpenAI-compatible), payload `{messages:[...], stream:false}`, estrae `choices[0].message.content` via **parsing JSON reale (Jackson)**, non regex (bug storico corretto: regex confondeva le virgolette escaped nel testo generato dal modello con la fine del campo); trace in `llamacpp-trace.log`.
- **`tools/`** — `ToolCallHandler` (`@FunctionalInterface handle(artifactName, JsonNode arguments) → RunResult`); `ToolEntry` (name, description, syntax, tags per scoring); `ToolIndex` (parse tools.md → mappa, `search(query, topK)` token-overlap, pinned=`memory_append`); `ApprovalHandler` (`approve(toolName, callToken) → boolean`); `MinimalApprovalHandler` (auto-approve o prompt stdin s/si/y/yes); `DisabledToolCallHandler` (fallback se `tool_library` non configurato); `DynamicToolHandlerFactory` (URLClassLoader + reflection per istanziare handler da JAR esterna); `MavenArtifactJarResolver` (risolve JAR path diretto o coordinate Maven in `~/.m2/repository`).

### 4. Tool catalog (j0-tools-internal → `FileToolCallHandler`)

18 tool, dispatch via switch su `artifactName`:

| Tool | Parametri | Note |
|---|---|---|
| `file_write` | filename, content | crea parent dir se mancante |
| `file_read` | filename | → `{content}` |
| `file_delete` | filename **oppure** foldername+pattern | dual-mode: singolo file o glob multi-file |
| `file_append` | filename, content | |
| `file_move` | src, dest | |
| `file_copy` | src, dest | |
| `file_exists` | filename | → `{exists}` |
| `file_info` | filename | size/created/modified/is_directory |
| `files_list` | foldername, min_size (opz.) | → `{files[], count}` |
| `files_search` | foldername, pattern | ricerca testo nei file |
| `files_find` | foldername, pattern (glob) | |
| `files_common` | foldername, pattern | file comuni a tutte le subdirectory |
| `dir_exists` | foldername | |
| `dir_create` | foldername | ricorsivo |
| `dir_delete` | foldername | ricorsivo, distruttivo |
| `file_compress` | src (file/folder/glob **), dest | crea ZIP, preserva gerarchia relativa |
| `file_decompress` | src, dest | con protezione zip-slip |
| `memory_append` | content | append a memory.md |

Molti tool hanno `approval="required"` per le operazioni con side-effect (write/delete/move/compress-related). Sintassi generale: `<call:tool_name attr="value">content</call>` oppure self-closing `<call:tool_name attr="value"/>`.

### 5. Formato `agent.yaml` (esempio reale)

```yaml
agent:
  name: gemma-3-1b-it-IQ4_NL.gguf
  version: 1.0.0
  description: ReAct loop agent with file system tools
  max_iterations: 10

prompts:
  soul:        system-prompt.md
  tools:       tools.md
  memory:      memory.md
  observation: observation-prompt.md
  reflection:  reflection-prompt.md
  rejection:   rejection-prompt.md

tool_rag:
  enabled: true
  top_k: 8
  discovery_prompt: discovery-prompt.md

tool_library:
  jar_path: tools/j0-tools-internal-0.1.0-SNAPSHOT.jar
  handler_class: io.j0.react.tools.FileToolCallHandler
```

### 6. File di prompt e placeholder

| File | Ruolo | Placeholder |
|---|---|---|
| `system-prompt.md` | identità + istruzioni comportamento | `{{TOOLS}}`, `{{MEMORY}}` |
| `tools.md` | tabella markdown statica di reference | — |
| `memory.md` | fatti persistenti, mutato da `memory_append` | — |
| `discovery-prompt.md` | genera keyword query per Tool RAG | `{{REQUEST}}` |
| `observation-prompt.md` | incornicia il risultato tool come user message | `{{TOOL_NAME}}`, `{{OBSERVATION}}` |
| `reflection-prompt.md` | istruisce il salvataggio fatti post-loop | `{{TODAY}}`, `{{ACTIONS}}` |
| `rejection-prompt.md` | messaggio quando l'utente nega l'approval | `{{TOOL_NAME}}` |

### 7. Esecuzione CLI

```bash
java -jar j0.jar --agent /path/to/agent --userprompt "..." --provider llamacpp --single-command
java -jar j0.jar --agentresource /agents/x/agent.yaml --provider ollama --auto-approve
java -jar j0.jar --agent ./my-agent   # loop interattivo, "exit" per uscire

mvn -pl j0-code package
java -jar j0-code/target/j0-code.jar --userprompt "scrivi un programma che stampa hello" --provider llamacpp
```

### 8. Vincoli/comportamenti noti importanti

- **Nessuna infrastruttura di code-execution**: nessun `javax.script`, GraalVM Truffle/polyglot, `ProcessBuilder`/`exec`, sandbox. GraalVM è usato **solo** per compilazione native-image (`--no-fallback`), non per esecuzione runtime di codice arbitrario.
- **RAG one-shot**: la query di discovery viene generata una sola volta a inizio `runLoop()`, non rigenerata ad ogni iterazione — rischio di perdere verbi/azioni in richieste multi-step.
- **`--auto-approve`** approva automaticamente **qualunque** tool con `approval="required"`, incluse operazioni distruttive come `dir_delete` su intere cartelle — nessuna distinzione di rischio granulare.
- **Modelli target**: pensato per piccoli modelli locali quantizzati (es. gemma-3-1b-it-IQ4_NL, qwen2.5-3b/7b); esperienza pratica mostra che questi modelli sono molto sensibili alla lunghezza/complessità del system prompt (prompt più corti e con pochi esempi mirati performano meglio di prompt lunghi ed esaustivi).
- Tool call extraction basata su regex per il tag XML (`<call:...>`), ma **parsing del body JSON HTTP delle risposte del modello fatto con Jackson**, non regex (per evitare falsi positivi su virgolette escaped).

