package com.vignesh.banking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One line in an account's ledger. A transfer produces two rows that share a reference:
 * a TRANSFER_OUT on the source account and a TRANSFER_IN on the destination account.
 */
@Entity
@Table(name = "transactions", indexes = @Index(name = "idx_transactions_account", columnList = "account_id"))
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 100)
    private String description;

    @Column(name = "counterparty_account", length = 12)
    private String counterpartyAccount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public static BankTransaction of(Account account, TransactionType type, BigDecimal amount,
                                     String description, String counterpartyAccount, String reference) {
        BankTransaction tx = new BankTransaction();
        tx.account = account;
        tx.type = type;
        tx.amount = amount;
        tx.balanceAfter = account.getBalance();
        tx.description = description;
        tx.counterpartyAccount = counterpartyAccount;
        tx.reference = reference;
        return tx;
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Account getAccount() {
        return account;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public String getCounterpartyAccount() {
        return counterpartyAccount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
