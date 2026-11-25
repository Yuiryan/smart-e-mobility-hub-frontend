package vn.edu.bku.smart_emobility_backend.common.exception;

import java.util.List;

public class SchedulingException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<String> violations;

    public SchedulingException(int status, String code, List<String> violations) {
        super(code);
        this.status = status;
        this.code = code;
        this.violations = List.copyOf(violations);
    }

    public int status() { return status; }
    public String code() { return code; }
    public List<String> violations() { return violations; }
}
