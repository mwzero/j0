package io.j0.mcp;

public final class Main {

    private static final int DEFAULT_PORT = 7070;

    private Main() {
    }

    public static void main(String[] args) {
        int port = resolvePort(args);
        new McpServer().run(port);
        System.out.println("j0-mcp listening on http://localhost:" + port + "/mcp");
    }

    static int resolvePort(String[] args) {
        if (args != null && args.length > 0) {
            return Integer.parseInt(args[0]);
        }

        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            return Integer.parseInt(envPort);
        }

        return DEFAULT_PORT;
    }
}