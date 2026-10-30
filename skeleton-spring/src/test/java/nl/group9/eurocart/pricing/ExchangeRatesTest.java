package nl.group9.eurocart.pricing;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeRatesTest {

	private final ExchangeRates rates = new ExchangeRates(new ObjectMapper());

	@ParameterizedTest
	@CsvSource({
			"2026-01-01T00:00:00+01:00, 2026-Q1",
			"2026-03-31T23:59:59+02:00, 2026-Q1",
			"2026-04-01T00:00:00+02:00, 2026-Q2",
			"2026-07-01T00:00:00+02:00, 2026-Q3",
			"2026-10-01T00:00:00+02:00, 2026-Q4",
			"2026-12-31T23:59:59+01:00, 2026-Q4",
			"2027-01-01T00:00:00+01:00, 2027-Q1"
	})
	void mapsADateToItsQuarterInTheTimeZoneOfTheShop(String at, String quarter) {
		assertThat(rates.quarterOf(OffsetDateTime.parse(at).toInstant())).isEqualTo(quarter);
	}

	@Test
	void hasTheRatesOfFinancePerQuarter() {
		Instant q4 = Instant.parse("2026-11-15T12:00:00Z");
		Instant q3 = Instant.parse("2026-08-15T12:00:00Z");

		assertThat(rates.eurPerUnit("GBP", q4)).isEqualByComparingTo("1.25");
		assertThat(rates.eurPerUnit("CHF", q4)).isEqualByComparingTo("1.04");
		assertThat(rates.eurPerUnit("PLN", q4)).isEqualByComparingTo("0.33");
		assertThat(rates.eurPerUnit("GBP", q3)).isEqualByComparingTo("1.20");
		assertThat(rates.eurPerUnit("CHF", q3)).isEqualByComparingTo("1.09");
		assertThat(rates.eurPerUnit("PLN", q3)).isEqualByComparingTo("0.23");
	}

	@Test
	void euroIsAlwaysOneEvenWithoutATable() {
		assertThat(rates.eurPerUnit("EUR", Instant.parse("2030-01-01T00:00:00Z"))).isEqualByComparingTo("1");
	}

	@Test
	void failsClearlyForAQuarterWithoutATable() {
		assertThatThrownBy(() -> rates.eurPerUnit("PLN", Instant.parse("2027-02-01T12:00:00Z")))
				.isInstanceOf(MissingExchangeRatesException.class)
				.hasMessage("No exchange rates for 2027-Q1: add that quarter to exchange-rates.json");
	}

}
