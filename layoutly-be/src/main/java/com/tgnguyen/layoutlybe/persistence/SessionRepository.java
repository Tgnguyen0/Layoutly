package com.tgnguyen.layoutlybe.persistence;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import java.time.Instant;
public interface SessionRepository extends JpaRepository<SessionEntity,UUID> {
List<SessionEntity> findByUserId(UUID userId);
Optional<SessionEntity> findByDigestAndKindAndExpiresAtAfter(String digest, String kind, Instant now);
List<SessionEntity> findByUserIdAndKindAndExpiresAtAfterOrderByCreatedAtAsc(UUID userId, String kind, Instant now);
void deleteByDigest(String digest);
void deleteByUserId(UUID userId);
}
