package io.j0.code;

import io.j0.code.cli.J0CodeCommand;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        
        int exitCode = new CommandLine(new J0CodeCommand()).execute(args);
        System.exit(exitCode);
    }
}