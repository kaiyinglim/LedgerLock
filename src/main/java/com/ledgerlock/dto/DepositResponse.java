package com.ledgerlock.dto;

import java.time.Instant;

public record DepositResponse(
    long ledgerTransactionId, long accountId, long amountSen, long balanceSen, Instant createdAt) {}
