package com.reemamiri.practice.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {
    Optional<AdminUser> findByEmailIgnoreCaseAndActiveTrue(String email);
    boolean existsByEmailIgnoreCase(String email);
}
