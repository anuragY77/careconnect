package com.careconnect.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class OriginCheckTest {

    private static OriginCheck check(String origin) {
        FrontendProperties properties = new FrontendProperties();
        properties.setOrigin(origin);
        return new OriginCheck(properties);
    }

    private static MockHttpServletRequest request(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }

    @Test
    void requestWithoutOriginIsAllowed() {
        assertThatCode(() -> check("http://localhost:8080").requireAllowed(request(null))).doesNotThrowAnyException();
    }

    @Test
    void ownOriginIsAllowed() {
        assertThatCode(() -> check("http://localhost:8080").requireAllowed(request("http://localhost:8080")))
            .doesNotThrowAnyException();
    }

    @Test
    void comparisonIgnoresCaseAndTrailingSlashInTheConfiguration() {
        assertThatCode(() -> check("https://App.Example.org/").requireAllowed(request("https://app.example.org")))
            .doesNotThrowAnyException();
    }

    @Test
    void defaultPortIsTreatedAsNoPort() {
        assertThatCode(() -> check("https://app.example.org:443").requireAllowed(request("https://app.example.org")))
            .doesNotThrowAnyException();
    }

    @Test
    void foreignOriginIsRejectedWith403() {
        assertThatThrownBy(() -> check("http://localhost:8080").requireAllowed(request("https://evil.example")))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void sameHostOnAnotherPortOrSchemeIsRejected() {
        OriginCheck check = check("http://localhost:8080");

        assertThatThrownBy(() -> check.requireAllowed(request("http://localhost:9999"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> check.requireAllowed(request("https://localhost:8080"))).isInstanceOf(ApiException.class);
    }

    @Test
    void nullOriginStringIsRejected() {
        assertThatThrownBy(() -> check("http://localhost:8080").requireAllowed(request("null")))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void badConfigurationFailsAtStartup() {
        assertThatThrownBy(() -> check("localhost:8080")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> check("ftp://example.org")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> check("https://example.org/app")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> check(" ")).isInstanceOf(IllegalStateException.class);
    }
}
