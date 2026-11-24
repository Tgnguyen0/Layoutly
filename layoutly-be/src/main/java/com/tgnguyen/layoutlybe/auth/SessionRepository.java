package com.tgnguyen.layoutlybe.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<UserSession, UUID> {

    @Query("select s from UserSession s join fetch s.user where s.tokenHash = :hash and s.expiresAt > :now")
    Optional<UserSession> findActive(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query("delete from UserSession s where s.tokenHash = :hash")
    int deleteByHash(@Param("hash") String hash);

    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
