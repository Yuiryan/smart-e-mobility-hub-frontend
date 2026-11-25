package vn.edu.bku.smart_emobility_backend.common.store;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException.FieldErrorDetail;

@Component
public class IdempotencyStore {
    private static final Duration RETENTION_TTL = Duration.ofMinutes(30);

    private final Map<String, StoredRecord> records = new ConcurrentHashMap<>();
    private final Clock clock;
    private final JsonMapper jsonMapper;

    public IdempotencyStore(Clock clock, JsonMapper jsonMapper) {
        this.clock = clock;
        this.jsonMapper = jsonMapper;
    }

    public static String requireValidUuidHeader(String rawHeader) {
        if (rawHeader == null || rawHeader.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Thiếu header Idempotency-Key bắt buộc",
                    List.of(new FieldErrorDetail("Idempotency-Key", "Header Idempotency-Key là bắt buộc")));
        }
        String trimmed = rawHeader.trim();
        try {
            UUID.fromString(trimmed);
            return trimmed;
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Idempotency-Key phải là UUID hợp lệ",
                    List.of(new FieldErrorDetail("Idempotency-Key", "Định dạng UUID không hợp lệ")));
        }
    }

    public String canonicalizePayload(Object payload) {
        if (payload == null) {
            return "{}";
        }
        Object normalized = normalizeObject(payload);
        try {
            return jsonMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return String.valueOf(payload);
        }
    }

    @SuppressWarnings("unchecked")
    private Object normalizeObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), normalizeObject(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::normalizeObject).toList();
        }
        try {
            Map<String, Object> asMap = jsonMapper.convertValue(value, Map.class);
            if (asMap != null) {
                TreeMap<String, Object> sorted = new TreeMap<>();
                for (Map.Entry<String, Object> entry : asMap.entrySet()) {
                    sorted.put(entry.getKey(), normalizeObject(entry.getValue()));
                }
                return sorted;
            }
        } catch (Exception ignored) {
        }
        return value;
    }

    public synchronized Optional<CachedResponse> checkReplay(
            String accountId,
            String method,
            String path,
            String idempotencyKey,
            String canonicalPayload) {
        purgeExpired();
        String scopeKey = buildScopeKey(accountId, method, path, idempotencyKey);
        StoredRecord existing = records.get(scopeKey);
        if (existing == null) {
            return Optional.empty();
        }
        if (!existing.canonicalPayload().equals(canonicalPayload)) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key đã được sử dụng với payload khác",
                    List.of(new FieldErrorDetail("Idempotency-Key", "Khác payload so với request ban đầu")));
        }
        return Optional.of(new CachedResponse(existing.httpStatus(), existing.responseBody()));
    }

    public synchronized void recordAccepted(
            String accountId,
            String method,
            String path,
            String idempotencyKey,
            String canonicalPayload,
            HttpStatus httpStatus,
            Object responseBody) {
        purgeExpired();
        String scopeKey = buildScopeKey(accountId, method, path, idempotencyKey);
        Instant now = clock.instant();
        records.put(scopeKey, new StoredRecord(
                canonicalPayload,
                httpStatus,
                responseBody,
                now.plus(RETENTION_TTL)));
    }

    public synchronized void clear() {
        records.clear();
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        records.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(now));
    }

    private String buildScopeKey(String accountId, String method, String path, String key) {
        return accountId + "|" + method.toUpperCase() + "|" + path + "|" + key;
    }

    public record CachedResponse(HttpStatus status, Object body) {
    }

    private record StoredRecord(
            String canonicalPayload,
            HttpStatus httpStatus,
            Object responseBody,
            Instant expiresAt) {
    }
}
