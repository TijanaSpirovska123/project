package com.example.marketing.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum StatusErrorResponse {
  INVALID_CREDENTIALS("AUTH-001", "Invalid email or password", HttpStatus.UNAUTHORIZED),
  ACCOUNT_EXPIRED("AUTH-002", "Account expired", HttpStatus.UNAUTHORIZED),
  ACCOUNT_LOCKED("AUTH-003", "Account locked", HttpStatus.UNAUTHORIZED),
  CREDENTIALS_EXPIRED("AUTH-004", "Credentials expired", HttpStatus.UNAUTHORIZED),
  ACCOUNT_DISABLED("AUTH-005", "Account disabled", HttpStatus.UNAUTHORIZED),
  META_TOKEN_EXPIRED("META_TOKEN_EXPIRED", "Meta access token expired. Please reconnect your Meta account.", HttpStatus.UNAUTHORIZED),

  // ───────────────────── CLIENT ERRORS ─────────────────────
  ILLEGAL_ARGUMENT("D-152", "Invalid request", HttpStatus.BAD_REQUEST),
  ENTITY_NOT_FOUND("D-153", "Entity not found", HttpStatus.NOT_FOUND),
  DUPLICATE_RESOURCE("D-160", "Resource already exists", HttpStatus.CONFLICT),
  CONSTRAINT_VIOLATION("D-161", "Invalid data", HttpStatus.CONFLICT),
  FOREIGN_KEY_VIOLATION("D-162", "Cannot delete: referenced by other records", HttpStatus.CONFLICT),
  DELETE_NOT_ALLOWED("D-156", "Delete not allowed", HttpStatus.BAD_REQUEST),
  UPDATE_NOT_ALLOWED("D-157", "Update not allowed", HttpStatus.BAD_REQUEST),
  FORBIDDEN("D-158", "Access denied", HttpStatus.FORBIDDEN),
  RATE_LIMITED("D-149", "Too many requests", HttpStatus.TOO_MANY_REQUESTS),

  // ───────────────────── EXTERNAL API ─────────────────────
  FACEBOOK_API_ERROR("D-164", "Facebook API error", HttpStatus.BAD_GATEWAY),
  EXTERNAL_API_ERROR("D-163", "External API error", HttpStatus.BAD_GATEWAY),

  // ───────────────────── SERVER ERRORS ─────────────────────
  SERVICE_UNAVAILABLE("D-155", "Service unavailable", HttpStatus.SERVICE_UNAVAILABLE),
  GENERIC_ERROR("D-151", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR)
  // Add new ones easily here
  ;

  private final String code;
  private final String description;
  private final HttpStatus httpStatus;

  // constructor + getters

  public ErrorMessage toErrorMessage(String detail) {
    return ErrorMessage.of(code, description, detail);
  }

  public ErrorMessage toErrorMessage() {
    return toErrorMessage(null);
  }
}
