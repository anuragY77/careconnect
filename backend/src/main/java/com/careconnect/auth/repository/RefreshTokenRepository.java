package com.careconnect.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.careconnect.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for refresh tokens. Only the auth module's services use it (ArchUnit enforces this). */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Loads the token and locks its row until the transaction ends. Two requests that present the
     * same token at the same moment are therefore handled one after the other: the second one sees
     * the token as already used. Without the lock both could succeed and the token would be copied.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findWithLockByTokenHash(String tokenHash);

    List<RefreshToken> findByFamilyId(UUID familyId);

    /** Revokes every token of the family that is not revoked yet. Returns how many were changed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    /** Deletes every token that expired before the cutoff. Returns how many were deleted. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int purgeExpiredBefore(@Param("cutoff") Instant cutoff);
}
