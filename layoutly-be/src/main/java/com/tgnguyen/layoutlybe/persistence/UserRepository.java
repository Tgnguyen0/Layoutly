package com.tgnguyen.layoutlybe.persistence;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import java.time.Instant;
public interface UserRepository extends JpaRepository<UserEntity,UUID> {
Optional<UserEntity> findByEmailAndDeletedAtIsNull(String email);
}
