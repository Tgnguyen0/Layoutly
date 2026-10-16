package com.tgnguyen.layoutlybe.persistence;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import java.time.Instant;
public interface ResetTokenRepository extends JpaRepository<ResetTokenEntity,UUID> {
@Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
Optional<ResetTokenEntity> findByDigest(String digest);
}
