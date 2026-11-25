# UC-00 HTTP authentication, logout, authorization and CORS

## Current state
Commit 3af4263 provides account login, persisted hashed sessions and 32 tests. The only production HTTP controller is AccountController. HTTP requests currently have no security chain.

## Goal
Authenticate Bearer requests, expose GET /api/auth/me and POST /api/auth/logout, enforce route roles and session ownership, and serve CORS with a configurable origin allowlist.

## Non-goals
Password recovery, IoT and Student/Operator business endpoints follow in later commits.

## Design decisions
- Add Boot-managed spring-boot-starter-security for SecurityFilterChain, SecurityContext and authorization. Reuse crypto already present.
- Exclude default UserDetailsService auto-configuration because accounts and sessions are managed by the application.
- Opaque Bearer only. Stateless HTTP security with no cookie/session authentication, form login or Basic auth. Disable CSRF for this Authorization-header mechanism.
- CurrentAccount contains accountId, studentId, email, role and session hash. HTTP response uses a dedicated DTO without hash or credential.
- Reuse SessionService.authenticate for expiry/status/renewal and re-read Account role each request.
- Public auth paths continue to reach MVC for 405. Ignore Authorization on register/login so a stale token does not block login.
- Public reads follow contract: POST /api/search/hubs and GET /api/vehicles/*.
- /api/operator/** requires OPERATOR. Contract Student mutations require STUDENT. /api/operations/* requires authentication when implemented. Other routes deny by default.
- Existing 404 regression moves to a public /api/vehicles/* path. Unknown protected routes receive 401/403 before MVC.
- Logout deletes only a session owned by the authenticated account through coordinator. Repeated logout with revoked token returns 401. Logout failure returns error and preserves session.
- me response: accountId, studentId, email, role. Only identity from authenticated principal is returned.
- Role route tests use test-only controllers excluded from application scanning. Existing production ownership applies to me/logout. Ownership of future business resources must read the real resource owner in its service.
- Shared ApiErrors factory and session mapping for advice and filters. JSON 401/403/409/500, no-store, WWW-Authenticate for 401.
- CORS runs before Bearer authentication. Default development origin http://localhost:5173, configurable through APP_CORS_ALLOWED_ORIGINS. No credentials/cookies, Authorization and Idempotency-Key allowed. Reject disallowed preflights with common 403 envelope.
- Filter beans have servlet auto-registration disabled, so they execute once inside SecurityFilterChain.
- Runtime stays at Boot 4.1.2 declared by pom. Architecture runtime notes are synchronized for auth integration.

## Files affected
- pom.xml, src/main/resources/application.properties, SmartEmobilityBackendApplication.java.
- common/config/SecurityConfiguration.java.
- common/security/CurrentAccount.java, BearerSessionFilter.java, SecurityErrorWriter.java.
- common/web/ApiErrors.java, GlobalExceptionHandler.java.
- common/exception/AuthorizationException.java.
- service/SessionService.java, controller/AccountController.java, dto/CurrentAccountResponseDTO.java.
- src/test/java/vn/edu/bku/smart_emobility_backend/AuthSecurityIntegrationTest.java, AuthApiRegressionTest.java, SessionServiceTest.java.
- docs/00-product-context.md, docs/02-architecture.md, docs/03-api-contract.md, docs/04-data-schema.md, docs/06-test-plan.md.
- docs/plans/feature-uc00-http-security.md (this file), feature-uc00-auth-sessions.md.

## Implementation
1. Record contract, dependency and route policies.
2. Add security chain, shared errors and Bearer principal.
3. Add me/logout with service-owned session revocation.
4. Exercise real HTTP authentication, roles, CORS and persistence failure.
5. Run all tests and clean package on an isolated source copy, then review and commit this group.

## Acceptance criteria
- Public register/login and old error/response tests pass.
- Anonymous, malformed, expired, revoked and inactive sessions receive correct errors.
- me shows caller identity, no password/hash, and renews idle expiry.
- Logout revokes only caller session and persists deletion, including failure rollback.
- Student/Operator role checks use current Account, including role/status changes after login.
- CORS preflight succeeds without a token only for configured origins/methods/headers. Disallowed origin returns common 403.
- No JSESSIONID or cookie-based authentication, and no authentication leaking between requests.
- Route fixtures exist only in test scope.

## Verification
- mvnw.cmd test.
- mvnw.cmd clean package -DskipTests=false on an isolated source copy.
- Test reports, API/schema changes and final diff reviewed before commit.

## Verification results — 2026-11-24

- AuthSecurityIntegrationTest: 31 test invocations, all passed.
- Existing suite: 32 tests, all passed with the security chain enabled.
- Clean package with tests enabled: BUILD SUCCESS, 63 tests, 0 failures, 0 errors.
- Source copy and logs: C:/Users/minhq/AppData/Local/Temp/m3-http-security-27d90231f5014962869a41d794390ab7/.
- Source/test files compiled cleanly by javac in the isolated copy.
- Frontend browser integration and ownership of future business endpoints remain integration work for those modules.

### Final checks

- Standalone mvnw.cmd -o test also passed with 63 tests, 0 failures and 0 errors.
- Rollback assertions compare auth entity sets and version because ConcurrentHashMap iteration order can change after restore.
- Packaged JAR inspected: test-only ProbeController and AuthSecurityIntegrationTest are absent.
