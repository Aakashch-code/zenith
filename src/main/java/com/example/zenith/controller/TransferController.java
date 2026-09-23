package com.example.zenith.controller;

import com.example.zenith.dto.TransferRequest;
import com.example.zenith.entity.Transaction;
import com.example.zenith.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<Transaction> initiateTransfer(@Valid @RequestBody TransferRequest request) {

        Transaction completedTransaction = transferService.processTransfer(
                request.getSenderAccountId(),
                request.getReceiverAccountId(),
                request.getAmount()
        );

        return ResponseEntity.ok(completedTransaction);
    }
}