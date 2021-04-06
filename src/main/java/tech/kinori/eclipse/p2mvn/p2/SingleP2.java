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
import tech.kinori.eclipse.p2mvn.*;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.diogonunes.jcolor.Ansi.colorize;
import static com.diogonunes.jcolor.Attribute.CYAN_TEXT;

public class SingleP2 implements Repository {

    public SingleP2(
        URI uri,
        Single repository) {
        this.repository = repository;
        this.uri = uri;
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
        System.out.println(colorize(
            String.format("Processing %d artifacts in the %s metadata", this.size(), this.uri),
            CYAN_TEXT()));
        // Get the mapping for osgi-bundles
        Rule osgiMapping = this.repository.getMappings().getRule().stream()
            .filter(r -> "(& (classifier=osgi.bundle))".equals(r.getFilter()))
            .findFirst()
            .get();
        Map<MavenCoordinates, List<Artifact>> mvnGroups = artifactCoordinates();
        System.out.println(colorize(
            String.format("Artifacts grouped into %d maven coordinates", mvnGroups.size()),
            CYAN_TEXT()));
        MavenTemplate tmplt = new MavenTemplate();
        FileWriter fileWriter = createMavenScript(p2mvnFolder);
        try (ProgressBar pb = new ProgressBar("Processing", mvnGroups.size(), 1000, System.out,
                     ProgressBarStyle.COLORFUL_UNICODE_BLOCK, "",1, false, null,
                     ChronoUnit.SECONDS, 0L, Duration.ZERO);
                 ProgressBar dwnldpb = new ProgressBar("Downloading", -1, 1000, System.out,
                     ProgressBarStyle.COLORFUL_UNICODE_BLOCK, "",1, false, null,
                     ChronoUnit.SECONDS, 0L, Duration.ZERO)) {
            processCoordinates(client, p2mvnFolder, mode, repoId, repoUrl, osgiMapping, mvnGroups, tmplt, pb, dwnldpb);
        } catch (IOException e) {
            throw new P2Exception("Unable to create download folder" + e.getMessage());
        }

    }

    private void processCoordinates(
        @NotNull AsyncHttpClient client,
        @NotNull Path p2mvnFolder,
        @NotNull Mode mode,
        @Nullable String repoId,
        @Nullable String repoUrl,
        Rule osgiMapping,
        Map<MavenCoordinates, List<Artifact>> mvnGroups,
        MavenTemplate tmplt,
        ProgressBar pb,
        ProgressBar dwnldpb) throws IOException, P2Exception {
        for (Map.Entry<MavenCoordinates, List<Artifact>> coord : mvnGroups.entrySet()) {
            Path groupFolder = p2mvnFolder.resolve(coord.getKey().groupId());
            Files.createDirectories(groupFolder);
            FileWriter fileWriter = createMavenScript(groupFolder);
            try(PrintWriter printWriter = new PrintWriter(fileWriter)) {
                for (Artifact artfct : coord.getValue()) {
                    URI jarUri = getJarUri(osgiMapping, tmplt, artfct);
                    dwnldpb.maxHint(downloadSize(artfct));
                    // Download the jar
                    Optional<Path> jarFile = new JarDownload(jarUri, groupFolder).getJar(client, dwnldpb);
                    if (jarFile.isPresent()) {
                        addScriptEntry(mode, repoId, repoUrl, coord, printWriter, artfct, jarFile.get());
                    }
                    dwnldpb.stepTo(0);
                }
            }
            pb.step();
        }
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


    private void addScriptEntry(
        @NotNull Mode mode,
        @Nullable String repoId,
        @Nullable String repoUrl,
        Map.Entry<MavenCoordinates, List<Artifact>> coord,
        PrintWriter printWriter,
        Artifact artfct,
        Path jarFile) {
        String mvnClassifier = this.mvnClassifier(artfct);
        if ("sources".equals(mvnClassifier)) {
            mvnClassifier = "java-source";
        }
        if (mode.equals(Mode.DEPLOY)) {
            // Create a deploy entry in the script
            printWriter.printf(
                coord.getKey().deployCmd(mvnClassifier),
                jarFile,
                repoId,
                repoUrl);
        }
        if (mode.equals(Mode.LOCAL)) {
            // Create an install entry in the script
            printWriter.printf(
                coord.getKey().installCmd(mvnClassifier),
                jarFile,
                repoId,
                repoUrl);
        }
        printWriter.println();
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

    private Map<Path, Boolean> seen = new HashMap<>();

    @NotNull
    private FileWriter createMavenScript(Path groupFolder) throws P2Exception {
        FileWriter fileWriter;
        Path p2mvnSh = groupFolder.resolve("deploy.sh");
        boolean append = this.seen.getOrDefault(groupFolder, false);
        try {
            fileWriter = new FileWriter(p2mvnSh.toFile(), append);
            this.seen.put(groupFolder, true);
        } catch (IOException e) {
            throw new P2Exception("Unable to create maven script; " + e.getMessage());
        }
        return fileWriter;
    }

    private final URI uri;
    private final Single repository;

    /**
     * Filter artifacts by
     * 1. classifier = osgi-bundle
     * 2. !property.name == format
     * Group artifacts by
     * 1. property.name == maven-groupid
     * 2. property.name == maven-artifactId
     *
     * @return a map of {@link MavenCoordinates} to all the {@link Artifact}s that belong to that coordinate
     */
    private Map<MavenCoordinates, List<Artifact>> artifactCoordinates() {
        Map<MavenCoordinates, List<Artifact>> mvnGroups = this.repository.getArtifacts().getArtifact().stream()
            .filter(this.isOsgiBundle().and(this.notPacked()))
            .collect(Collectors.groupingBy(
                a -> new MavenCoordinates(
                    this.groupId(a),
                    this.artifactId(a),
                    this.version(a))
            ));
        //System.out.println(mvnGroups);
        return mvnGroups;
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

    private String mvnClassifier(Artifact artifact) {
        return artifact.getProperties().getProperty().stream()
            .filter(p -> "maven-classifier".equals(p.getName()))
            .map(Property::getPropertyValue)
            .findFirst()
            .orElseGet(() -> "jar");
    }
}
