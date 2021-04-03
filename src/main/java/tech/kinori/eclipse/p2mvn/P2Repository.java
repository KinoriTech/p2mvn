package tech.kinori.eclipse.p2mvn;

import io.netty.handler.codec.http.HttpHeaders;
import org.asynchttpclient.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class P2Repository {

    public P2Repository(AsyncHttpClient client) {
        this.client = client;
    }

    public static void main(String... args) throws IOException {
        AsyncHttpClient client = Dsl.asyncHttpClient();
        P2Repository repo = new P2Repository(client);
        // Search compositeContent.xml, compositeContent.har, content.xml, content.jar
        try {
            repo.get("https://download.eclipse.org/modeling/emf/emf/builds/release/2.25/site.xml");
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            client.close();
        }
        System.out.println("Done");

    }

    public void get(String p2Url) throws ExecutionException, InterruptedException {

        BoundRequestBuilder getRequest = client.prepareGet(p2Url).setFollowRedirect(true);
        Response response = getRequest.execute(new AsyncCompletionHandler<Response>() {
            @Override
            public Response onCompleted(Response response) throws Exception {
                return response;
            }
        }).get();
        System.out.println(response.getStatusCode());
        System.out.println(response.getContentType());
        System.out.println(response.getResponseBody());

    }

    private final AsyncHttpClient client;
}
