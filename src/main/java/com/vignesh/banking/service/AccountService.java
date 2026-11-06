
package com.vignesh.banking.service;

import com.vignesh.banking.dto.AccountResponse;
import com.vignesh.banking.dto.BalanceResponse;
import com.vignesh.banking.dto.CreateAccountRequest;
import com.vignesh.banking.exception.AccountNotFoundException;
import com.vignesh.banking.exception.AccountStateException;
import com.vignesh.banking.model.Account;
import com.vignesh.banking.model.AccountStatus;
import com.vignesh.banking.model.BankTransaction;
import com.vignesh.banking.model.TransactionType;
import com.vignesh.banking.repository.AccountRepository;
import com.vignesh.banking.repository.TransactionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final TransactionRules rules;

    public AccountService(
            AccountRepository accounts,
            TransactionRepository transactions,
            TransactionRules rules) {

        this.accounts = accounts;
        this.transactions = transactions;
        this.rules = rules;
    }

    // Create a new bank account
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {

        BigDecimal openingDeposit =
                rules.validateOpeningDeposit(request.initialDeposit());

        Account account = new Account();

        account.setAccountNumber(generateAccountNumber());
        account.setHolderName(request.holderName().trim());
        account.setEmail(
                request.email().trim().toLowerCase(Locale.ROOT)
        );
        account.setAccountType(request.accountType());
        account.setBalance(openingDeposit);

        account = accounts.save(account);

        // Record opening deposit
        if (openingDeposit.signum() > 0) {

            transactions.save(
                    BankTransaction.of(
                            account,
                            TransactionType.DEPOSIT,
                            openingDeposit,
                            "Opening deposit",
                            null,
                            UUID.randomUUID().toString()
                    )
            );
        }

        return AccountResponse.from(account);
    }

    // Get all accounts
    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts() {

        return accounts.findAllByOrderByCreatedAtDescIdDesc()
                .stream()
                .map(AccountResponse::from)
                .toList();
    }

    // Get account by account number
    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountNumber) {

        return AccountResponse.from(find(accountNumber));
    }

    // Get account balance
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountNumber) {

        Account account = find(accountNumber);

        return new BalanceResponse(
                account.getAccountNumber(),
                account.getBalance()
        );
    }

    // Close account
    @Transactional
    public AccountResponse closeAccount(String accountNumber) {

        String number = accountNumber.trim();

        Account account = accounts.findByAccountNumberForUpdate(number)
                .orElseThrow(
                        () -> new AccountNotFoundException(number)
                );

        if (account.getStatus() == AccountStatus.CLOSED) {

            throw new AccountStateException(
                    "Account " + number + " is already closed"
            );
        }

        if (account.getBalance().signum() != 0) {

            throw new AccountStateException(
                    "Account " + number + " still holds "
                            + account.getBalance().toPlainString()
                            + ". Withdraw or transfer the funds before closing it"
            );
        }

        account.setStatus(AccountStatus.CLOSED);

        return AccountResponse.from(accounts.save(account));
    }

    // Find account
    private Account find(String accountNumber) {

        String number = accountNumber.trim();

        return accounts.findByAccountNumber(number)
                .orElseThrow(
                        () -> new AccountNotFoundException(number)
                );
    }

    // Generate unique 10-digit account number
    private String generateAccountNumber() {

        String number;

        do {
            long value = ThreadLocalRandom.current()
                    .nextLong(1_000_000_000L, 10_000_000_000L);

            number = Long.toString(value);

        } while (accounts.existsByAccountNumber(number));

        return number;
    }
}
