package com.henryfll.auth.shared.error;

/** Una operación válida en forma pero que viola una regla de negocio (HTTP 422). */
public class BusinessRuleException extends DomainException {

  public BusinessRuleException(String message) {
    super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
  }

  public BusinessRuleException(ErrorCode code, String message) {
    super(code, message);
  }
}
