package com.walletledger.walletbackend.controller;

import java.security.Principal;

import com.walletledger.walletbackend.dto.common.MessageResponse;
import com.walletledger.walletbackend.dto.common.PageResponse;
import com.walletledger.walletbackend.dto.response.NotificationResponse;
import com.walletledger.walletbackend.dto.response.UnreadCountResponse;
import com.walletledger.walletbackend.service.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @GetMapping
    public PageResponse<NotificationResponse> list(Principal principal,
                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return notificationQueryService.list(principal.getName(), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(Principal principal) {
        return notificationQueryService.unreadCount(principal.getName());
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(Principal principal, @PathVariable Long id) {
        return notificationQueryService.markRead(principal.getName(), id);
    }

    @PatchMapping("/read-all")
    public MessageResponse markAllRead(Principal principal) {
        return notificationQueryService.markAllRead(principal.getName());
    }
}
