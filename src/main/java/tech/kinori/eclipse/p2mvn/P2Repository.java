package tech.kinori.eclipse.p2mvn;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.asynchttpclient.*;
import tech.kinori.eclipse.p2.Artifact;
import tech.kinori.eclipse.p2.Property;
import tech.kinori.eclipse.p2.Repository;
import tech.kinori.eclipse.p2.Rule;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class P2Repository {

    public P2Repository(AsyncHttpClient client) {
        this.client = client;
    }

    public static void main(String... args) throws IOException {
        AsyncHttpClient client = Dsl.asyncHttpClient();
        P2Repository p2repo = new P2Repository(client);
        // Search compositeArtifacts.xml, compositeArtifacts.jar, artifacts.xml, artifacts.jar
        final String p2Url = "https://download.eclipse.org/modeling/emf/emf/builds/release/2.25/";
        final String artifactsFile = p2Url + "artifacts.jar";
        try {
            Repository repo = p2repo.getArtifacts(artifactsFile);
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
            // This is a param
            Path p2mvnPath = Paths.get(System.getProperty("user.home"), "p2mvn/");
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
                        mappingValues.put("repoUrl", p2Url);
                        mappingValues.put("id", artfct.getId());
                        mappingValues.put("version", artfct.getVersion());
                        String jarUrl = tmplt.format(osgiMapping.getOutput(), mappingValues);
                        //boolean isSource =
                        Path jarFile = p2repo.getJar(jarUrl, p2mvnPath);
                        // Add parameter for install or deploy
                        // Add parameter for repoid
                        // Add parameter for repourl
                        String mvnClassifier = p2repo.mvnClassifier(artfct);
                        printWriter.printf(coord.getKey().deployCmd(mvnClassifier), coord.getKey().)
                    }
                    // Download the jar

                    // Create a deploy entry in the script

                    // Download the sources jar
                    // Create a deploy entry in the script for the sources


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
                .orElseGet(() -> "unkown");
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

    public Path getJar(String jarUrl, Path destination) throws IOException, ExecutionException, InterruptedException {
        Path jarFile = destination.resolve(Paths.get(jarUrl).getFileName());
        FileOutputStream stream = new FileOutputStream(jarFile.toString());
        System.out.println(jarFile.toString());
        BoundRequestBuilder getRequest = client.prepareGet(jarUrl).setFollowRedirect(true);
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

    private final AsyncHttpClient client;
}
