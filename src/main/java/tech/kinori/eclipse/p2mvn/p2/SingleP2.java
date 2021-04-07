package tech.kinori.eclipse.p2mvn.p2;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import org.asynchttpclient.AsyncHttpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.kinori.eclipse.p2.single.Artifact;
import tech.kinori.eclipse.p2.single.Property;
import tech.kinori.eclipse.p2.single.Rule;
import tech.kinori.eclipse.p2.single.Single;
import tech.kinori.eclipse.p2mvn.JarDownload;
import tech.kinori.eclipse.p2mvn.P2Exception;
import tech.kinori.eclipse.p2mvn.cli.Message;
import tech.kinori.eclipse.p2mvn.maven.MavenCoordinates;
import tech.kinori.eclipse.p2mvn.maven.MavenTemplate;
import tech.kinori.eclipse.p2mvn.maven.Mode;
import tech.kinori.eclipse.p2mvn.maven.Script;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Handle the processing of a single p2 repository
 */
public class SingleP2 implements Repository {

    /**
     * Constructor
     * @param uri
     * @param repository
     */
    public SingleP2(
        URI uri,
        Single repository) {
        this(uri, repository,true);
    }

    /**
     * Constructor
     * @param uri
     * @param repository
     * @param nested
     */
    public SingleP2(
        URI uri,
        Single repository,
        boolean nested) {
        this.repository = repository;
        this.uri = uri;
        this.message = new Message();
        this.nested = nested;
    }

    @Override
    public boolean isComposite() {
        return false;
    }

    @Override
    public int size() {
        return repository.getArtifacts().getArtifact().size();
    }

    @Override
    public void process(
        @NotNull AsyncHttpClient client,
        @NotNull Path p2mvnFolder,
        @NotNull Mode mode,
        @Nullable String repoId,
        @Nullable String repoUrl) throws P2Exception {
        this.purgeDirectory(p2mvnFolder.toFile());
        this.message.showInfo(String.format("Processing %d artifacts in the %s metadata", this.size(), this.uri));
        // Get the mapping for osgi-bundles
        Rule osgiMapping = this.repository.getMappings().getRule().stream()
            .filter(r -> "(& (classifier=osgi.bundle))".equals(r.getFilter()))
            .findFirst()
            .get();
        List<Artifact> artifacts = artifactCoordinates();
        this.message.showInfo(String.format("Found %d not-packed OSGI bundles", artifacts.size()));
        MavenTemplate template = new MavenTemplate();
        Map<Artifact, Path> downloads = new HashMap<>();
        try (ProgressBar pb = new ProgressBar("Processing", artifacts.size(), 1000, System.out,
                     ProgressBarStyle.COLORFUL_UNICODE_BLOCK, "",1, false, null,
                     ChronoUnit.SECONDS, 0L, Duration.ZERO);
                 ProgressBar dwnldpb = new ProgressBar("Downloading", -1, 1000, System.out,
                     ProgressBarStyle.COLORFUL_UNICODE_BLOCK, "",1, false, null,
                     ChronoUnit.SECONDS, 0L, Duration.ZERO)) {
            for (Artifact artifact : artifacts) {
                Path groupFolder = getGroupFolder(p2mvnFolder, artifact);
                URI jarUri = this.getJarUri(osgiMapping, template, artifact);
                dwnldpb.maxHint(this.downloadSize(artifact));
                // Download the jar
                Optional<Path> jarFile = new JarDownload(jarUri, groupFolder).getJar(client, dwnldpb);
                if (jarFile.isPresent()) {
                    downloads.put(artifact, jarFile.get());
                }
                dwnldpb.stepTo(0);
                pb.step();
            }
        }
        reportMissing(artifacts, downloads.keySet());
        Script script = new Script(downloads, mode, repoId, repoUrl, p2mvnFolder);
        script.createMavenScripts();
    }

    private final URI uri;
    private final Single repository;
    private final Message message;
    private final boolean nested;


    private void reportMissing(
        List<Artifact> artifacts,
        Set<Artifact> found) {
        artifacts.forEach(a -> {
            if (!found.contains(a)) {
                this.message.showInfo(String.format("Could not download "));
            }
        });
    }

    @NotNull
    private Path getGroupFolder(
        @NotNull Path p2mvnFolder,
        Artifact artifact) throws P2Exception {
        Path groupFolder = p2mvnFolder.resolve(this.groupId(artifact));
        try {
            Files.createDirectories(groupFolder);
        } catch (IOException e) {
            throw new P2Exception("There was an error storing the p2 repository jars; " + e.getMessage());
        }
        return groupFolder;
    }

    @NotNull
    private URI getJarUri(
        Rule osgiMapping,
        MavenTemplate tmplt,
        Artifact artfct) throws P2Exception {
        URI jarUrl = null;
        try {
            jarUrl = new URI(tmplt.format(osgiMapping.getOutput(), getFormatMappings(artfct)));
        } catch (URISyntaxException e) {
            throw new P2Exception("The artifact metadata contains invalid id and version information.");
        }
        return jarUrl;
    }


    @NotNull
    private Map<String, String> getFormatMappings(Artifact artfct) {
        Map<String, String> mappingValues = new HashMap<>();
        mappingValues.put("repoUrl", this.uri.toString().substring(0, this.uri.toString().length() - 1)); // Remove last slash
        mappingValues.put("id", artfct.getId());
        mappingValues.put("version", artfct.getVersion());
        return mappingValues;
    }

    private Long downloadSize(Artifact artifact) {
        return Long.valueOf(artifact.getProperties().getProperty().stream()
            .filter(p -> "download.size".equals(p.getName()))
            .map(Property::getPropertyValue)
            .findFirst()
            .orElseGet(() -> "-1"));
    }

    /**
     * Filter artifacts by
     * 1. classifier = osgi-bundle
     * 2. !property.name == format
     * @return a map of {@link MavenCoordinates} to all the {@link Artifact}s that belong to that coordinate
     */
    private List<Artifact> artifactCoordinates() {
        return this.repository.getArtifacts().getArtifact().stream()
            .filter(this.isOsgiBundle().and(this.notPacked()))
            .collect(Collectors.toList());
    }

    /**
     * We are only interested in bundles, not features or binaries
     *
     * @return true if the artifact is an osgi bundle
     */
    private Predicate<Artifact> isOsgiBundle() {
        return artifact -> "osgi.bundle".equals(artifact.getClassifier());
    }

    /**
     * We dont supported packed artifacts
     *
     * @return true if the artifact is not packed
     */
    private Predicate<Artifact> notPacked() {
        return artifact -> artifact.getProperties().getProperty().stream()
            .noneMatch(p -> "format".equals(p.getName())
                && "packed".equals(p.getPropertyValue()));

    }

    private String groupId(Artifact artifact) {
        return artifact.getProperties().getProperty().stream()
            .filter(p -> "maven-groupId".equals(p.getName()))
            .map(Property::getPropertyValue)
            .findFirst()
            .orElseGet(() -> "tech.kinori");
    }

    /**
     * Cleans the directory only if not nested
     * @param dir
     */
    void purgeDirectory(File dir) {
        if (nested) {
            return;
        }
        for (File file: dir.listFiles()) {
            if (file.isDirectory())
                purgeDirectory(file);
            file.delete();
        }
    }


}
