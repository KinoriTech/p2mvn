package tech.kinori.eclipse.p2mvn.p2;

import org.asynchttpclient.AsyncHttpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.kinori.eclipse.p2.composite.Composite;
import tech.kinori.eclipse.p2mvn.Mode;
import tech.kinori.eclipse.p2mvn.P2Exception;

import java.net.URI;
import java.nio.file.Path;

public class CompositeP2 implements Repository {

    public CompositeP2(
        URI artifactsUri,
        Composite repository) {
        this.repository = repository;
    }

    @Override
    public boolean isComposite() {
        return true;
    }

    @Override
    public int size() {
        return this.repository.getChildren().getChild().size();
    }

    @Override
    public void process(
        @NotNull AsyncHttpClient client,
        @NotNull Path p2mvnFolder,
        @NotNull Mode mode,
        @Nullable String repoId,
        @Nullable String repoUrl) throws P2Exception {
        // TODO tech.kinori.eclipse.p2mvn.p2.CompositeP2.process not implemented
        throw new UnsupportedOperationException("Not implemeted");
    }

    private final Composite repository;
}
