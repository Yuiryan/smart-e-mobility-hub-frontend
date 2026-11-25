package vn.edu.bku.smart_emobility_backend.service.operator;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException.FieldErrorDetail;
import vn.edu.bku.smart_emobility_backend.common.error.GlobalExceptionHandler;
import vn.edu.bku.smart_emobility_backend.dto.operator.AuditLogDTO;
import vn.edu.bku.smart_emobility_backend.dto.operator.AuditLogDTO.AuditLogListResponseDTO;
import vn.edu.bku.smart_emobility_backend.dto.operator.AuditOutcome;
import vn.edu.bku.smart_emobility_backend.dto.operator.ResourceType;

@Service
public class AuditLogService {
    private static final int MAX_AUDIT_ITEMS = 200;

    private final Map<String, AuditEntry> entries = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);
    private final Clock clock;

    public AuditLogService(Clock clock) {
        this.clock = clock;
    }

    public synchronized AuditEntry record(
            String actorId,
            ResourceType resourceType,
            String resourceId,
            String action,
            String reason,
            String operationId,
            AuditOutcome outcome,
            String errorCode) {
        long seq = sequence.getAndIncrement();
        String auditId = String.format("audit-%04d", seq);
        Instant now = clock.instant();
        AuditEntry entry = new AuditEntry(
                auditId,
                seq,
                actorId,
                resourceType,
                resourceId,
                action,
                reason,
                now,
                now,
                operationId,
                outcome,
                errorCode);
        entries.put(auditId, entry);
        return entry;
    }

    public synchronized void updateOutcome(String auditId, AuditOutcome outcome, String errorCode) {
        if (auditId == null) {
            return;
        }
        AuditEntry existing = entries.get(auditId);
        if (existing == null) {
            return;
        }
        Instant now = clock.instant();
        entries.put(auditId, new AuditEntry(
                existing.auditId(),
                existing.sequenceOrder(),
                existing.actorId(),
                existing.resourceType(),
                existing.resourceId(),
                existing.action(),
                existing.reason(),
                existing.createdAt(),
                now,
                existing.operationId(),
                outcome,
                errorCode));
    }

    public synchronized AuditLogListResponseDTO listAuditLogs(
            ResourceType resourceType,
            String resourceId,
            String operationId,
            AuditOutcome outcome) {
        if (resourceId != null && resourceType == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Khi lọc theo resourceId phải cung cấp cả resourceType",
                    List.of(new FieldErrorDetail("resourceType", "Bắt buộc khi có resourceId")));
        }

        List<AuditEntry> filtered = entries.values().stream()
                .filter(e -> resourceType == null || resourceType == e.resourceType())
                .filter(e -> resourceId == null || resourceId.equals(e.resourceId()))
                .filter(e -> operationId == null || operationId.equals(e.operationId()))
                .filter(e -> outcome == null || outcome == e.outcome())
                .sorted(Comparator.comparing(AuditEntry::createdAt).reversed()
                        .thenComparing(Comparator.comparingLong(AuditEntry::sequenceOrder).reversed())
                        .thenComparing(Comparator.comparing(AuditEntry::auditId).reversed()))
                .toList();

        boolean truncated = filtered.size() > MAX_AUDIT_ITEMS;
        List<AuditLogDTO> items = filtered.stream()
                .limit(MAX_AUDIT_ITEMS)
                .map(this::toDto)
                .toList();

        return new AuditLogListResponseDTO(items, truncated);
    }

    public synchronized void clear() {
        entries.clear();
        sequence.set(1);
    }

    private AuditLogDTO toDto(AuditEntry entry) {
        return new AuditLogDTO(
                entry.auditId(),
                entry.actorId(),
                entry.resourceType(),
                entry.resourceId(),
                entry.action(),
                entry.reason(),
                GlobalExceptionHandler.formatTimestamp(entry.createdAt()),
                GlobalExceptionHandler.formatTimestamp(entry.updatedAt()),
                entry.operationId(),
                entry.outcome(),
                entry.errorCode());
    }

    public record AuditEntry(
            String auditId,
            long sequenceOrder,
            String actorId,
            ResourceType resourceType,
            String resourceId,
            String action,
            String reason,
            Instant createdAt,
            Instant updatedAt,
            String operationId,
            AuditOutcome outcome,
            String errorCode) {
    }
}
