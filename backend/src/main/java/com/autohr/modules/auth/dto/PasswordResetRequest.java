package com.autohr.modules.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetRequest {
    private String mobilePhone;
    private String email;

    @NotBlank(message = "verification code is required")
    @Pattern(regexp = "\\d{6}", message = "verification code must contain 6 digits")
    private String verificationCode;

    @NotBlank(message = "new password is required")
    @Size(min = 8, message = "new password must contain at least 8 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "new password must contain both letters and numbers")
    private String newPassword;

    @AssertTrue(message = "provide exactly one of mobilePhone or email")
    public boolean hasExactlyOneContact() {
        return (mobilePhone != null && !mobilePhone.isBlank()) ^ (email != null && !email.isBlank());
    }
}
