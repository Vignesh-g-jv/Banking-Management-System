package com.vignesh.banking.dto;

import com.vignesh.banking.model.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateAccountRequest(

        @NotBlank(message = "Holder name is required")
        @Size(min = 2, max = 100, message = "Holder name must be between 2 and 100 characters")
        String holderName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @NotNull(message = "Account type is required (SAVINGS or CURRENT)")
        AccountType accountType,

        @DecimalMin(value = "0.00", message = "Opening deposit cannot be negative")
        @Digits(integer = 12, fraction = 2, message = "Opening deposit can have at most 2 decimal places")
        BigDecimal initialDeposit) {
}
