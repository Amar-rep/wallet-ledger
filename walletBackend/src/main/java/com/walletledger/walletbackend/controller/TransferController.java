package com.walletledger.walletbackend.controller;

import java.security.Principal;

import com.walletledger.walletbackend.dto.request.SendMoneyRequest;
import com.walletledger.walletbackend.dto.response.TransferResponse;
import com.walletledger.walletbackend.dto.response.UserSearchResponse;
import com.walletledger.walletbackend.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @GetMapping("/api/users/search")
    public UserSearchResponse search(Principal principal, @RequestParam String query) {
        return transferService.search(principal.getName(), query);
    }

    @PostMapping("/api/transfers")
    public TransferResponse sendMoney(Principal principal, @Valid @RequestBody SendMoneyRequest request) {
        return transferService.sendMoney(principal.getName(), request);
    }
}
