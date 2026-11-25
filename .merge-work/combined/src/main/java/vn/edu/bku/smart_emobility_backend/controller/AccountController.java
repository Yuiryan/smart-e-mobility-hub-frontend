package vn.edu.bku.smart_emobility_backend.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.bku.smart_emobility_backend.dto.RecoveryGrantDTO;
import vn.edu.bku.smart_emobility_backend.dto.RecoveryResetRequestDTO;
import vn.edu.bku.smart_emobility_backend.dto.RecoveryVerifyRequestDTO;
import vn.edu.bku.smart_emobility_backend.common.domain.Account;
import vn.edu.bku.smart_emobility_backend.common.security.CurrentAccount;
import vn.edu.bku.smart_emobility_backend.dto.ChangePasswordRequestDTO;
import vn.edu.bku.smart_emobility_backend.dto.CurrentAccountResponseDTO;
import vn.edu.bku.smart_emobility_backend.dto.LoginRequestDTO;
import vn.edu.bku.smart_emobility_backend.dto.LoginResponseDTO;
import vn.edu.bku.smart_emobility_backend.dto.RecoveryRequestDTO;
import vn.edu.bku.smart_emobility_backend.dto.RegisterStudentRequestDTO;
import vn.edu.bku.smart_emobility_backend.dto.RegisterStudentResponseDTO;
import vn.edu.bku.smart_emobility_backend.service.AuthService;
import vn.edu.bku.smart_emobility_backend.service.SessionService;

@RestController
@RequestMapping("/api/auth")
public class AccountController {

        private final AuthService authService;
        private final SessionService sessionService;

        public AccountController(AuthService authService, SessionService sessionService) {
                this.authService = authService;
                this.sessionService = sessionService;
        }

        @PostMapping("/register")
        public ResponseEntity<RegisterStudentResponseDTO> reg(
                        @Valid @RequestBody RegisterStudentRequestDTO request) {

                Account account = authService.reg(request);

                RegisterStudentResponseDTO response = new RegisterStudentResponseDTO(
                                account.id(),
                                account.studentId(),
                                account.email(),
                                account.role(),
                                account.status(),
                                account.createdAt());

                return ResponseEntity
                                .status(HttpStatus.CREATED)
                                .body(response);
        }

        @PostMapping("/login")
        public ResponseEntity<LoginResponseDTO> login(
                        @Valid @RequestBody LoginRequestDTO request) {

                LoginResponseDTO response = authService.login(request);

                return ResponseEntity
                                .ok()
                                .header("Cache-Control", "no-store")
                                .body(response);
        }

        @GetMapping("/me")
        public ResponseEntity<CurrentAccountResponseDTO> me(
                        @AuthenticationPrincipal CurrentAccount account) {
                return ResponseEntity.ok()
                                .header("Cache-Control", "no-store")
                                .body(new CurrentAccountResponseDTO(
                                                account.accountId(), account.studentId(), account.email(),
                                                account.role()));
        }

        @PostMapping("/logout")
        public ResponseEntity<Void> logout(@AuthenticationPrincipal CurrentAccount account) {
                sessionService.logout(account.accountId(), account.sessionId());
                return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
        }

        @PostMapping("/password/change")
        public ResponseEntity<Void> changePassword(
                        @AuthenticationPrincipal CurrentAccount account,
                        @Valid @RequestBody ChangePasswordRequestDTO request) {

                authService.changePassword(
                                account.accountId(), account.sessionId(), request);

                return ResponseEntity.noContent()
                                .header("Cache-Control", "no-store")
                                .build();
        }

        @PostMapping("/password/recovery/request")
        public ResponseEntity<Void> requestRecovery(
                        @Valid @RequestBody RecoveryRequestDTO request) {
                authService.sendRecovery(request.email());
                return ResponseEntity.accepted()
                                .header("Cache-Control", "no-store")
                                .build();
        }

        @PostMapping("/password/recovery/verify")
        public ResponseEntity<RecoveryGrantDTO> verifyCode(@Valid @RequestBody RecoveryVerifyRequestDTO request) {
                return ResponseEntity.ok().header("Cache-Control", "no-store").body(authService.verifyCode(request));
        }

        @PostMapping("/password/recovery/reset")
        public ResponseEntity<Void> resetPassword(@Valid @RequestBody RecoveryResetRequestDTO request) {
                authService.updatePass(request);
                return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
        }
}
