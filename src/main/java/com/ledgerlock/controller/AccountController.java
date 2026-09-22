package com.ledgerlock.controller;

import com.ledgerlock.dto.AccountResponse;
import com.ledgerlock.dto.CreateAccountRequest;
import com.ledgerlock.service.AccountService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @PostMapping
  public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
    AccountResponse response = accountService.create(request.ownerName());
    return ResponseEntity.created(URI.create("/accounts/" + response.id())).body(response);
  }

  @GetMapping("/{id}")
  public AccountResponse get(@PathVariable long id) {
    return accountService.get(id);
  }
}
