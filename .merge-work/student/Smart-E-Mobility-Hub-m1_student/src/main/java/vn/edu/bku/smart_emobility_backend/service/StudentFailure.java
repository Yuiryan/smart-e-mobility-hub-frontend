package vn.edu.bku.smart_emobility_backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Stable API error identifier alongside the human-readable reason. */
public class StudentFailure extends ResponseStatusException {
    private final String code;

    public StudentFailure(HttpStatus status,String code,String reason) {
        super(status,reason);
        this.code = code;
    }
    public String code() { return code; }
}
