package com.example.zenith.service;

import com.example.zenith.entity.*;
import com.example.zenith.repositoy.AccountRepository;
import com.example.zenith.repositoy.LedgerEntryRepository;
import com.example.zenith.repositoy.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    private static final Long PLATFORM_FEE_ACCOUNT_ID = 1L;
    private static final Long PLATFORM_TAX_ACCOUNT_ID = 2L;

    private static final BigDecimal FEE_RATE = new BigDecimal("0.015");
    private static final BigDecimal GST_RATE = new BigDecimal("0.18");

    @Transactional
    public Transaction processTransfer(Long senderAccountId, Long receiverAccountId, BigDecimal principalAmount) {

        if (senderAccountId.equals(receiverAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        if (principalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }

        Account firstLock = accountRepository.findByIdForUpdate(
                        Math.min(senderAccountId, receiverAccountId))
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        Account secondLock = accountRepository.findByIdForUpdate(
                        Math.max(senderAccountId, receiverAccountId))
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        Account sender = firstLock.getId().equals(senderAccountId) ? firstLock : secondLock;
        Account receiver = firstLock.getId().equals(receiverAccountId) ? firstLock : secondLock;

        Account feeAccount = accountRepository.findByIdForUpdate(PLATFORM_FEE_ACCOUNT_ID)
                .orElseThrow(() -> new IllegalStateException("System fee account missing"));
        Account taxAccount = accountRepository.findByIdForUpdate(PLATFORM_TAX_ACCOUNT_ID)
                .orElseThrow(() -> new IllegalStateException("System tax account missing"));

        BigDecimal feeAmount = principalAmount.multiply(FEE_RATE).setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal gstAmount = feeAmount.multiply(GST_RATE).setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal totalDeduction = principalAmount.add(feeAmount).add(gstAmount);

        if (sender.getBalance().compareTo(totalDeduction) < 0) {
            throw new IllegalStateException("Insufficient funds to cover amount + fees + GST");
        }

        Transaction transaction = Transaction.builder()
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .build();
        transaction = transactionRepository.save(transaction);


        sender.setBalance(sender.getBalance().subtract(totalDeduction));
        createLedgerEntry(transaction, sender, Direction.DEBIT, totalDeduction);

        receiver.setBalance(receiver.getBalance().add(principalAmount));
        createLedgerEntry(transaction, receiver, Direction.CREDIT, principalAmount);

        feeAccount.setBalance(feeAccount.getBalance().add(feeAmount));
        createLedgerEntry(transaction, feeAccount, Direction.CREDIT, feeAmount);

        taxAccount.setBalance(taxAccount.getBalance().add(gstAmount));
        createLedgerEntry(transaction, taxAccount, Direction.CREDIT, gstAmount);

        accountRepository.saveAll(List.of(sender, receiver, feeAccount, taxAccount));

        return transaction;
    }

    private void createLedgerEntry(Transaction tx, Account account, Direction direction, BigDecimal amount) {
        LedgerEntry entry = LedgerEntry.builder()
                .transaction(tx)
                .account(account)
                .direction(direction)
                .amount(amount)
                .balanceAfter(account.getBalance())
                .build();
        ledgerEntryRepository.save(entry);
    }
}