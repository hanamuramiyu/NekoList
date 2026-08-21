package hanamuramiyu.monban.identity;

public final class OnlineProfileResolutionException extends Exception {
    public enum Kind {
        NOT_FOUND,
        UNAVAILABLE
    }

    private final Kind kind;

    public OnlineProfileResolutionException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public OnlineProfileResolutionException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
