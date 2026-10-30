package com.walletledger.walletbackend.repository;

import com.walletledger.walletbackend.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
}
