package com.example.zenith.authentication.application.service;

import com.example.zenith.dto.TransferRequest;
import com.example.zenith.entity.Account;
import com.example.zenith.entity.Transaction;
import com.example.zenith.entity.User;
import com.example.zenith.repositoy.AccountRepository;
import com.example.zenith.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionAuthorizationService {

    private final LedgerService ledgerService;
    private final AccountRepository accountRepository;

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new AccessDeniedException("Unauthenticated user.");
        }
        return user.getId();
    }

    @Transactional
    public Transaction authorizeAndProcessTransfer(TransferRequest request) {
        Long currentUserId = getCurrentUserId();

        Account senderAccount = accountRepository.findById(request.getSenderAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Sender account not found"));

        if (!senderAccount.getUserId().equals(currentUserId)) {
            throw new AccessDeniedException("Unauthorized: You do not own the sender account.");
        }

        return ledgerService.processTransfer(
                request.getSenderAccountId(),
                request.getReceiverAccountId(),
                request.getAmount()
        );
    }
}