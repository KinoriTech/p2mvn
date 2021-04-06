package tech.kinori.eclipse.p2mvn;

public class P2Exception extends Exception {

    public P2Exception(String message) {
        super(message);
    }

    public P2Exception(
        String message,
        Throwable cause) {
        super(message, cause);
    }
}
