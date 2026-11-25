package vn.edu.bku.smart_emobility_backend.common.domain;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BCRYPT_BYTES = 72;

    private PasswordPolicy() {
    }

    public static boolean isValidNewPassword(String password) {
        return password != null
                && !password.isBlank()
                && password.length() >= MIN_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_BYTES;
    }
}