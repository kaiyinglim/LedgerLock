package com.ledgerlock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DepositRequest(
    @NotNull(message = "amountSen is required")
        @Positive(message = "amountSen must be greater than zero")
        Long amountSen) {}
