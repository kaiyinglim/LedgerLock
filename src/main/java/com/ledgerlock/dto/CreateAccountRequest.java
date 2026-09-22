package com.ledgerlock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
    @NotBlank(message = "ownerName must not be blank")
        @Size(max = 100, message = "ownerName must be at most 100 characters")
        String ownerName) {

  public CreateAccountRequest {
    if (ownerName != null) {
      ownerName = ownerName.strip();
    }
  }
}
