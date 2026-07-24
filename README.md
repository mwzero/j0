# j0
A lightweight ReAct loop module for local LLM experiments.

This module focuses on:
- Prompt-driven Reason/Act/Observe cycles
- Tool execution via XML-like call tags
- Local model providers (llama.cpp and Ollama)
- Resource-based agent configuration for repeatable tests

## What Is Included

- Core loop orchestrator: `HubberAgentLoop`
- YAML config loader: `ReactAgentConfig`
- Tool catalog indexing and RAG selection: `ToolIndex`
- Built-in file tools handler: `MinimalFileToolCallHandler`
- Local providers:
  - `MinimalLlamaCppProvider`
  - `MinimalOllamaProvider`

## Current Behavior (Important)

- Tool calls are currently parsed from tags in this format:
  - `<call:tool_name attr="value">content</call>`
  - self-closing variant: `<call:tool_name attr="value"/>`
- The interactive demo entrypoint is inside test code (`HubberAgentLoopTest.main`) and is intended for local experimentation.
- This module is intentionally minimal and standalone-oriented.

## Requirements

- Java 21+
- Maven 3.9+
- Optional local model runtime:
  - llama.cpp server (`/v1/chat/completions`) on `http://localhost:8080`
  - or Ollama on `http://localhost:11434`

## Quick Start

From repository root:

```bash
mvn -pl hubbers-react test
```

Or run only this module by pom path:

```bash
mvn test -f hubbers-react/pom.xml
```

Build the module jar:

```bash
mvn -pl hubbers-react package
```

## Run Loop Tests

Run all hubbers-react tests:

```bash
mvn -pl hubbers-react test
```

Run only selected tests:

```bash
mvn -pl hubbers-react -Dtest=ToolIndexTest test
mvn -pl hubbers-react -Dtest=MinimalFileToolCallHandlerTest test
mvn -pl hubbers-react -Dtest=HubberAgentLoopTest test
```

## Optional Interactive Demo

The interactive console demo is exposed via:
- `org.hubbers.react.HubberAgentLoopTest.main`

Typical workflow:
1. Start your local model server (llama.cpp or Ollama).
2. Run `HubberAgentLoopTest.main` from your IDE.
3. Chat with the agent and type `exit` to stop.

## Agent Configuration

Test profiles are under:
- `src/test/resources/org/hubbers/react/qwen2.5-3b-instruct-q8_0/`
- `src/test/resources/org/hubbers/react/qwen2.5-7b-instruct-q4_k_m/`

Each profile contains:
- `agent.yaml`
- `system-prompt.md`
- `tools.md`
- `memory.md`
- `observation-prompt.md`
- `reflection-prompt.md`
- `rejection-prompt.md`
- optional `discovery-prompt.md` (for tool RAG flow)

Example config sections in `agent.yaml`:
- `agent`
- `model`
- `config.max_iterations`
- `prompts`
- optional `tool_rag` (`enabled`, `top_k`, `discovery_prompt`)

## Tool Catalog and RAG

- `tools.md` defines the exact syntax the model should copy.
- `ToolIndex` parses the markdown table and ranks tools by token overlap.
- `memory_append` is pinned and always included in tool subsets.

## Trace Logs

Providers write request/response traces in the working directory:
- llama.cpp provider: `llamacpp-trace.log`
- Ollama provider: `ollama-trace.log`

If debugging model output, check these files first.

## Troubleshooting

### Tests pass but interactive run fails

- Verify local model server is running.
- Confirm endpoint and port:
  - llama.cpp: `http://localhost:8080/v1/chat/completions`
  - Ollama: `http://localhost:11434/api/generate`

### Tool call does not execute

- Ensure the model output uses exact tag syntax from `tools.md`.
- Check that tool names match exactly (for example `dir_exists`, `file_write`).

### Prompt/resource not found

- Verify profile path under `src/test/resources/org/hubbers/react/...`.
- Ensure `agent.yaml` references existing files in the same profile folder.

## Notes for Contributors

- Keep this module small and explicit (manual wiring, no Spring).
- Preserve deterministic test behavior (stub model providers where possible).
- Prefer adding focused unit tests when changing parsing, tool dispatch, or config loading.
