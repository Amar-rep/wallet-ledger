package com.walletledger.walletbackend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.walletledger.walletbackend.dto.enums.UserRole;
import com.walletledger.walletbackend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    List<User> findByRole(UserRole role);

    List<User> findByLockedAtLessThanEqual(OffsetDateTime cutoff);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(OffsetDateTime from, OffsetDateTime to);
}
