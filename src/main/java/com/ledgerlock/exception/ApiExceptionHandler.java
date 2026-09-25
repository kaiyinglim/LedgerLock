package com.ledgerlock.exception;

import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(InvalidDepositAmountException.class)
  public ProblemDetail handleInvalidDepositAmount(InvalidDepositAmountException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler(BalanceLimitExceededException.class)
  public ProblemDetail handleBalanceLimitExceeded(BalanceLimitExceededException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
  }

  @ExceptionHandler(AccountNotFoundException.class)
  public ProblemDetail handleAccountNotFound(AccountNotFoundException exception) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String detail =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getDefaultMessage())
            .distinct()
            .sorted()
            .collect(Collectors.joining("; "));
    return handleExceptionInternal(
        exception, ProblemDetail.forStatusAndDetail(status, detail), headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    // Parsing exceptions can contain submitted values and implementation details.
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            status, "Request body must be valid JSON containing only supported fields");
    return handleExceptionInternal(exception, problem, headers, status, request);
  }
}
