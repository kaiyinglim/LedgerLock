package com.ledgerlock.dto;

import java.time.Instant;

public record AccountResponse(
    long id, String ownerName, long balanceSen, Instant createdAt, Instant updatedAt) {}
