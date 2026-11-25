package vn.edu.bku.smart_emobility_backend.common.exception;

public class RegistrationConflictException extends RuntimeException {

    public enum Reason {
        EMAIL_ALREADY_REGISTERED,
        STATE_CONFLICT
    }

    private final Reason reason;

    public RegistrationConflictException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
