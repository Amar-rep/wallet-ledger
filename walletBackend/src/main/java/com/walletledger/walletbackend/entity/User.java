package com.walletledger.walletbackend.entity;

import java.time.OffsetDateTime;

import com.walletledger.walletbackend.dto.enums.UserRole;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String phone;
    private String passwordHash;
    private String pinHash;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    private Integer failedLoginCount;
    private OffsetDateTime lockedAt;
    private OffsetDateTime createdAt;
}
