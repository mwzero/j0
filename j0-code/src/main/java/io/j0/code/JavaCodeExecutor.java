package io.j0.code;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class JavaCodeExecutor {

    public ExecutionResult compileAndRun(String source, Path outputDirectory, Duration timeout, List<String> programArgs)
            throws IOException, InterruptedException {
        JavaSourceUtils.validateRunnableSource(source);
        String className = JavaSourceUtils.detectPublicClassName(source);

        Path normalizedOutput = outputDirectory.toAbsolutePath().normalize();
        Path sourceFile = normalizedOutput.resolve(className + ".java");
        Path classesDirectory = normalizedOutput.resolve("classes");
        Files.createDirectories(classesDirectory);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        compile(sourceFile, classesDirectory);
        RunOutcome outcome = execute(className, classesDirectory, timeout, programArgs);
        return new ExecutionResult(sourceFile, classesDirectory, className, outcome.exitCode(), outcome.output());
    }

    private void compile(Path sourceFile, Path classesDirectory) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler available. Run j0-code with a JDK, not a JRE.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjects(sourceFile.toFile());
            List<String> options = List.of(
                    "--release", Integer.toString(Runtime.version().feature()),
                    "-d", classesDirectory.toString()
            );
            Boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!Boolean.TRUE.equals(success)) {
                StringBuilder message = new StringBuilder("Java compilation failed:\n");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    message.append(diagnostic.getKind())
                            .append(" at line ")
                            .append(diagnostic.getLineNumber())
                            .append(": ")
                            .append(diagnostic.getMessage(Locale.ROOT))
                            .append('\n');
                }
                throw new IllegalStateException(message.toString().trim());
            }
        }
    }

    private RunOutcome execute(String className, Path classesDirectory, Duration timeout, List<String> programArgs)
            throws IOException, InterruptedException {
        String javaExecutable = resolveJavaExecutable();
        List<String> command = new java.util.ArrayList<>();
        command.add(javaExecutable);
        command.add("-cp");
        command.add(classesDirectory.toString());
        command.add(className);
        command.addAll(programArgs);

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new IllegalStateException("Generated program exceeded timeout of " + timeout.toSeconds() + " seconds.");
        }

        String output = readAll(process.getInputStream());
        return new RunOutcome(process.exitValue(), output);
    }

    private String resolveJavaExecutable() {
        Path javaHome = Path.of(System.getProperty("java.home"));
        Path candidate = javaHome.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
        if (Files.exists(candidate)) {
            return candidate.toString();
        }
        return "java";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String readAll(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            in.transferTo(buffer);
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    private record RunOutcome(int exitCode, String output) {
    }

    public record ExecutionResult(Path sourceFile, Path classesDirectory, String className, int exitCode, String output) {
    }
}