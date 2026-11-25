package vn.edu.bku.smart_emobility_backend.common.exception;

public class SimulationException extends RuntimeException {
    private final int status;
    private final String code;

    public SimulationException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
