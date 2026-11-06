package com.vignesh.banking.controller;

import com.vignesh.banking.dto.AccountResponse;
import com.vignesh.banking.dto.AmountRequest;
import com.vignesh.banking.dto.BalanceResponse;
import com.vignesh.banking.dto.CreateAccountRequest;
import com.vignesh.banking.dto.TransactionResponse;
import com.vignesh.banking.model.TransactionType;
import com.vignesh.banking.service.AccountService;
import com.vignesh.banking.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    public AccountController(AccountService accountService, TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse created = accountService.createAccount(request);
        return ResponseEntity.created(URI.create("/api/accounts/" + created.accountNumber())).body(created);
    }

    @GetMapping
    public List<AccountResponse> list() {
        return accountService.listAccounts();
    }

    @GetMapping("/{accountNumber}")
    public AccountResponse get(@PathVariable String accountNumber) {
        return accountService.getAccount(accountNumber);
    }

    @GetMapping("/{accountNumber}/balance")
    public BalanceResponse balance(@PathVariable String accountNumber) {
        return accountService.getBalance(accountNumber);
    }

    @DeleteMapping("/{accountNumber}")
    public AccountResponse close(@PathVariable String accountNumber) {
        return accountService.closeAccount(accountNumber);
    }

    @PostMapping("/{accountNumber}/deposit")
    public TransactionResponse deposit(@PathVariable String accountNumber,
                                       @Valid @RequestBody AmountRequest request) {
        return transactionService.deposit(accountNumber, request.amount(), request.description());
    }

    @PostMapping("/{accountNumber}/withdraw")
    public TransactionResponse withdraw(@PathVariable String accountNumber,
                                        @Valid @RequestBody AmountRequest request) {
        return transactionService.withdraw(accountNumber, request.amount(), request.description());
    }

    @GetMapping("/{accountNumber}/transactions")
    public List<TransactionResponse> history(@PathVariable String accountNumber,
                                             @RequestParam(required = false) TransactionType type,
                                             @RequestParam(defaultValue = "100") int limit) {
        return transactionService.history(accountNumber, type, limit);
    }
}
