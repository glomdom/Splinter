package com.glomdom.splinter;

import com.google.gson.Gson;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@SuppressWarnings({"UnstableApiUsage", "unused"})
public class Loader implements PluginLoader {
    private static final PluginLibraries EMPTY = new PluginLibraries(Map.of(), List.of());

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        PluginLibraries libraries = load();

        libraries.asDependencies().forEach(resolver::addDependency);
        libraries.asRepositories().forEach(resolver::addRepository);

        classpathBuilder.addLibrary(resolver);
    }

    private PluginLibraries load() {
        try (var in = getClass().getResourceAsStream("/paper-libraries.json")) {
            if (in == null) return EMPTY;

            var libraries = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), PluginLibraries.class);
            return libraries != null ? libraries : EMPTY;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private record PluginLibraries(Map<String, String> repositories, List<String> dependencies) {
        public Stream<Dependency> asDependencies() {
            if (dependencies == null) return Stream.empty();

            return dependencies.stream()
                .filter(d -> d != null && !d.isBlank())
                .map(d -> new Dependency(new DefaultArtifact(d), null));
        }

        public Stream<RemoteRepository> asRepositories() {
            if (repositories == null) return Stream.empty();

            return repositories.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .map(e -> new RemoteRepository.Builder(e.getKey(), "default", e.getValue()).build());
        }
    }
}
