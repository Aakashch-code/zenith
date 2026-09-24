package com.example.zenith.service;

import com.example.zenith.authentication.application.service.SecuredService;
import com.example.zenith.entity.*;
import com.example.zenith.repositoy.AccountRepository;
import com.example.zenith.repositoy.LedgerEntryRepository;
import com.example.zenith.repositoy.TransactionRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LedgerService extends SecuredService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AuditService auditService;

    private static final Long FEE = 1L;
    private static final Long TAX = 2L;
    private static final Long VAULT = 4L;
    private static final Set<Long> SYSTEM = Set.of(FEE, TAX, VAULT);
    private static final BigDecimal FEE_RATE = new BigDecimal("0.015");
    private static final BigDecimal GST_RATE = new BigDecimal("0.18");

    @Transactional
    public Transaction processTransfer(Long senderId, Long receiverId, BigDecimal amount) {
        try {
            validateAmount(amount);

            if (senderId.equals(receiverId))
                throw new IllegalArgumentException("Cannot transfer to the same account");

            validateUser(senderId);
            validateUser(receiverId);

            Map<Long, Account> a = lock(senderId, receiverId, FEE, TAX);
            Account sender = a.get(senderId);

            if (!sender.getUserId().equals(currentUserId())) {
                throw new AccessDeniedException("Unauthorized: You do not own the sender account.");
            }

            Account receiver = a.get(receiverId);
            Account fee = a.get(FEE);
            Account tax = a.get(TAX);

            BigDecimal feeAmount = amount.multiply(FEE_RATE).setScale(4, RoundingMode.HALF_EVEN);
            BigDecimal gstAmount = feeAmount.multiply(GST_RATE).setScale(4, RoundingMode.HALF_EVEN);
            BigDecimal total = amount.add(feeAmount).add(gstAmount);

            requireBalance(sender, total, "Insufficient funds to cover amount + fees + GST");

            Transaction tx = transaction(TransactionType.TRANSFER);
            debit(tx, sender, total);
            credit(tx, receiver, amount);
            credit(tx, fee, feeAmount);
            credit(tx, tax, gstAmount);

            save(a.values());

            auditService.logAction(currentUserId(), ActionType.TRANSFER_COMPLETED,
                    String.format("Transferred %s from Acc %d to Acc %d (Ref: %s)", amount, senderId, receiverId, tx.getReferenceId()));

            return tx;

        } catch (Exception e) {
            Long actorId = getActorIdSafely();
            auditService.logAction(actorId, ActionType.TRANSFER_FAILED, "Transfer Failed: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public Transaction processDeposit(Long accountId, BigDecimal amount) {
        try {
            validateAmount(amount);
            validateUser(accountId);

            Map<Long, Account> a = lock(VAULT, accountId);
            Account userAccount = a.get(accountId);

            if (!userAccount.getUserId().equals(currentUserId())) {
                throw new AccessDeniedException("Unauthorized: You do not own this account.");
            }

            Transaction tx = execute(TransactionType.DEPOSIT, a.get(VAULT), userAccount, amount, a.values());

            auditService.logAction(currentUserId(), ActionType.DEPOSIT_COMPLETED, "Deposited " + amount + " to Acc " + accountId);
            return tx;

        } catch (Exception e) {
            auditService.logAction(getActorIdSafely(), ActionType.DEPOSIT_FAILED, "Deposit Failed: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public Transaction processWithdrawal(Long accountId, BigDecimal amount) {
        try {
            validateAmount(amount);
            validateUser(accountId);

            Map<Long, Account> a = lock(VAULT, accountId);
            Account userAccount = a.get(accountId);

            if (!userAccount.getUserId().equals(currentUserId())) {
                throw new AccessDeniedException("Unauthorized: You do not own this account.");
            }

            requireBalance(userAccount, amount, "Insufficient funds for withdrawal");
            Transaction tx = execute(TransactionType.WITHDRAWAL, userAccount, a.get(VAULT), amount, a.values());

            auditService.logAction(currentUserId(), ActionType.WITHDRAWAL_COMPLETED, "Withdrew " + amount + " from Acc " + accountId);
            return tx;

        } catch (Exception e) {
            auditService.logAction(getActorIdSafely(), ActionType.WITHDRAWAL_FAILED, "Withdrawal Failed: " + e.getMessage());
            throw e;
        }
    }

    @Transactional
    public Transaction reverseTransaction(String referenceId) {
        try {

            if (!isAdmin()) {
                throw new AccessDeniedException("Unauthorized: Only Admins can reverse transactions.");
            }

            Transaction original = transactionRepository.findByReferenceId(referenceId)
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

            if (original.getStatus() == TransactionStatus.REVERSED)
                throw new IllegalStateException("Transaction already reversed");

            List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(original.getId());

            Long[] ids = entries.stream()
                    .map(e -> e.getAccount().getId())
                    .distinct()
                    .toArray(Long[]::new);

            Map<Long, Account> accounts = lock(ids);
            Transaction reversal = transaction(TransactionType.REVERSAL);

            for (LedgerEntry entry : entries) {
                Account account = accounts.get(entry.getAccount().getId());
                BigDecimal amt = entry.getAmount();
                Direction direction = reverse(entry.getDirection());

                if (direction == Direction.CREDIT) {
                    account.setBalance(account.getBalance().add(amt));
                } else {
                    if (!SYSTEM.contains(account.getId()))
                        requireBalance(account, amt, "Insufficient funds for reversal");
                    account.setBalance(account.getBalance().subtract(amt));
                }

                ledger(reversal, account, direction, amt);
            }

            save(accounts.values());
            original.setStatus(TransactionStatus.REVERSED);
            transactionRepository.save(original);

            auditService.logAction(currentUserId(), ActionType.REVERSAL_COMPLETED,
                    "Admin reversed TX Ref: " + referenceId + " | Reversal TX Ref: " + reversal.getReferenceId());

            return reversal;

        } catch (Exception e) {
            auditService.logAction(getActorIdSafely(), ActionType.REVERSAL_FAILED,
                    "Reversal Failed for TX Ref " + referenceId + ": " + e.getMessage());
            throw e;
        }
    }

    private Long getActorIdSafely() {
        try {
            return currentUserId();
        } catch (Exception ex) {
            return 0L;
        }
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void applyInterestToAccount(Long accountId, BigDecimal interestAmount) {
        try {
            validateAmount(interestAmount);

            Map<Long, Account> a = lock(VAULT, accountId);
            Account vault = a.get(VAULT);
            Account user = a.get(accountId);

            Transaction tx = execute(TransactionType.INTEREST_PAYMENT, vault, user, interestAmount, a.values());

            auditService.logAction(0L, ActionType.INTEREST_APPLIED,
                    "System applied ₹" + interestAmount + " interest to Acc " + accountId);

        } catch (Exception e) {
            auditService.logAction(0L, ActionType.INTEREST_FAILED,
                    "Failed to apply interest to Acc " + accountId + ": " + e.getMessage());
            throw e;
        }
    }
    private Transaction execute(TransactionType type, Account debit, Account credit, BigDecimal amount, Collection<Account> accounts) {
        Transaction tx = transaction(type);
        debit(tx, debit, amount);
        credit(tx, credit, amount);
        save(accounts);
        return tx;
    }

    private Transaction transaction(TransactionType type) {
        return transactionRepository.save(
                Transaction.builder()
                        .type(type)
                        .status(TransactionStatus.COMPLETED)
                        .build()
        );
    }

    private void debit(Transaction tx, Account account, BigDecimal amount) {
        account.setBalance(account.getBalance().subtract(amount));
        ledger(tx, account, Direction.DEBIT, amount);
    }

    private void credit(Transaction tx, Account account, BigDecimal amount) {
        account.setBalance(account.getBalance().add(amount));
        ledger(tx, account, Direction.CREDIT, amount);
    }

    private void ledger(Transaction tx, Account account, Direction direction, BigDecimal amount) {
        ledgerEntryRepository.save(
                LedgerEntry.builder()
                        .transaction(tx)
                        .account(account)
                        .direction(direction)
                        .amount(amount)
                        .balanceAfter(account.getBalance())
                        .build()
        );
    }

    private Map<Long, Account> lock(Long... ids) {
        Map<Long, Account> accounts = new LinkedHashMap<>();

        Arrays.stream(ids)
                .distinct()
                .sorted()
                .forEach(id -> accounts.put(
                        id,
                        accountRepository.findByIdForUpdate(id)
                                .orElseThrow(() ->
                                        new IllegalArgumentException("Account not found: " + id))
                ));

        return accounts;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Invalid amount");
    }

    private void validateUser(Long id) {
        if (SYSTEM.contains(id))
            throw new IllegalArgumentException("System account cannot be used");
    }

    private void requireBalance(Account account, BigDecimal amount, String message) {
        if (account.getBalance().compareTo(amount) < 0)
            throw new IllegalStateException(message);
    }

    private void save(Collection<Account> accounts) {
        accountRepository.saveAll(accounts);
    }

    private Direction reverse(Direction direction) {
        return direction == Direction.CREDIT
                ? Direction.DEBIT
                : Direction.CREDIT;
    }
}