package com.ledgerlock.controller;

import com.ledgerlock.dto.DepositRequest;
import com.ledgerlock.dto.DepositResponse;
import com.ledgerlock.service.DepositService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts/{id}/deposits")
public class DepositController {

  private final DepositService deposits;

  public DepositController(DepositService deposits) {
    this.deposits = deposits;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DepositResponse deposit(
      @PathVariable long id, @Valid @RequestBody DepositRequest request) {
    return deposits.deposit(id, request.amountSen());
  }
}
