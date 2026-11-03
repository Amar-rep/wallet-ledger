package com.walletledger.walletbackend.controller;

import java.security.Principal;

import com.walletledger.walletbackend.dto.common.MessageResponse;
import com.walletledger.walletbackend.dto.request.AddMoneyRequest;
import com.walletledger.walletbackend.dto.request.WalletStatusRequest;
import com.walletledger.walletbackend.dto.response.WalletResponse;
import com.walletledger.walletbackend.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public WalletResponse getWallet(Principal principal) {
        return walletService.getWallet(principal.getName());
    }

    @PostMapping("/add-money")
    public WalletResponse addMoney(Principal principal, @Valid @RequestBody AddMoneyRequest request) {
        return walletService.addMoney(principal.getName(), request);
    }

    @PatchMapping("/status")
    public WalletResponse updateStatus(Principal principal, @Valid @RequestBody WalletStatusRequest request) {
        return walletService.setOwnStatus(principal.getName(), request.status());
    }

    /** Toggle Flex Instant Credit opt-in for the current user's wallet. */
    @PostMapping("/flex/toggle")
    public MessageResponse toggleFlex(Principal principal) {
        return walletService.toggleFlex(principal.getName());
    }
}
