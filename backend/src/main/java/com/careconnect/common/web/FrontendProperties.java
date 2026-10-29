package com.careconnect.common.web;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Where the web app is served from, {@code careconnect.frontend.origin} (FRONTEND_ORIGIN). */
@Component
@ConfigurationProperties(prefix = "careconnect.frontend")
@Validated
public class FrontendProperties {

    @NotBlank
    private String origin = "http://localhost:8080";

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }
}
