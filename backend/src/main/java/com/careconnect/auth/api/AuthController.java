package com.careconnect.auth.api;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import com.careconnect.auth.service.AuthenticatedUser;
import com.careconnect.auth.service.CurrentUserService;
import com.careconnect.auth.service.IssuedSession;
import com.careconnect.auth.service.LoginService;
import com.careconnect.auth.service.LoginThrottle;
import com.careconnect.auth.service.TokenService;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;
import com.careconnect.common.web.OriginCheck;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign in, renew and sign out. The access token is returned in the body; the refresh token only
 * in an HttpOnly cookie. Login, refresh and logout check the request origin (see {@link OriginCheck}).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** One message for every failure, so the response never says which part was wrong. */
    static final String LOGIN_FAILED = "Invalid username or password.";

    /** One message for every refresh failure: unknown, expired, reused or deactivated. */
    static final String SESSION_ENDED = "Your session has ended. Sign in again.";

    private final LoginService loginService;
    private final LoginThrottle loginThrottle;
    private final TokenService tokenService;
    private final CurrentUserService currentUserService;
    private final OriginCheck originCheck;
    private final Clock clock;

    public AuthController(LoginService loginService, LoginThrottle loginThrottle, TokenService tokenService,
                          CurrentUserService currentUserService, OriginCheck originCheck, Clock clock) {
        this.loginService = loginService;
        this.loginThrottle = loginThrottle;
        this.tokenService = tokenService;
        this.currentUserService = currentUserService;
        this.originCheck = originCheck;
        this.clock = clock;
    }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
                                 HttpServletResponse response) {
        originCheck.requireAllowed(http);

        // Behind nginx the container sees the real client address here; the proxy chain is
        // resolved by the server (server.forward-headers-strategy) and never read by hand.
        String clientIp = http.getRemoteAddr();
        loginThrottle.checkAllowed(clientIp);

        Optional<AuthenticatedUser> user = loginService.login(request.username(), request.password());
        if (user.isEmpty()) {
            loginThrottle.recordFailure(clientIp);
            throw new ApiException(ErrorCode.UNAUTHENTICATED, LOGIN_FAILED);
        }
        return send(response, tokenService.startSession(user.get()));
    }

    @PostMapping("/refresh")
    public SessionResponse refresh(@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
                                   HttpServletRequest http, HttpServletResponse response) {
        originCheck.requireAllowed(http);

        Optional<IssuedSession> session = tokenService.rotate(refreshToken);
        if (session.isEmpty()) {
            // Remove the cookie that no longer works, so the browser stops sending it.
            response.addHeader(HttpHeaders.SET_COOKIE, RefreshCookies.clear().toString());
            throw new ApiException(ErrorCode.UNAUTHENTICATED, SESSION_ENDED);
        }
        return send(response, session.get());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
                       HttpServletRequest http, HttpServletResponse response) {
        originCheck.requireAllowed(http);

        tokenService.endSession(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, RefreshCookies.clear().toString());
    }

    /** The signed-in user, read fresh from the database. Needs a valid access token. */
    @GetMapping("/me")
    public SessionResponse.UserSummary me(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = currentUserService.findActive(jwt.getSubject())
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, SESSION_ENDED));
        return new SessionResponse.UserSummary(user.userId(), user.username(), user.fullName(), user.role(),
            user.mustChangePassword());
    }

    /** Sets the refresh cookie and the no-store header, and builds the body. */
    private SessionResponse send(HttpServletResponse response, IssuedSession session) {
        Duration maxAge = Duration.between(clock.instant(), session.refreshExpiresAt());
        response.addHeader(HttpHeaders.SET_COOKIE,
            RefreshCookies.issue(session.refreshToken(), maxAge).toString());
        // Responses that carry tokens must never be stored by a browser or a proxy.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return SessionResponse.from(session);
    }
}
