package vn.edu.bku.smart_emobility_backend.common.exception;

public class InvalidPasswordInputException extends RuntimeException {

    public InvalidPasswordInputException(String message) {
        super(message);
    }
}