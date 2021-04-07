package tech.kinori.eclipse.p2mvn.p2;

import org.asynchttpclient.AsyncHttpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.kinori.eclipse.p2.composite.Child;
import tech.kinori.eclipse.p2.composite.Composite;
import tech.kinori.eclipse.p2mvn.Inspector;
import tech.kinori.eclipse.p2mvn.cli.Message;
import tech.kinori.eclipse.p2mvn.maven.Mode;
import tech.kinori.eclipse.p2mvn.P2Exception;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class CompositeP2 implements Repository {

    public CompositeP2(
        URI baseURI,
        Composite repository) {
        this.baseURI = baseURI;
        this.repository = repository;
        this.message = new Message();
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
        for (Child child : this.repository.getChildren().getChild()) {
            URI singleRepoURI = this.baseURI.resolve(child.getLocation());
            message.showProgress("Downloading p2 repository jars from "+ singleRepoURI);
            Inspector inspector = new Inspector(singleRepoURI.toString(), client);
            Repository singleRepo = inspector.analyze();
            singleRepo.process(client, p2mvnFolder, mode, repoId, repoUrl);
            message.showProgress("maven scripts created successfully.");
            System.out.println("test");
        }
    }

    private final Composite repository;
    private final Message message;
    private final URI baseURI;
}
