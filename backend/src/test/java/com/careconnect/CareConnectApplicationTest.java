package com.careconnect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Toolchain check for the Phase 0 skeleton: proves that compilation and the
 * test runner work. A full application-context test is added in TASK-008
 * once the database and Flyway baseline exist.
 */
class CareConnectApplicationTest {

    @Test
    void applicationClassIsAnnotatedAndLivesInRootPackage() {
        assertThat(CareConnectApplication.class.isAnnotationPresent(SpringBootApplication.class)).isTrue();
        assertThat(CareConnectApplication.class.getPackageName()).isEqualTo("com.careconnect");
    }
}
