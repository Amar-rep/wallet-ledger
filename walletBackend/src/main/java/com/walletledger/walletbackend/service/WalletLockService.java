package com.walletledger.walletbackend.service;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Acquires pessimistic write-locks on a set of wallets in ascending ID order.
 * Always locking in the same order prevents deadlocks between opposing
 * transfers
 * even when the system wallet is one of the participants.
 */
@Service
@RequiredArgsConstructor
public class WalletLockService {

    private final WalletRepository walletRepository;

    public Map<Long, Wallet> lockInOrder(Long... walletIds) {
        Map<Long, Wallet> result = new TreeMap<>();
        for (Long id : new TreeSet<>(Arrays.asList(walletIds))) {
            result.put(id, walletRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND,
                            "Wallet " + id + " not found")));
        }
        return result;
    }
}
