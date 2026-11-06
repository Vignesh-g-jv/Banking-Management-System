package com.vignesh.banking.repository;

import com.vignesh.banking.model.BankTransaction;
import com.vignesh.banking.model.TransactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<BankTransaction, Long> {

    List<BankTransaction> findByAccount_AccountNumberOrderByCreatedAtDescIdDesc(String accountNumber, Pageable pageable);

    List<BankTransaction> findByAccount_AccountNumberAndTypeOrderByCreatedAtDescIdDesc(
            String accountNumber, TransactionType type, Pageable pageable);
}
