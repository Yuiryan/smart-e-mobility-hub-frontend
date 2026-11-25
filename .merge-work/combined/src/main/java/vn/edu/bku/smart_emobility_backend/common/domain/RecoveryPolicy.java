package vn.edu.bku.smart_emobility_backend.common.domain;

public final class RecoveryPolicy {

    public static final int CODE_LENGTH = 6;
    public static final int CODE_BOUND = 1_000_000;

    public static final String CODE_PATTERN = "[0-9]{" + CODE_LENGTH + "}";
    public static final int MAX_FAILED_ATTEMPTS = 5;

    private RecoveryPolicy() {
    }
}
