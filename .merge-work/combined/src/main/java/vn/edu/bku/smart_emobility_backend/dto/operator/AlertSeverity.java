package vn.edu.bku.smart_emobility_backend.dto.operator;

public enum AlertSeverity {
    NORMAL,
    WARNING,
    CRITICAL,
    MISSING_DATA;

    public static AlertSeverity combine(AlertSeverity a, AlertSeverity b) {
        if (a == MISSING_DATA || b == MISSING_DATA) {
            return MISSING_DATA;
        }
        if (a == CRITICAL || b == CRITICAL) {
            return CRITICAL;
        }
        if (a == WARNING || b == WARNING) {
            return WARNING;
        }
        return NORMAL;
    }
}
