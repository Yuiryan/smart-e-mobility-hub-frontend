package vn.edu.bku.smart_emobility_backend.common.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import vn.edu.bku.smart_emobility_backend.common.web.ApiErrors;
import vn.edu.bku.smart_emobility_backend.dto.ApiErrorResponse;

@Component
public class SecurityErrorWriter {

    private static final Logger log = LoggerFactory.getLogger(SecurityErrorWriter.class);
    private final JsonMapper mapper;

    public SecurityErrorWriter(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletResponse response,
            ResponseEntity<ApiErrorResponse> error) throws IOException {
        response.setStatus(error.getStatusCode().value());
        error.getHeaders().forEach((name, values) ->
                values.forEach(value -> response.addHeader(name, value)));
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        if (error.getStatusCode().value() == 401) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        mapper.writeValue(response.getOutputStream(), error.getBody());
    }

    public void unexpectedFailure(HttpServletResponse response, RuntimeException exception)
            throws IOException {
        var error = ApiErrors.response(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR", "An unexpected server error occurred", List.of());
        log.error("Authentication storage error, requestId={}",
                error.getBody().error().requestId(), exception);
        write(response, error);
    }
}
