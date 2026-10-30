package com.walletledger.walletbackend.dto.response;

import java.util.List;

public record AdminUserDetailResponse(
        AdminUserResponse user,
        List<TransactionSummaryResponse> recentTransactions
) {
}
