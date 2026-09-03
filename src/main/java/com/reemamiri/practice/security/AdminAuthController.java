package com.reemamiri.practice.security;

import com.reemamiri.practice.security.dto.ChangePasswordRequest;
import com.reemamiri.practice.security.dto.LoginRequest;
import com.reemamiri.practice.security.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin authentication")
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService authService;

    @Operation(summary = "Change the signed-in operator's password")
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @Valid @RequestBody ChangePasswordRequest request, Principal principal) {
        authService.changePassword(
                principal.getName(), request.currentPassword(), request.newPassword());
    }

    @Operation(summary = "Log in and receive an access token")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        var result = authService.login(request.email(), request.password());
        return new LoginResponse(
                result.accessToken(), "Bearer", result.expiresInSeconds(), result.displayName());
    }
}
