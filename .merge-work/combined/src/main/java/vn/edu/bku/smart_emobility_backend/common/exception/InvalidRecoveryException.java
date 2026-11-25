package vn.edu.bku.smart_emobility_backend.common.exception;

public class InvalidRecoveryException extends RuntimeException {
    public InvalidRecoveryException() {
        super("Recovery credential is invalid or expired");
    }
}
