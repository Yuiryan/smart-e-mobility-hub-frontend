package vn.edu.bku.smart_emobility_backend.common.exception;

public class LoginException extends RuntimeException {

    public enum Reason {
        INVALID_CREDENTIALS,
        ACCOUNT_INACTIVE,
        STATE_CONFLICT
    }

    private final Reason reason;

    public LoginException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}