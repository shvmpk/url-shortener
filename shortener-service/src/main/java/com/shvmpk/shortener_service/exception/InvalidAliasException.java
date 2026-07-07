package com.shvmpk.shortener_service.exception;

import org.springframework.http.HttpStatus;

public class InvalidAliasException extends BaseApiException {
  public InvalidAliasException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
