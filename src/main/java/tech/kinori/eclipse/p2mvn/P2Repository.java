package tech.kinori.eclipse.p2mvn;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.asynchttpclient.*;
import tech.kinori.eclipse.p2.Repository;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
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
