package nl.group9.eurocart.pricing;

/** There is no exchange rate table for the quarter of the order date: finance has to add one. */
public class MissingExchangeRatesException extends RuntimeException {

	public MissingExchangeRatesException(String quarter) {
		super("No exchange rates for " + quarter + ": add that quarter to exchange-rates.json");
	}

}
