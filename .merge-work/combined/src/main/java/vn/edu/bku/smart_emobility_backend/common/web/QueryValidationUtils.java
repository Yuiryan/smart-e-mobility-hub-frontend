package vn.edu.bku.smart_emobility_backend.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException.FieldErrorDetail;

public final class QueryValidationUtils {
    private QueryValidationUtils() {
    }

    /**
     * Validates that only allowed query parameters are present, no parameter is repeated,
     * and no present parameter has an empty/blank value.
     */
    public static void validateQueryParams(HttpServletRequest request, Set<String> allowedKeys) {
        if (request == null) {
            return;
        }
        String rawQuery = request.getQueryString();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            for (String pair : rawQuery.split("&", -1)) {
                if (pair.isEmpty()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                            "Query string chứa tham số rỗng không hợp lệ");
                }
                int eqIdx = pair.indexOf('=');
                if (eqIdx <= 0 || eqIdx == pair.length() - 1) {
                    String key = eqIdx > 0 ? pair.substring(0, eqIdx) : pair;
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                            "Tham số query không được để trống",
                            java.util.List.of(new FieldErrorDetail(key, "Không được để trống")));
                }
            }
        }
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            if (!allowedKeys.contains(key)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Tham số query không được hỗ trợ: " + key,
                        java.util.List.of(new FieldErrorDetail(key, "Tham số không được phép")));
            }
            if (values == null || values.length != 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Tham số query bị trùng lặp: " + key,
                        java.util.List.of(new FieldErrorDetail(key, "Không được truyền trùng tham số")));
            }
            if (values[0] == null || values[0].isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Tham số query không được để trống: " + key,
                        java.util.List.of(new FieldErrorDetail(key, "Không được để trống")));
            }
        }
    }

    public static <E extends Enum<E>> E parseEnumParam(Class<E> enumType, String fieldName, String rawValue) {
        if (rawValue == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, rawValue);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Giá trị không hợp lệ cho " + fieldName + ": " + rawValue,
                    java.util.List.of(new FieldErrorDetail(fieldName, "Giá trị enum không hợp lệ")));
        }
    }
}
