package tech.kinori.eclipse.p2mvn;

import me.tongfei.progressbar.ProgressBar;
import org.asynchttpclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class JarDownload {

    public JarDownload(
        URI jarUri,
        Path folder) {
        this.jarUri = jarUri;
        this.folder = folder;
    }

    public Optional<Path> getJar(
        AsyncHttpClient client,
        ProgressBar pb) throws P2Exception {
        String path = this.jarUri.getPath();
        String jarname = path.substring(path.lastIndexOf('/') + 1);
        Path jarFile = this.folder.resolve(jarname);
        try (FileOutputStream stream = new FileOutputStream(jarFile.toString());) {
            BoundRequestBuilder getRequest = client.prepareGet(this.jarUri.toString()).setFollowRedirect(true);
            Response response = getRequest.execute(new AsyncCompletionHandler<Response>() {

                    @Override
                    public State onBodyPartReceived(HttpResponseBodyPart bodyPart)
                        throws Exception {
                        pb.stepBy(bodyPart.getBodyPartBytes().length);
                        stream.getChannel().write(bodyPart.getBodyByteBuffer());
                        return State.CONTINUE;
                    }

                    @Override
                    public Response onCompleted(Response response) throws Exception {
                        return response;
                    }
                })
                .get();
            // System.out.println(response.getStatusCode());
            if (response.getStatusCode() != 200) {
                return Optional.empty();
            }
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Should not enter here.");
        } catch (IOException | ExecutionException e) {
            throw new P2Exception("Error saving artifact jar; " + e.getMessage());
        } catch (InterruptedException e) {
            log.warn("Download was interrupted.", e);
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        return Optional.of(jarFile);
    }

    private static final Logger log = LoggerFactory.getLogger(JarDownload.class);
    private final URI jarUri;
    private final Path folder;

}
