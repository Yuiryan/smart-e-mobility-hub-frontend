package vn.edu.bku.smart_emobility_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import vn.edu.bku.smart_emobility_backend.common.domain.PasswordPolicy;

/** Input for student self-registration. The server assigns the STUDENT role. */
public record RegisterStudentRequestDTO(
        @NotBlank(message = "fullName is required") String fullName,
        @NotBlank(message = "studentId is required") String studentId,
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid") String email,
        @NotBlank(message = "password is required")
        @Size(
                min = PasswordPolicy.MIN_LENGTH,
                message = "password must contain at least 8 characters")
        String password) {

    @Override
    public String toString() {
        return "RegisterStudentRequestDTO[password=REDACTED]";
    }
}
