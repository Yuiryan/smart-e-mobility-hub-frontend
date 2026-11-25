package vn.edu.bku.smart_emobility_backend.common.security;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import vn.edu.bku.smart_emobility_backend.common.exception.SessionException;
import vn.edu.bku.smart_emobility_backend.common.web.ApiErrors;
import vn.edu.bku.smart_emobility_backend.service.SessionService;

@Component
public class BearerSessionFilter extends OncePerRequestFilter {

    private final SessionService sessions;
    private final SessionTokenService tokens;
    private final SecurityErrorWriter errors;

    public BearerSessionFilter(ObjectProvider<SessionService> sessions,
            ObjectProvider<SessionTokenService> tokens,
            ObjectProvider<SecurityErrorWriter> errors) {
        this.sessions = sessions.getIfAvailable();
        this.tokens = tokens.getIfAvailable();
        this.errors = errors.getIfAvailable();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (sessions == null || tokens == null || errors == null) {
            return true;
        }
        String path = request.getServletPath();
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/password/recovery/request")
                || path.equals("/api/auth/password/recovery/verify")
                || path.equals("/api/auth/password/recovery/reset");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String header = headers.getFirst();
            if (headers.size() != 1
                    || !header.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
                throw new SessionException(SessionException.Reason.INVALID_SESSION,
                        "Invalid Authorization header");
            }

            String token = header.substring("Bearer ".length());
            var account = sessions.authenticate(token);
            if (account.role() == null) {
                throw new SessionException(SessionException.Reason.ACCOUNT_INACTIVE,
                        "Account role is missing");
            }
            var principal = new CurrentAccount(
                    account.id(), account.studentId(), account.email(),
                    account.role(), tokens.hashToken(token));
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name())));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (SessionException exception) {
            SecurityContextHolder.clearContext();
            errors.write(response, ApiErrors.sessionFailure(exception));
            return;
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            errors.unexpectedFailure(response, exception);
            return;
        }

        chain.doFilter(request, response);
    }
}
