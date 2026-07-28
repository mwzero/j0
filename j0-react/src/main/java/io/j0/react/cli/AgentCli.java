package io.j0.react.cli;


import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.IOException;

@Slf4j
public class AgentCli {

    public static void main(String[] args) throws IOException {

        int exitCode = new CommandLine(new J0Command()).execute(args);
        System.exit(exitCode);
    }

}