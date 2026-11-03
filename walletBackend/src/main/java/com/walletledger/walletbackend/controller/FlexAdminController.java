package com.walletledger.walletbackend.controller;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;

import com.walletledger.walletbackend.dto.common.MessageResponse;
import com.walletledger.walletbackend.dto.enums.FlexCreditStatus;
import com.walletledger.walletbackend.dto.request.FlexWriteOffRequest;
import com.walletledger.walletbackend.dto.response.FlexCreditResponse;
import com.walletledger.walletbackend.entity.FlexCredit;
import com.walletledger.walletbackend.service.FlexService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/flex")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class FlexAdminController {

    private final FlexService flexService;

    /** List all Flex credits, optionally filtered by status. */
    @GetMapping
    public List<FlexCreditResponse> list(
            @RequestParam(required = false) FlexCreditStatus status) {
        return flexService.list(status).stream().map(this::toResponse).toList();
    }

    /** Write off an unrecoverable RECOVERING Flex credit. */
    @PostMapping("/{id}/write-off")
    public MessageResponse writeOff(Principal principal,
            @PathVariable Long id,
            @Valid @RequestBody FlexWriteOffRequest request) {
        flexService.writeOff(id, principal.getName(), request.reason());
        return new MessageResponse("Flex credit #" + id + " written off.");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private FlexCreditResponse toResponse(FlexCredit fc) {
        String beneficiaryName = fc.getBeneficiaryWallet().getUser() != null
                ? fc.getBeneficiaryWallet().getUser().getName() : "—";
        return new FlexCreditResponse(
                fc.getId(),
                fc.getTransaction().getId(),
                fc.getAmount(),
                fc.getAmountRecovered(),
                fc.getAmountOutstanding(),
                fc.getStatus(),
                beneficiaryName,
                toInstant(fc.getDisbursedAt()),
                toInstant(fc.getSettledAt()),
                toInstant(fc.getRecoveredAt()),
                toInstant(fc.getWrittenOffAt()),
                fc.getWriteOffReason(),
                fc.getWrittenOffBy());
    }

    private java.time.Instant toInstant(OffsetDateTime v) {
        return v == null ? null : v.toInstant();
    }
}
