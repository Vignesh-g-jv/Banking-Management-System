package com.vignesh.banking.controller;

import com.vignesh.banking.dto.TransferRequest;
import com.vignesh.banking.dto.TransferResponse;
import com.vignesh.banking.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransactionService transactionService;

    public TransferController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        return transactionService.transfer(
                request.fromAccount(), request.toAccount(), request.amount(), request.description());
    }
}
