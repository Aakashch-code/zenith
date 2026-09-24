package com.example.zenith.controller;

import com.example.zenith.dto.DepositRequest;
import com.example.zenith.dto.TransferRequest;
import com.example.zenith.dto.WithdrawalRequest;
import com.example.zenith.entity.Transaction;
import com.example.zenith.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final LedgerService ledgerService;

    @PostMapping("/transfer")
    public ResponseEntity<Transaction> initiateTransfer(@Valid @RequestBody TransferRequest request) {
        Transaction completedTransaction = ledgerService.processTransfer(
                request.getSenderAccountId(),
                request.getReceiverAccountId(),
                request.getAmount()
        );
        return ResponseEntity.ok(completedTransaction);
    }

    @PostMapping("/deposit")
    public ResponseEntity<Transaction> initiateDeposit(@Valid @RequestBody DepositRequest request) {
        Transaction completedTransaction = ledgerService.processDeposit(
                request.getAccountId(),
                request.getAmount()
        );
        return ResponseEntity.ok(completedTransaction);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<Transaction> initiateWithdrawal(@Valid @RequestBody WithdrawalRequest request) {
        Transaction completedTransaction = ledgerService.processWithdrawal(
                request.getAccountId(),
                request.getAmount()
        );
        return ResponseEntity.ok(completedTransaction);
    }

    @PostMapping("/reverse/{referenceId}")
    public ResponseEntity<Transaction> reverseTransaction(@PathVariable String referenceId) {
        Transaction reversalTransaction = ledgerService.reverseTransaction(referenceId);
        return ResponseEntity.ok(reversalTransaction);
    }
}