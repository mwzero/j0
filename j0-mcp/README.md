# j0-mcp

Server MCP minimale implementato con Javalin (HTTP).

## Tool disponibile

- `java_compile_and_run`: riceve sorgente Java, lo compila con il JDK corrente ed esegue il `main`.

## Avvio

```bash
java -jar j0-mcp/target/j0-mcp.jar
```

Per default espone:

- endpoint MCP: `POST /mcp`
- health check: `GET /health`
- porta: `7070` (oppure `PORT` env var, oppure primo argomento CLI)

Esempio:

```bash
curl -s http://localhost:7070/mcp \
	-H "Content-Type: application/json" \
	-d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

Il server parla JSON-RPC 2.0 su HTTP e supporta almeno:

- `initialize`
- `tools/list`
- `tools/call`