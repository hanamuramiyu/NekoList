package hanamuramiyu.monban.access.admin;

public final class AccessGrantScopeValidationException extends IllegalArgumentException {
    public AccessGrantScopeValidationException(String message) {
        super(message);
    }

    public AccessGrantScopeValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
