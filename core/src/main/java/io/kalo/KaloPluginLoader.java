package io.kalo;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.ClassPathLibrary;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnstableApiUsage")
public final class KaloPluginLoader implements PluginLoader {
    @Override
    public void classloader(@NotNull PluginClasspathBuilder classpathBuilder) {
        classpathBuilder.addLibrary(mavenCentral());
    }

    private static ClassPathLibrary mavenCentral() {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        RemoteRepository centralRepo = new RemoteRepository.Builder("central", "default", "https://maven-central.storage-download.googleapis.com/maven2").build();
        resolver.addRepository(centralRepo);

        resolver.addDependency(new Dependency(new DefaultArtifact("org.incendo:cloud-paper:2.0.0"), null));

        return resolver;
    }
}
