package vn.edu.bku.smart_emobility_backend.common.exception;

public class SessionException extends RuntimeException {

    public enum Reason {
        INVALID_SESSION,
        SESSION_EXPIRED,
        ACCOUNT_INACTIVE,
        STATE_CONFLICT
    }

    private final Reason reason;

    public SessionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
