package com.varshith.ratelimiter.repository;

import com.varshith.ratelimiter.model.BackendLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BackendLinkRepository extends JpaRepository<BackendLink, UUID> {
    List<BackendLink> findByUserId(UUID userId);
    Optional<BackendLink> findByBackendUrlAndUserId(String backendUrl, UUID userId);
    Optional<BackendLink> findByVerificationToken(String token);
    List<BackendLink> findByUserIdAndIsVerifiedTrue(UUID userId);
    Optional<BackendLink> findByIdAndUserId(UUID id, UUID userId);
}
