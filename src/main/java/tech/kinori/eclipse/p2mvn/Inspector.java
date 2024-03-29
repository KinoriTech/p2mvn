package tech.kinori.eclipse.p2mvn;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import org.asynchttpclient.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kinori.eclipse.p2.composite.Composite;
import tech.kinori.eclipse.p2.single.Single;
import tech.kinori.eclipse.p2mvn.p2.CompositeP2;
import tech.kinori.eclipse.p2mvn.p2.Repository;
import tech.kinori.eclipse.p2mvn.p2.SingleP2;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Inspector {

    private static final Logger log = LoggerFactory.getLogger(Inspector.class);

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

    public Inspector(
        String p2Url,
        AsyncHttpClient client) {
        this.p2Url = p2Url;
        this.client = client;
    }

    private final AsyncHttpClient client;
    private final String p2Url;

    public Repository analyze() throws P2Exception {
        return this.analyze(false);
    }
    /**
     * Determine the p2 repository type
     * @return
     */
    public Repository analyze(boolean nested) throws P2Exception {
        final URI p2Uri = getP2Uri();
        EnumSet<Type> targets = EnumSet.allOf(Type.class);
        Repository result = null;
        int step = 100 / targets.size();
        try (ProgressBar pb = new ProgressBar("Analyze",100,1000, System.out,
                ProgressBarStyle.COLORFUL_UNICODE_BLOCK, "",1, false, null,
                ChronoUnit.SECONDS, 0L, Duration.ZERO)
            ) {
            for (Type target : targets) {
                Optional<Repository> optRepo = getRepository(target, p2Uri, nested);
                if (optRepo.isPresent()) {
                    result = optRepo.get();
                    pb.stepTo(100);
                    break;
                }
                pb.stepBy(step);
            }
        }
        if (result == null) {
            throw new IllegalArgumentException("Unable to find artifact metadata in the p2 repository.");
        }
        return result;
    }

    /**
     * Adds trailing backslash if missing and returns resulting URI
     * @return the uri
     * @throws P2Exception If the given string violates RFC 2396, as augmented by the above deviations
     */
    private URI getP2Uri() throws P2Exception {
        String url = this.p2Url;
        if (!url.endsWith("/")) {
            url += "/";
        }
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new P2Exception(
                String.format("The provided p2 URL is not valid. %s", e.getMessage()));
        }

    }

    private Optional<Repository> getRepository(
        Type target,
        URI p2Uri,
        boolean nested) throws P2Exception {
        final URI artifactsUri = p2Uri.resolve(target.fragment());
        BoundRequestBuilder getRequest = client.prepareGet(artifactsUri.toString()).setFollowRedirect(true);

        Path tempFile = createTempFile(target);
        Response response = downloadFile(getRequest, tempFile);
        if (response == null) { // Interrupted
            return Optional.empty();
        }
        if (response.getStatusCode() != 200) {
            return Optional.empty();
        }
        File repoXML;
        if ("application/x-java-archive".equals(response.getContentType())) {
            repoXML = extractXML(tempFile);
        } else {
            repoXML = tempFile.toFile();
        }
        try {
            switch (target) {
                case COMPOSITE, COMPOSITE_JAR -> {
                    JAXBContext context = JAXBContext.newInstance(Composite.class);
                    return Optional.of(new CompositeP2(p2Uri, (Composite) context.createUnmarshaller()
                        .unmarshal(new FileReader(repoXML))));
                }
                case SINGLE, SINGLE_JAR -> {
                    JAXBContext context = JAXBContext.newInstance(Single.class);
                    return Optional.of(new SingleP2(
                        p2Uri,
                        (Single) context.createUnmarshaller().unmarshal(new FileReader(repoXML)),
                        nested));
                }
            }
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Downloaded file should exit.", e);
        } catch (JAXBException e) {
            throw new P2Exception("The p2 artifact metadata can't be parsed.", e);
        }
        return Optional.empty();
    }

    private Response downloadFile(
        BoundRequestBuilder getRequest,
        Path tempFile) throws P2Exception {
        Response response = null;
        try(FileOutputStream stream = createStream(tempFile.toFile())) {
            response = getRequest.execute(new AsyncCompletionHandler<Response>() {

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
                })
                .get();
        } catch (IOException | ExecutionException e) {
            throw new P2Exception("Error saving p2 artifact metadata; " + e.getMessage());
        } catch (InterruptedException e) {
            log.warn("Download was interrupted.", e);
            Thread.currentThread().interrupt();
        }
        return response;
    }

    private Path createTempFile(Type target) throws P2Exception {
        String[] parts = target.fragment().split("\\.");
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(parts[0], "." + parts[1]);
        } catch (IOException e) {
            throw new P2Exception("Unable to create temporal file; " + e.getMessage());
        }
        return tempFile;
    }

    private FileOutputStream createStream(@NotNull File file) {
        try {
           return new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Should not enter here.");
        }
    }

    private File extractXML(Path tempFile) throws P2Exception {
        File repoXML;
        // Extract jar
        Path dir = null;
        try {
            dir = Files.createTempDirectory("p2");
        } catch (IOException e) {
            throw new P2Exception("Unable to create temporal directory for downloads; " + e.getMessage());
        }
        extractArchive(tempFile, dir);
        // Find xml
        File[] files = dir.toFile().listFiles(
            (dir1, name) -> name.toLowerCase().contains("artifacts") && name.endsWith(".xml"));
        // System.out.println(files[0]);
        repoXML = files[0];
        return repoXML;
    }

    /**
     *
     * @param archiveFile
     * @param destPath
     * @throws P2Exception if there is an error extracting the jar
     */
    private void extractArchive(Path archiveFile, Path destPath) throws P2Exception {
        try (ZipFile archive = new ZipFile(archiveFile.toFile())) {
            Files.createDirectories(destPath); // create dest path folder(s)
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
        } catch (IOException e) {
            throw new P2Exception("There was an error extracting metada from jar; " + e.getMessage());
        }

    }


