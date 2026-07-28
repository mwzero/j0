package io.j0.react.tools;

import io.j0.react.AgentConfig;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.nio.file.Path;

/**
 * Creates a ToolCallHandler by loading an external tool library JAR at runtime.
 */
public final class DynamicToolHandlerFactory {

    private final MavenArtifactJarResolver resolver;

    public DynamicToolHandlerFactory() {
        this(new MavenArtifactJarResolver());
    }

    DynamicToolHandlerFactory(MavenArtifactJarResolver resolver) {
        this.resolver = resolver;
    }

    public ToolCallHandler create(AgentConfig.ToolLibraryConfig cfg, Path memoryPath, Path agentBaseDir) throws IOException {
        AgentConfig.ToolLibraryConfig validated = cfg.validated();
        Path jarPath = resolver.resolve(validated, agentBaseDir);

        URLClassLoader loader = buildClassLoader(jarPath);
        Class<?> loadedClass;
        try {
            loadedClass = Class.forName(validated.handlerClass(), true, loader);
        } catch (ClassNotFoundException e) {
            throw new IOException("Handler class not found in tool library: " + validated.handlerClass(), e);
        }

        if (!ToolCallHandler.class.isAssignableFrom(loadedClass)) {
            throw new IOException("Configured handler class does not implement ToolCallHandler: " + validated.handlerClass());
        }

        try {
            Constructor<?> constructor = loadedClass.getConstructor(Path.class);
            Object instance = constructor.newInstance(memoryPath);
            return (ToolCallHandler) instance;
        } catch (NoSuchMethodException e) {
            throw new IOException("Missing required constructor (Path) in handler class: " + validated.handlerClass(), e);
        } catch (ReflectiveOperationException e) {
            throw new IOException("Unable to instantiate handler class: " + validated.handlerClass(), e);
        }
    }

    private URLClassLoader buildClassLoader(Path jarPath) throws IOException {
        try {
            return new URLClassLoader(new java.net.URL[]{jarPath.toUri().toURL()}, DynamicToolHandlerFactory.class.getClassLoader());
        } catch (MalformedURLException e) {
            throw new IOException("Invalid JAR URL: " + jarPath, e);
        }
    }
}
