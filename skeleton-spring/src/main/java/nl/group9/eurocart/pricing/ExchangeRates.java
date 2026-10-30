package nl.group9.eurocart.pricing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import nl.group9.eurocart.catalogue.Country;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The exchange rates of finance: one table per quarter with how many EUR one unit of a currency is worth. The date of
 * the order (in the time zone of the shop) decides which quarter, and so which table, applies. The tables are read from
 * {@code exchange-rates.json}; a quarter without a table is an error, it never falls back to another quarter.
 */
@Component
public class ExchangeRates {

	/** The shop is in the Netherlands: the quarter, and the day of the daily report, change at midnight there. */
	public static final String SHOP_ZONE_ID = "Europe/Amsterdam";

	public static final ZoneId SHOP_ZONE = ZoneId.of(SHOP_ZONE_ID);

	private static final Pattern QUARTER = Pattern.compile("\\d{4}-Q[1-4]");

	private final Map<String, Map<String, BigDecimal>> eurPerUnitByQuarter;

	public ExchangeRates(ObjectMapper objectMapper) {
		this.eurPerUnitByQuarter = load(objectMapper);
	}

	/** The quarter an instant falls in, in the time zone of the shop, e.g. {@code 2026-Q4}. */
	public String quarterOf(Instant at) {
		LocalDate date = at.atZone(SHOP_ZONE).toLocalDate();
		return date.getYear() + "-Q" + ((date.getMonthValue() - 1) / 3 + 1);
	}

	/** How many EUR one unit of {@code currency} was worth in the quarter of {@code at}. EUR itself is always 1. */
	public BigDecimal eurPerUnit(String currency, Instant at) {
		if (currency.equals("EUR")) {
			return BigDecimal.ONE;
		}
		String quarter = quarterOf(at);
		Map<String, BigDecimal> table = eurPerUnitByQuarter.get(quarter);
		if (table == null) {
			throw new MissingExchangeRatesException(quarter);
		}
		return table.get(currency);
	}

	private record QuarterRates(String quarter, Map<String, BigDecimal> eurPerUnit) {
	}

	private static Map<String, Map<String, BigDecimal>> load(ObjectMapper objectMapper) {
		Set<String> foreignCurrencies = Arrays.stream(Country.values()).map(Country::currency)
				.filter(currency -> !currency.equals("EUR")).collect(Collectors.toSet());
		try (InputStream in = new ClassPathResource("exchange-rates.json").getInputStream()) {
			List<QuarterRates> tables = objectMapper.readValue(in, new TypeReference<List<QuarterRates>>() {
			});
			Map<String, Map<String, BigDecimal>> byQuarter = new LinkedHashMap<>();
			for (QuarterRates table : tables) {
				if (table.quarter() == null || !QUARTER.matcher(table.quarter()).matches()) {
					throw new IllegalStateException("Quarter in exchange-rates.json must look like 2026-Q4: " + table.quarter());
				}
				if (table.eurPerUnit() == null || !table.eurPerUnit().keySet().containsAll(foreignCurrencies)) {
					throw new IllegalStateException("Missing exchange rate in exchange-rates.json for " + table.quarter()
							+ ", needed: " + foreignCurrencies);
				}
				if (table.eurPerUnit().values().stream().anyMatch(rate -> rate == null || rate.signum() <= 0)) {
					throw new IllegalStateException("Exchange rates must be positive in exchange-rates.json: " + table.quarter());
				}
				if (byQuarter.put(table.quarter(), Map.copyOf(table.eurPerUnit())) != null) {
					throw new IllegalStateException("Duplicate quarter in exchange-rates.json: " + table.quarter());
				}
			}
			return byQuarter;
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read exchange-rates.json", ex);
		}
	}

}
