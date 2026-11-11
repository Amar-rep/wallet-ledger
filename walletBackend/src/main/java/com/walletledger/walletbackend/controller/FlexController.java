package com.walletledger.walletbackend.controller;

import java.security.Principal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import com.walletledger.walletbackend.dto.response.FlexCreditResponse;
import com.walletledger.walletbackend.entity.FlexCredit;
import com.walletledger.walletbackend.entity.Wallet;
import com.walletledger.walletbackend.exception.BusinessRuleException;
import com.walletledger.walletbackend.repository.FlexCreditRepository;
import com.walletledger.walletbackend.repository.UserRepository;
import com.walletledger.walletbackend.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flex")
@RequiredArgsConstructor
public class FlexController {

        private final UserRepository userRepository;
        private final WalletRepository walletRepository;
        private final FlexCreditRepository flexCreditRepository;

        /** All Flex credits where the current user is the beneficiary, newest first. */
        @GetMapping("/my-credits")
        public List<FlexCreditResponse> myCredits(Principal principal) {
                Long userId = userRepository.findByEmail(principal.getName())
                                .orElseThrow(() -> new BusinessRuleException(HttpStatus.UNAUTHORIZED, "User not found"))
                                .getId();
                Wallet wallet = walletRepository.findByUserId(userId)
                                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND, "Wallet not found"));

                return flexCreditRepository
                                .findByBeneficiaryWalletIdOrderByDisbursedAtDesc(wallet.getId())
                                .stream()
                                .map(this::toResponse)
                                .toList();
        }

        private FlexCreditResponse toResponse(FlexCredit fc) {
                return new FlexCreditResponse(
                                fc.getId(),
                                fc.getTransaction().getId(),
                                fc.getAmount(),
                                fc.getAmountRecovered(),
                                fc.getAmountOutstanding(),
                                fc.getStatus(),
                                fc.getBeneficiaryWallet().getUser() != null
                                                ? fc.getBeneficiaryWallet().getUser().getName()
                                                : "—",
                                toInstant(fc.getDisbursedAt()),
                                toInstant(fc.getSettledAt()),
                                toInstant(fc.getRecoveredAt()),
                                toInstant(fc.getWrittenOffAt()),
                                fc.getWriteOffReason(),
                                fc.getWrittenOffBy());
        }

        private Instant toInstant(OffsetDateTime v) {
                return v == null ? null : v.toInstant();
        }
}
