package com.walletledger.walletbackend.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import com.walletledger.walletbackend.dto.common.MessageResponse;
import com.walletledger.walletbackend.dto.common.PageResponse;
import com.walletledger.walletbackend.dto.enums.FraudAlertStatus;
import com.walletledger.walletbackend.dto.request.AdminTransactionFilter;
import com.walletledger.walletbackend.dto.request.FraudReviewRequest;
import com.walletledger.walletbackend.dto.request.WalletFreezeRequest;
import com.walletledger.walletbackend.dto.response.AdminTransactionDetailResponse;
import com.walletledger.walletbackend.dto.response.AdminTransactionResponse;
import com.walletledger.walletbackend.dto.response.AdminUserDetailResponse;
import com.walletledger.walletbackend.dto.response.AdminUserResponse;
import com.walletledger.walletbackend.dto.response.DashboardResponse;
import com.walletledger.walletbackend.dto.response.FraudAlertResponse;
import com.walletledger.walletbackend.dto.response.JobRunResponse;
import com.walletledger.walletbackend.dto.response.ReconciliationResponse;
import com.walletledger.walletbackend.dto.response.SettlementMonitorResponse;
import com.walletledger.walletbackend.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() { return adminService.dashboard(); }

    @GetMapping("/users")
    public PageResponse<AdminUserResponse> users(@RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return adminService.users(search, page, size); }

    @GetMapping("/users/{userId}")
    public AdminUserDetailResponse userDetail(@PathVariable Long userId) { return adminService.userDetail(userId); }

    @PostMapping("/users/{userId}/freeze")
    public MessageResponse freezeWallet(Principal principal, @PathVariable Long userId,
            @RequestBody(required = false) WalletFreezeRequest ignoredRequest) { return adminService.freezeWallet(principal, userId, ignoredRequest); }

    @PostMapping("/users/{userId}/unfreeze")
    public MessageResponse unfreezeWallet(Principal principal, @PathVariable Long userId,
            @RequestBody(required = false) WalletFreezeRequest ignoredRequest) { return adminService.unfreezeWallet(principal, userId, ignoredRequest); }

    @GetMapping("/transactions")
    public PageResponse<AdminTransactionResponse> transactions(@ModelAttribute AdminTransactionFilter filter) { return adminService.transactions(filter); }

    @GetMapping("/transactions/{id}")
    public AdminTransactionDetailResponse transactionDetail(@PathVariable Long id) { return adminService.transactionDetail(id); }

    @GetMapping("/settlements")
    public SettlementMonitorResponse settlements() { return adminService.settlements(); }

    @PostMapping("/transactions/{id}/settle")
    public MessageResponse manualSettle(Principal principal, @PathVariable Long id) { return adminService.manualSettle(principal, id); }

    @GetMapping("/fraud-alerts")
    public List<FraudAlertResponse> fraudAlerts(@RequestParam(defaultValue = "PENDING") FraudAlertStatus status) { return adminService.fraudAlerts(status); }

    @PostMapping("/fraud-alerts/{alertId}/review")
    public FraudAlertResponse reviewFraudAlert(Principal principal, @PathVariable Long alertId,
            @Valid @RequestBody FraudReviewRequest request) { return adminService.reviewFraudAlert(principal, alertId, request); }

    @GetMapping("/jobs")
    public List<JobRunResponse> jobs(@RequestParam(required = false) String jobName) { return adminService.jobs(jobName); }

    @GetMapping("/reconciliation")
    public ReconciliationResponse reconciliation(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) { return adminService.reconciliation(date); }
}