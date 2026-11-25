package vn.edu.bku.smart_emobility_backend.common.security;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.bku.smart_emobility_backend.common.domain.Account;
import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;
import vn.edu.bku.smart_emobility_backend.common.domain.Session;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;

@Component
public class OperatorAccessGuard {
    private final InMemoryDataStore dataStore;
    private final Clock clock;

    public OperatorAccessGuard(InMemoryDataStore dataStore, Clock clock) {
        this.dataStore = dataStore;
        this.clock = clock;
    }

    public AuthenticatedActor requireAuthenticated(HttpServletRequest request) {
        String authHeader = request != null ? request.getHeader(HttpHeaders.AUTHORIZATION) : null;
        return requireAuthenticated(authHeader);
    }

    public AuthenticatedActor requireAuthenticated(String authHeader) {
        String token = extractBearerToken(authHeader);
        Instant now = clock.instant();
        Session session = findSessionByToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Token không hợp lệ hoặc đã hết hạn"));

        if (session.expiresAt() == null || !session.expiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Phiên đăng nhập đã hết hạn");
        }

        Account account = dataStore.findById(Account.class, session.accountId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Tài khoản của phiên không tồn tại"));

        if (!account.active()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED",
                    "Tài khoản đang bị tạm khóa hoặc chưa kích hoạt");
        }

        boolean isDemo = account.id() != null && account.id().startsWith("demo-");
        return new AuthenticatedActor(account.id(), account.role(), isDemo, session.id(), session.expiresAt());
    }

    public AuthenticatedActor requireOperator(HttpServletRequest request) {
        String authHeader = request != null ? request.getHeader(HttpHeaders.AUTHORIZATION) : null;
        return requireOperator(authHeader);
    }

    public AuthenticatedActor requireOperator(String authHeader) {
        AuthenticatedActor actor = requireAuthenticated(authHeader);
        if (!actor.hasOperatorCapability()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Tài khoản không có quyền thực hiện thao tác vận hành (OPERATOR)");
        }
        return actor;
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Thiếu header Authorization Bearer");
        }
        String prefix = "Bearer ";
        if (!authHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Định dạng Authorization phải là Bearer <token>");
        }
        String token = authHeader.substring(prefix.length()).trim();
        if (token.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token không được để trống");
        }
        return token;
    }

    private Optional<Session> findSessionByToken(String rawToken) {
        Optional<Session> direct = dataStore.findById(Session.class, rawToken);
        if (direct.isPresent()) {
            return direct;
        }
        String hashed = sha256Hex(rawToken);
        Optional<Session> byHash = dataStore.findById(Session.class, hashed);
        if (byHash.isPresent()) {
            return byHash;
        }
        List<Session> allSessions = dataStore.findAll(Session.class);
        return allSessions.stream()
                .filter(s -> rawToken.equals(s.id()) || hashed.equals(s.id()))
                .findFirst();
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public record AuthenticatedActor(
            String accountId,
            AccountRole role,
            boolean demoMode,
            String sessionId,
            Instant expiresAt) {
        public boolean hasOperatorCapability() {
            return role == AccountRole.OPERATOR || demoMode;
        }
    }
}
