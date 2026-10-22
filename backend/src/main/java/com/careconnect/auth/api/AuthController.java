package com.careconnect.auth.api;

import com.careconnect.auth.service.AuthenticatedUser;
import com.careconnect.auth.service.LoginService;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** One message for every failure, so the response never says which part was wrong. */
    static final String LOGIN_FAILED = "Invalid username or password.";

    private final LoginService loginService;

    public AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticatedUser user = loginService.login(request.username(), request.password())
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, LOGIN_FAILED));
        return new LoginResponse(user.userId(), user.username(), user.fullName(), user.role(),
            user.mustChangePassword());
    }
}
