package com.example.zenith.controller;

import com.example.zenith.dto.DepositRequest;
import com.example.zenith.dto.TransferRequest;
import com.example.zenith.dto.WithdrawalRequest;
import com.example.zenith.entity.Transaction;
import com.example.zenith.entity.TransactionStatus;
import com.example.zenith.entity.TransactionType;
import com.example.zenith.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final LedgerService ledgerService;

    @PostMapping("/transfer")
    public ResponseEntity<?> initiateTransfer(@Valid @RequestBody TransferRequest request) {
        try {
            Transaction completedTransaction = ledgerService.processTransfer(
                    request.getSenderAccountId(),
                    request.getReceiverAccountId(),
                    request.getAmount()
            );
            return ResponseEntity.ok(completedTransaction);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while processing the transfer.");
        }
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> initiateDeposit(@Valid @RequestBody DepositRequest request) {
        try {
            Transaction completedTransaction = ledgerService.processDeposit(
                    request.getAccountId(),
                    request.getAmount()
            );
            return ResponseEntity.ok(completedTransaction);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while processing the deposit.");
        }
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> initiateWithdrawal(@Valid @RequestBody WithdrawalRequest request) {
        try {
            Transaction completedTransaction = ledgerService.processWithdrawal(
                    request.getAccountId(),
                    request.getAmount()
            );
            return ResponseEntity.ok(completedTransaction);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage()); 
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while processing the withdrawal.");
        }
    }

    @PostMapping("/reverse/{referenceId}")
    public ResponseEntity<?> reverseTransaction(@PathVariable String referenceId) {
        try {
            Transaction reversalTransaction = ledgerService.reverseTransaction(referenceId);
            return ResponseEntity.ok(reversalTransaction);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage()); 
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while reversing the transaction.");
        }
    }

    @GetMapping
    public ResponseEntity<?> getTransactions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {

        try {
            Page<Transaction> transactions = ledgerService.getTransactions(
                    search, type, status, page, size, sortBy, sortDir
            );
            return ResponseEntity.ok(transactions);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while fetching transactions.");
        }
    }
}