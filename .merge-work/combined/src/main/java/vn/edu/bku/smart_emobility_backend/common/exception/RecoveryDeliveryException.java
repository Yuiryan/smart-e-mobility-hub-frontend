package vn.edu.bku.smart_emobility_backend.common.exception;

import java.util.Objects;

public class RecoveryDeliveryException extends RuntimeException {

    public RecoveryDeliveryException(Throwable cause) {
        super("Recovery email could not be delivered", Objects.requireNonNull(cause));
    }
}
