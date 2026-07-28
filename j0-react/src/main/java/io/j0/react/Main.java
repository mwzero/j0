package io.j0.react;

import io.j0.react.cli.J0Command;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new J0Command()).execute(args);
        System.exit(exitCode);
    }
}
