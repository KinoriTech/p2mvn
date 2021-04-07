package tech.kinori.eclipse.p2mvn.maven;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.kinori.eclipse.p2.single.Artifact;
import tech.kinori.eclipse.p2.single.Property;
import tech.kinori.eclipse.p2mvn.P2Exception;
import tech.kinori.eclipse.p2mvn.cli.Message;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Script {

    public Script(
        @NotNull Map<Artifact, Path> artifacts,
        @NotNull Mode mode,
        @Nullable String repoId,
        @Nullable String repoUrl,
        @NotNull Path p2mvnFolder) {
        this.artifacts = artifacts;
        this.mode = mode;
        this.p2mvnFolder = p2mvnFolder;
        this.repoId = repoId;
        this.repoUrl = repoUrl;
        this.message = new Message();
    }

    public void createMavenScripts() throws P2Exception {
        // Group by groupid/folder
        Map<String, List<Artifact>> grouped = this.artifacts.keySet().stream()
            .collect(Collectors.groupingBy(this::groupId));
        for (Map.Entry<String, List<Artifact>> e : grouped.entrySet()) {
            this.createScript(e.getKey(), e.getValue());
        }
    }

    private final Map<Artifact, Path> artifacts;
    private final Mode mode;
    private final Path p2mvnFolder;
    private final String repoId;
    private final String repoUrl;
    private final Message message;

    private void createScript(
        String groupid,
        List<Artifact> artifacts) throws P2Exception {
        Path groupFolder = this.p2mvnFolder.resolve(groupid);
        String os = System.getProperty("os.name").toLowerCase();
        String name = mode == Mode.DEPLOY ? "deploy" : "install";
        boolean batch = false;
        if (os.startsWith("win")) {
            name += ".bat";
            batch = true;
        } else {
            name += ".sh";
        }
        Path scriptPath = groupFolder.resolve(name);
        FileWriter fileWriter;
        try {
            // We always append because it can be a composite repo
            fileWriter = new FileWriter(scriptPath.toFile(), true);
        } catch (IOException e) {
            throw new P2Exception("Unable to create maven script; " + e.getMessage());
        }
        try (PrintWriter printWriter = new PrintWriter(fileWriter)) {
            for (Artifact artifact: artifacts) {
                addScriptEntry(printWriter, artifact, batch);
            }
        }
        this.message.showResult("Maven script created", scriptPath.toString());
    }
    
    private void addScriptEntry(
        PrintWriter printWriter,
        Artifact artfct,
        boolean batch) {
        String mvnClassifier = this.mvnClassifier(artfct);
        if ("sources".equals(mvnClassifier)) {
            mvnClassifier = "java-source";
        }
        MavenCoordinates coord = new MavenCoordinates(this.groupId(artfct), this.artifactId(artfct), this.version(artfct));
        String format = "";
        if (mode.equals(Mode.DEPLOY)) {
            // Create a deploy entry in the script
            format = coord.deployCmd(mvnClassifier, batch);
        }
        if (mode.equals(Mode.LOCAL)) {
            // Create an install entry in the script
            format = coord.installCmd(mvnClassifier, batch);
        }
        printWriter.printf(
            format,
            this.artifacts.get(artfct),
            this.repoId,
            this.repoUrl);
    }

    private String mvnClassifier(Artifact artifact) {
        return artifact.getProperties().getProperty().stream()
            .filter(p -> "maven-classifier".equals(p.getName()))
            .map(Property::getPropertyValue)
            .findFirst()
            .orElseGet(() -> "jar");
    }

    private String groupId(Artifact artifact) {
        return artifact.getProperties().getProperty().stream()
            .filter(p -> "maven-groupId".equals(p.getName()))
            .map(Property::getPropertyValue)
            .findFirst()
            .orElseGet(() -> "tech.kinori");
    }

    private String artifactId(Artifact artifact) {
        return artifact.getProperties().getProperty().stream()
            .filter(p -> "maven-artifactId".equals(p.getName()))
            .map(Property::getPropertyValue)
            .findFirst()
            .orElseGet(() -> "unknown");
    }

    private String version(Artifact artifact) {
        return artifact.getProperties().getProperty().stream()
            .filter(p -> "maven-version".equals(p.getName()))
            .map(Property::getPropertyValue)
            .findFirst()
            .orElseGet(() -> "0.0.0");
    }
}
