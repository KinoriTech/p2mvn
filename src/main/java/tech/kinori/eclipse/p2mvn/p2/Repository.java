package tech.kinori.eclipse.p2mvn.p2;

import org.asynchttpclient.AsyncHttpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.kinori.eclipse.p2mvn.Mode;
import tech.kinori.eclipse.p2mvn.P2Exception;

import java.nio.file.Path;

public interface Repository {

    boolean isComposite();

    int size();

    void process(
        @NotNull AsyncHttpClient client,
        @NotNull Path p2mvnFolder,
        @NotNull Mode mode,
        @Nullable String repoId,
        @Nullable String repoUrl) throws P2Exception;
}
