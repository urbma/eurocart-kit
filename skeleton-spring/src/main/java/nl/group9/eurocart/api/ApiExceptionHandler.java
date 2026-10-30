package nl.group9.eurocart.api;

import nl.group9.eurocart.cart.UnknownProductException;
import nl.group9.eurocart.order.OrderNotFoundException;
import nl.group9.eurocart.pricing.MissingExchangeRatesException;
import nl.group9.eurocart.report.DailyReportException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(UnknownProductException.class)
	public ProblemDetail unknownProduct(UnknownProductException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(OrderNotFoundException.class)
	public ProblemDetail unknownOrder(OrderNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(MissingExchangeRatesException.class)
	public ProblemDetail missingExchangeRates(MissingExchangeRatesException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
	}

	@ExceptionHandler(DailyReportException.class)
	public ProblemDetail dailyReport(DailyReportException ex) {
		HttpStatus status = switch (ex.reason()) {
			case NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
			case NOT_DELIVERED -> HttpStatus.BAD_GATEWAY;
			case TOO_SOON -> HttpStatus.TOO_MANY_REQUESTS;
		};
		return ProblemDetail.forStatusAndDetail(status, ex.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ProblemDetail badRequest(IllegalArgumentException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

}
