package tech.kinori.eclipse.p2mvn;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import me.tongfei.progressbar.ColorPBR;
import me.tongfei.progressbar.ConsoleProgressBarConsumer;
import me.tongfei.progressbar.ProgressBar;
import org.asynchttpclient.*;
import tech.kinori.eclipse.p2.Artifact;
import tech.kinori.eclipse.p2.Property;
import tech.kinori.eclipse.p2.Repository;
import tech.kinori.eclipse.p2.Rule;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class P2Repository {

    public enum Mode {
        LOCAL("l"),
        DEPLOY("d"),
        INVALID("");

        private final String param;

        Mode(String param) {
            this.param = param;
        }

        public static Mode fromParam(String param) {
            switch (param) {
                case "":
                case "l":
                case "L":
                    return LOCAL;
                case "d":
                case "D":
                    return DEPLOY;
                default:
                    return INVALID;
            }
        }
    }

    public enum Type {
        COMPOSITE_JAR("compositeArtifacts.jar"),
        COMPOSITE("compositeArtifacts.xml"),
        SINGLE_JAR("artifacts.jar"),
        SINGLE("artifacts.xml");
        
        private final String fragment;

        public String fragment() {
            return this.fragment;
        }

        Type(String fragment) {
            this.fragment = fragment;
        }
    }

    public P2Repository(
        String p2Url,
        Path p2mvnPath,
        String repoId,
        String repoUrl,
        AsyncHttpClient client) {
        this.p2Url = p2Url;
        this.p2mvnPath = p2mvnPath;
        this.repoId = repoId;
        this.repoUrl = repoUrl;
        this.deploy = this.repoId == null || this.repoUrl == null;
        this.client = client;
    }

    private final AsyncHttpClient client;
    private final String p2Url;
    private final Path p2mvnPath;
    private final String repoId;
    private final String repoUrl;
    private final boolean deploy;

    /**
     * Determine the p2 repository type
     * @return
     */
    public Type analyze() {
        final URI p2Uri;
        try {
            p2Uri = getP2Uri();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                String.format("The provided p2 URL is not valid. %s", e.getMessage()));
        }
        EnumSet<Type> targets = EnumSet.allOf(Type.class);
        Type result = null;
        int step = 100 / targets.size();
        try (ProgressBar pb = new ProgressBar(
                "Analyze",
               100,
                1000,
                0L,
                Duration.ZERO,
                new ColorPBR(),
                new ConsoleProgressBarConsumer(System.out)
                )
            ) {
            for (Type target : targets) {
                pb.setExtraMessage(target.fragment());
                final URI artifactsUri = p2Uri.resolve(target.fragment());
                BoundRequestBuilder getRequest = client.prepareHead(artifactsUri.toString()).setFollowRedirect(true);
                Response response = null;
                try {
                    response = getRequest.execute(new AsyncCompletionHandler<Response>() {
                        @Override
                        public Response onCompleted(Response response) throws Exception {
                            return response;
                        }
                    }).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new IllegalStateException(
                        String.format("Unable to connect to the p2 repo. %s", e.getMessage()));
                }
                if (response != null) {
                    if (response.getStatusCode() == 200) {
                        result = target;
                        pb.stepTo(100);
                        break;
                    }
                }
                pb.stepBy(step);
            }
        }
        if (result == null) {
            throw new IllegalArgumentException("Unable to find artifact metadata in the p2 repository.");
        }
        return result;
    }

    public static void main(String... args) throws IOException, URISyntaxException {
        AsyncHttpClient client = Dsl.asyncHttpClient();
        // These are a params
        String p2Url = "https://download.eclipse.org/modeling/emf/emf/builds/release/2.25";
        final URI p2Uri = null;//getP2Uri(p2Url);

        final Path p2mvnPath = Paths.get(System.getProperty("user.home"), "p2mvn/");
        // Are any plugins non-SNAPSHOT? If so we need to ask both dirs
        final String repoId = "maven-snapshots";
        final URI repoUrl = new URI("http://172.16.46.46:8081/repository/maven-snapshots");


        P2Repository p2repo = new P2Repository(p2Url, p2mvnPath, repoId, repoUrl.toString(), client);


        final URI artifactsUri = p2Uri.resolve("artifacts.jar");
        // Search compositeArtifacts.xml, compositeArtifacts.jar, artifacts.xml, artifacts.jar
        try {
            Repository repo = p2repo.getArtifacts(artifactsUri.toString());
            System.out.println(repo.getName());
            // Check type is simple?
            // Get the mapping for osgi-bundles
            Rule osgiMapping = repo.getMappings().getRule().stream()
                    .filter(r -> "(& (classifier=osgi.bundle))".equals(r.getFilter()))
                    .findFirst()
                    .get();
            // Filter artifacts by
            // 1. classifier = osgi-bundle
            // 2. !property.name == format
            // Group artifacts by
            // 1. property.name == maven-groupid
            // 2. property.name == maven-artifactId
            //      -> here we need a class with artifact and sources and we need to collect
            Map<MavenCoordinates, List<Artifact>> mvnGroups = repo.getArtifacts().getArtifact().stream()
                    .filter(p2repo.isOsgiBundle().and(p2repo.notPacked()))
                    .collect(Collectors.groupingBy(
                            a -> new MavenCoordinates(
                                    p2repo.groupId(a),
                                    p2repo.artifactId(a),
                                    p2repo.version(a))
                    ));
            System.out.println(mvnGroups);
            // For each coordinate, create the folder, pom and download jars

            Path p = Files.createDirectories(p2mvnPath);
            System.out.println(p);
            Path p2mvnSh = p2mvnPath.resolve("deploy.sh");
            FileWriter fileWriter = new FileWriter(p2mvnSh.toFile());
            MavenTemplate tmplt = new MavenTemplate();
            Map<String, String> mappingValues = new HashMap<>();
            try (PrintWriter printWriter = new PrintWriter(fileWriter)) {
                for (Map.Entry<MavenCoordinates, List<Artifact>> coord : mvnGroups.entrySet()) {
                    for (Artifact artfct : coord.getValue()) {
                        mappingValues.clear();
                        mappingValues.put("repoUrl", p2Uri.toString().substring(0, p2Uri.toString().length()-1)); // Remove last slash
                        mappingValues.put("id", artfct.getId());
                        mappingValues.put("version", artfct.getVersion());
                        URI jarUrl = new URI(tmplt.format(osgiMapping.getOutput(), mappingValues));
                        // Download the jar
                        Path jarFile = p2repo.getJar(jarUrl, p2mvnPath);
                        // Add parameter for install or deploy
                        // Add parameter for repoid
                        // Add parameter for repourl
                        String mvnClassifier = p2repo.mvnClassifier(artfct);
                        if ("sources".equals(mvnClassifier)) {
                            mvnClassifier = "java-source";
                        }
                        // Create a deploy entry in the script
                        printWriter.printf(
                            coord.getKey().deployCmd(mvnClassifier),
                            jarFile,
                            repoId,
                            repoUrl);
                        printWriter.println();
                        // Download the sources jar
                        // Create a deploy entry in the script for the sources
                    }
                }
            }


        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (JAXBException e) {
            e.printStackTrace();
        } finally {
            client.close();
        }
        System.out.println("Done");
    }

    private URI getP2Uri() throws URISyntaxException {
        // Add test to add trailing backslash
        String url = this.p2Url;
        if (!url.endsWith("/")) {
            url += "/";
        }
        return new URI(url);
    }

    /**
     * We are only interested in bundles, not features or binaries
     * @return true if the artifact is an osgi bundle
     */
    private Predicate<Artifact> isOsgiBundle() {
        return artifact -> "osgi.bundle".equals(artifact.getClassifier());
    }

    /**
     * We dont supported packed artifacts
     * @return true if the artifact is not packed
     */
    private Predicate<Artifact>  notPacked() {
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

    public Path getJar(URI jarUrl, Path destination) throws IOException, ExecutionException, InterruptedException {
        String path = jarUrl.getPath();
        String jarname = path.substring(path.lastIndexOf('/') + 1);
        Path jarFile = destination.resolve(jarname);
        FileOutputStream stream = new FileOutputStream(jarFile.toString());
        System.out.println(jarFile.toString());
        BoundRequestBuilder getRequest = client.prepareGet(jarUrl.toString()).setFollowRedirect(true);
        Response response = getRequest.execute(new AsyncCompletionHandler<Response>() {

            @Override
            public State onBodyPartReceived(HttpResponseBodyPart bodyPart)
                    throws Exception {
                stream.getChannel().write(bodyPart.getBodyByteBuffer());
                return State.CONTINUE;
            }

            @Override
            public Response onCompleted(Response response) throws Exception {
                return response;
            }
        }).get();
        stream.close();
        System.out.println(response.getStatusCode());
        if (response.getStatusCode() != 200) {
            throw new IllegalArgumentException("file not found in server");
        }
        return jarFile;
    }

    public Repository getArtifacts(String p2Url) throws ExecutionException, InterruptedException, IOException, JAXBException {
        Path tempFile = Files.createTempFile("artifacts", ".jar");
        FileOutputStream stream = new FileOutputStream(tempFile.toString());
        System.out.println(tempFile.toString());
        BoundRequestBuilder getRequest = client.prepareGet(p2Url).setFollowRedirect(true);
        Response response = getRequest.execute(new AsyncCompletionHandler<Response>() {

            @Override
            public State onBodyPartReceived(HttpResponseBodyPart bodyPart)
                    throws Exception {
                stream.getChannel().write(bodyPart.getBodyByteBuffer());
                return State.CONTINUE;
            }

            @Override
            public Response onCompleted(Response response) throws Exception {
                    return response;
                }
            }).get();
        stream.close();
        System.out.println(response.getStatusCode());
        if (response.getStatusCode() != 200) {
            throw new IllegalArgumentException("file not found in server");
        }
        // We are expecting text/xml or application/x-java-archive
        System.out.println(response.getContentType());
        System.out.println(tempFile.toFile().getTotalSpace());
        // Extract jar
        Path dir = Files.createTempDirectory("p2");
        extractArchive(tempFile, dir);
        // Find xml
        File[] files = dir.toFile().listFiles(
                (dir1, name) -> name.startsWith("artifacts") && name.endsWith(".xml"));
        System.out.println(files[0]);
        JAXBContext context = JAXBContext.newInstance(Repository.class);
        return (Repository) context.createUnmarshaller()
                .unmarshal(new FileReader(files[0]));

    }

    public void extractArchive(Path archiveFile, Path destPath) throws IOException {

        Files.createDirectories(destPath); // create dest path folder(s)

        try (ZipFile archive = new ZipFile(archiveFile.toFile())) {

            // sort entries by name to always create folders first
            List<? extends ZipEntry> entries = archive.stream()
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .collect(Collectors.toList());

            // copy each entry in the dest path
            for (ZipEntry entry : entries) {
                Path entryDest = destPath.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectory(entryDest);
                    continue;
                }

                Files.copy(archive.getInputStream(entry), entryDest);
            }
        }

    }
}
