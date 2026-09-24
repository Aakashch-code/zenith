package com.example.zenith.service;

import com.example.zenith.entity.*;
import com.example.zenith.repositoy.AccountRepository;
import com.example.zenith.repositoy.LedgerEntryRepository;
import com.example.zenith.repositoy.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    private static final Long FEE = 1L;
    private static final Long TAX = 2L;
    private static final Long VAULT = 4L;

    private static final Set<Long> SYSTEM = Set.of(FEE, TAX, VAULT);
    private static final BigDecimal FEE_RATE = new BigDecimal("0.015");
    private static final BigDecimal GST_RATE = new BigDecimal("0.18");

    @Transactional
    public Transaction processTransfer(Long senderId, Long receiverId, BigDecimal amount) {
        validateAmount(amount);

        if (senderId.equals(receiverId))
            throw new IllegalArgumentException("Cannot transfer to the same account");

        validateUser(senderId);
        validateUser(receiverId);

        Map<Long, Account> a = lock(senderId, receiverId, FEE, TAX);

        Account sender = a.get(senderId);
        Account receiver = a.get(receiverId);
        Account fee = a.get(FEE);
        Account tax = a.get(TAX);

        BigDecimal feeAmount = amount.multiply(FEE_RATE)
                .setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal gstAmount = feeAmount.multiply(GST_RATE)
                .setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal total = amount.add(feeAmount).add(gstAmount);

        requireBalance(sender, total, "Insufficient funds to cover amount + fees + GST");

        Transaction tx = transaction(TransactionType.TRANSFER);

        debit(tx, sender, total);
        credit(tx, receiver, amount);
        credit(tx, fee, feeAmount);
        credit(tx, tax, gstAmount);

        save(a.values());
        return tx;
    }

    @Transactional
    public Transaction processDeposit(Long accountId, BigDecimal amount) {
        validateAmount(amount);
        validateUser(accountId);

        Map<Long, Account> a = lock(VAULT, accountId);
        return execute(TransactionType.DEPOSIT, a.get(VAULT), a.get(accountId), amount, a.values());
    }

    @Transactional
    public Transaction processWithdrawal(Long accountId, BigDecimal amount) {
        validateAmount(amount);
        validateUser(accountId);

        Map<Long, Account> a = lock(VAULT, accountId);
        Account user = a.get(accountId);

        requireBalance(user, amount, "Insufficient funds for withdrawal");

        return execute(TransactionType.WITHDRAWAL, user, a.get(VAULT), amount, a.values());
    }

    @Transactional
    public Transaction reverseTransaction(String referenceId) {
        Transaction original = transactionRepository.findByReferenceId(referenceId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (original.getStatus() == TransactionStatus.REVERSED)
            throw new IllegalStateException("Transaction already reversed");

        List<LedgerEntry> entries =
                ledgerEntryRepository.findByTransactionId(original.getId());

        Long[] ids = entries.stream()
                .map(e -> e.getAccount().getId())
                .distinct()
                .toArray(Long[]::new);

        Map<Long, Account> accounts = lock(ids);
        Transaction reversal = transaction(TransactionType.REVERSAL);

        for (LedgerEntry entry : entries) {
            Account account = accounts.get(entry.getAccount().getId());
            BigDecimal amount = entry.getAmount();
            Direction direction = reverse(entry.getDirection());

            if (direction == Direction.CREDIT) {
                account.setBalance(account.getBalance().add(amount));
            } else {
                if (!SYSTEM.contains(account.getId()))
                    requireBalance(account, amount, "Insufficient funds for reversal");

                account.setBalance(account.getBalance().subtract(amount));
            }

            ledger(reversal, account, direction, amount);
        }

        save(accounts.values());

        original.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(original);

        return reversal;
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