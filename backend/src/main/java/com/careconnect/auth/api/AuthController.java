package com.careconnect.auth.api;

import java.util.Optional;

import com.careconnect.auth.service.AuthenticatedUser;
import com.careconnect.auth.service.LoginService;
import com.careconnect.auth.service.LoginThrottle;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
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
    private final LoginThrottle loginThrottle;

    public AuthController(LoginService loginService, LoginThrottle loginThrottle) {
        this.loginService = loginService;
        this.loginThrottle = loginThrottle;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // Behind nginx the container sees the real client address here; the proxy chain is
        // resolved by the server (server.forward-headers-strategy) and never read by hand.
        String clientIp = http.getRemoteAddr();
        loginThrottle.checkAllowed(clientIp);

        Optional<AuthenticatedUser> user = loginService.login(request.username(), request.password());
        if (user.isEmpty()) {
            loginThrottle.recordFailure(clientIp);
            throw new ApiException(ErrorCode.UNAUTHENTICATED, LOGIN_FAILED);
        }
        AuthenticatedUser authenticated = user.get();
        return new LoginResponse(authenticated.userId(), authenticated.username(), authenticated.fullName(),
            authenticated.role(), authenticated.mustChangePassword());
    }
}
