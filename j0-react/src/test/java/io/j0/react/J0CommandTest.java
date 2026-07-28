package io.j0.react;


import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import org.junit.jupiter.api.Test;

import io.j0.react.cli.J0Command;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled;

@Slf4j
public class J0CommandTest {

    /*
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    
    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }
    */

    @Test
    @Disabled
    void testOne() {
        
        // Test generic subcommand routing without invoking an artifact execution path.
        J0Command command = new J0Command();
        CommandLine commandLine = new CommandLine(command);
        
        int exitCode = commandLine.execute(
            "--agentresource", "/agents/gemma-3-1b-it-IQ4_NL/agent.yaml",
                    "--userprompt", "elenca i file della cartella corrente",
                    "--provider", "llamacpp",
                    "--single-command");
        
        assertEquals(0, exitCode, "command should return exit code 0");
    }

    @Test
    void testGenericCommand() {
        
        // Test generic subcommand routing without invoking an artifact execution path.
        J0Command command = new J0Command();
        CommandLine commandLine = new CommandLine(command);
        
        int exitCode = commandLine.execute(
            "--agent", "../j0-agents/agents/gemma-3-1b-it-IQ4_NL/agent.yaml",
                    "--userprompt", """
                    crea un file compresso denominato c:/temp2/temp.zip con tutti i files della cartella c:/temp 
                    che hanno una dimensione maggiore di 2k. cancella i files che hai utilizzato nel file compresso
                    """,
                    "--provider", "llamacpp",
                    "--auto-approve",
                    "--single-command");
        
        assertEquals(0, exitCode, "command should return exit code 0");
    }

    

}