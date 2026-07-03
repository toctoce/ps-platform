package com.psplatform.backend.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SupportInquiryExceptionHandler {

	@ExceptionHandler(InvalidSupportInquiryException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleInvalid(InvalidSupportInquiryException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	@ExceptionHandler(SupportInquiryDeliveryException.class)
	@ResponseStatus(HttpStatus.BAD_GATEWAY)
	public ErrorResponse handleDeliveryFailure(SupportInquiryDeliveryException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	public record ErrorResponse(String message) {
	}
}