//    public static void main(String... args) throws IOException, URISyntaxException {
//        AsyncHttpClient client = Dsl.asyncHttpClient();
//        // These are a params
//        String p2Url = "https://download.eclipse.org/modeling/emf/emf/builds/release/2.25";
//        final URI p2Uri = null;//getP2Uri(p2Url);
//
//        final Path p2mvnPath = Paths.get(System.getProperty("user.home"), "p2mvn/");
//        // Are any plugins non-SNAPSHOT? If so we need to ask both dirs
//        final String repoId = "maven-snapshots";
//        final URI repoUrl = new URI("http://172.16.46.46:8081/repository/maven-snapshots");
//
//
//        Inspector p2repo = new Inspector(p2Url, p2mvnPath, repoId, repoUrl.toString(), client);
//
//
//        final URI artifactsUri = p2Uri.resolve("artifacts.jar");
//        // Search compositeArtifacts.xml, compositeArtifacts.jar, artifacts.xml, artifacts.jar
//        try {
//            Repository repo = p2repo.getRepository(artifactsUri.toString());
//            System.out.println(repo.getName());
//            // Check type is simple?
//            // Get the mapping for osgi-bundles
//            Rule osgiMapping = repo.getMappings().getRule().stream()
//                    .filter(r -> "(& (classifier=osgi.bundle))".equals(r.getFilter()))
//                    .findFirst()
//                    .get();
//            // Filter artifacts by
//            // 1. classifier = osgi-bundle
//            // 2. !property.name == format
//            // Group artifacts by
//            // 1. property.name == maven-groupid
//            // 2. property.name == maven-artifactId
//            //      -> here we need a class with artifact and sources and we need to collect
//            Map<MavenCoordinates, List<Artifact>> mvnGroups = repo.getArtifacts().getArtifact().stream()
//                    .filter(p2repo.isOsgiBundle().and(p2repo.notPacked()))
//                    .collect(Collectors.groupingBy(
//                            a -> new MavenCoordinates(
//                                    p2repo.groupId(a),
//                                    p2repo.artifactId(a),
//                                    p2repo.version(a))
//                    ));
//            System.out.println(mvnGroups);
//            // For each coordinate, create the folder, pom and download jars
//
//            Path p = Files.createDirectories(p2mvnPath);
//            System.out.println(p);
//            Path p2mvnSh = p2mvnPath.resolve("deploy.sh");
//            FileWriter fileWriter = new FileWriter(p2mvnSh.toFile());
//            MavenTemplate tmplt = new MavenTemplate();
//            Map<String, String> mappingValues = new HashMap<>();
//            try (PrintWriter printWriter = new PrintWriter(fileWriter)) {
//                for (Map.Entry<MavenCoordinates, List<Artifact>> coord : mvnGroups.entrySet()) {
//                    for (Artifact artfct : coord.getValue()) {
//                        mappingValues.clear();
//                        mappingValues.put("repoUrl", p2Uri.toString().substring(0, p2Uri.toString().length()-1)); // Remove last slash
//                        mappingValues.put("id", artfct.getId());
//                        mappingValues.put("version", artfct.getVersion());
//                        URI jarUrl = new URI(tmplt.format(osgiMapping.getOutput(), mappingValues));
//                        // Download the jar
//                        Path jarFile = p2repo.getJar(jarUrl, p2mvnPath);
//                        // Add parameter for install or deploy
//                        // Add parameter for repoid
//                        // Add parameter for repourl
//                        String mvnClassifier = p2repo.mvnClassifier(artfct);
//                        if ("sources".equals(mvnClassifier)) {
//                            mvnClassifier = "java-source";
//                        }
//                        // Create a deploy entry in the script
//                        printWriter.printf(
//                            coord.getKey().deployCmd(mvnClassifier),
//                            jarFile,
//                            repoId,
//                            repoUrl);
//                        printWriter.println();
//                        // Download the sources jar
//                        // Create a deploy entry in the script for the sources
//                    }
//                }
//            }
//
//
//        } catch (ExecutionException e) {
//            e.printStackTrace();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        } catch (JAXBException e) {
//            e.printStackTrace();
//        } finally {
//            client.close();
//        }
//        System.out.println("Done");
//    }






}
