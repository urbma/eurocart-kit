package nl.group9.eurocart.pricing;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import nl.group9.eurocart.cart.CartLine;
import nl.group9.eurocart.cart.CartView;
import nl.group9.eurocart.catalogue.Category;
import nl.group9.eurocart.catalogue.Country;
import nl.group9.eurocart.catalogue.Product;
import nl.group9.eurocart.catalogue.ProductCatalogue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingServiceTest {

	private final ProductCatalogue catalogue = new ProductCatalogue(new ObjectMapper());

	/** A day in 2026-Q4: the rates of that quarter apply to everything that is not given a date. */
	private static final Instant Q4 = Instant.parse("2026-11-15T12:00:00Z");

	private final PricingService pricing = new PricingService(catalogue, new ExchangeRates(new ObjectMapper()),
			Clock.fixed(Q4, ZoneOffset.UTC));

	@ParameterizedTest
	@CsvSource({
			"LAP-001, 23, 303.03, 69.70, 372.73",
			"BOK-001,  5, 303.03, 15.15, 318.18",
			"EBK-001,  5, 303.03, 15.15, 318.18",
			"FOD-001,  5, 303.03, 15.15, 318.18",
			"MED-001,  8, 303.03, 24.24, 327.27"
	})
	void pricesOneHundredEuroWithThePolishVatOfTheProduct(String productId, String vatPercent, String net, String vat, String gross) {
		Price price = pricing.price(new BigDecimal("100.00"), catalogue.find(productId).orElseThrow(), Country.PL);

		assertThat(price.vatPercent()).isEqualByComparingTo(vatPercent);
		assertThat(price.net()).isEqualTo(new BigDecimal(net));
		assertThat(price.vat()).isEqualTo(new BigDecimal(vat));
		assertThat(price.gross()).isEqualTo(new BigDecimal(gross));
	}

	@ParameterizedTest
	@CsvSource({
			"LAP-001, NL,  21,  899.00, 188.79, 1087.79",
			"LAP-001, CH, 8.1,  864.42,  70.02,  934.44",
			"LAP-001, GB,  20,  719.20, 143.84,  863.04",
			"BOK-001, GB,   0,   10.00,   0.00,   10.00"
	})
	void pricesInTheCurrencyAndWithTheVatOfTheStorefront(String productId, Country country, String vatPercent,
			String net, String vat, String gross) {
		Product product = catalogue.find(productId).orElseThrow();

		Price price = pricing.price(product.price(), product, country);

		assertThat(price.vatPercent()).isEqualByComparingTo(vatPercent);
		assertThat(price.net()).isEqualTo(new BigDecimal(net));
		assertThat(price.vat()).isEqualTo(new BigDecimal(vat));
		assertThat(price.gross()).isEqualTo(new BigDecimal(gross));
	}

	@Test
	void pricesTheCartInTheCurrencyOfTheStorefront() {
		CartView cart = new CartView(List.of(CartLine.of(catalogue.find("LAP-001").orElseThrow(), 1)),
				new BigDecimal("899.00"));

		assertThat(pricing.price(cart, Country.DE).currency()).isEqualTo("EUR");
		assertThat(pricing.price(cart, Country.CH).currency()).isEqualTo("CHF");
		assertThat(pricing.price(cart, Country.GB).currency()).isEqualTo("GBP");
	}

	@Test
	void roundsNetAndVatHalfUp() {
		// 24.98 EUR / 0.33 = 75.6969... PLN, and 5% of 75.70 is exactly 3.785
		Price price = pricing.price(new BigDecimal("24.98"), product("T-1", Category.BOOK, "12.50", "5"), Country.PL);

		assertThat(price.net()).isEqualTo(new BigDecimal("75.70"));
		assertThat(price.vat()).isEqualTo(new BigDecimal("3.79"));
		assertThat(price.gross()).isEqualTo(new BigDecimal("79.49"));
	}

	@Test
	void cartTotalsAreTheSumOfTheRoundedLinesAndTheShipping() {
		Product laptop = product("T-1", Category.LAPTOP, "899.00", "23");
		Product book = product("T-2", Category.BOOK, "12.50", "5");
		CartView cart = new CartView(List.of(CartLine.of(laptop, 1), CartLine.of(book, 2)), new BigDecimal("924.00"));

		PricedCart priced = pricing.price(cart, Country.PL);

		assertThat(priced.currency()).isEqualTo("PLN");
		assertThat(priced.eurPerUnit()).isEqualByComparingTo("0.33");
		assertThat(priced.rateQuarter()).isEqualTo("2026-Q4");
		// shipping: 5.00 EUR / 0.33 = 15.15 PLN, plus 23% VAT
		assertThat(priced.shipping().net()).isEqualTo(new BigDecimal("15.15"));
		assertThat(priced.shipping().vat()).isEqualTo(new BigDecimal("3.48"));
		assertThat(priced.shipping().gross()).isEqualTo(new BigDecimal("18.63"));
		assertThat(priced.net()).isEqualTo(new BigDecimal("2815.15"));
		assertThat(priced.vat()).isEqualTo(new BigDecimal("633.85"));
		assertThat(priced.gross()).isEqualTo(new BigDecimal("3449.00"));
	}

	@ParameterizedTest
	@CsvSource({"NL, 21", "DE, 19", "FR, 20", "PL, 23", "GB, 20", "CH, 2.6"})
	void shippingIsTaxedAtTheHighRateExceptInSwitzerlandWhichUsesItsLowRate(Country country, String vatPercent) {
		CartView cart = new CartView(List.of(CartLine.of(catalogue.find("BOK-001").orElseThrow(), 1)),
				new BigDecimal("12.50"));

		assertThat(pricing.price(cart, country).shipping().vatPercent()).isEqualByComparingTo(vatPercent);
	}

	@Test
	void shippingIsChargedOncePerOrderNotPerProduct() {
		Product book = product("T-2", Category.BOOK, "12.50", "5");

		PricedCart one = pricing.price(new CartView(List.of(CartLine.of(book, 1)), new BigDecimal("12.50")), Country.PL);
		PricedCart three = pricing.price(new CartView(List.of(CartLine.of(book, 3)), new BigDecimal("37.50")), Country.PL);

		assertThat(three.shipping()).isEqualTo(one.shipping());
	}

	@Test
	void shippingIsPricedInTheCurrencyAndAtTheStandardVatOfTheStorefront() {
		CartView cart = new CartView(List.of(CartLine.of(catalogue.find("BOK-001").orElseThrow(), 1)),
				new BigDecimal("12.50"));

		Price germanShipping = pricing.price(cart, Country.DE).shipping();
		Price britishShipping = pricing.price(cart, Country.GB).shipping();

		assertThat(germanShipping.vatPercent()).isEqualByComparingTo("19");
		assertThat(germanShipping.net()).isEqualTo(new BigDecimal("5.00"));
		assertThat(germanShipping.vat()).isEqualTo(new BigDecimal("0.95"));
		// 5.00 EUR / 1.25 = 4.00 GBP, plus 20% VAT
		assertThat(britishShipping.net()).isEqualTo(new BigDecimal("4.00"));
		assertThat(britishShipping.vat()).isEqualTo(new BigDecimal("0.80"));
	}

	@ParameterizedTest
	@CsvSource({
			"NL, EUR, 9,   8.99, 0.81, 9.80",
			"DE, EUR, 7,   8.99, 0.63, 9.62",
			"FR, EUR, 5.5, 8.99, 0.49, 9.48",
			"PL, PLN, 5,  27.24, 1.36, 28.60",
			"CH, CHF, 2.6, 8.64, 0.22, 8.86",
			"GB, GBP, 0,   7.19, 0.00, 7.19"
	})
	void ebookIsTaxedWithTheVatOfTheCountryTheCustomerOrdersIn(
			Country country, String currency, String vatPercent, String net, String vat, String gross) {
		CartView cart = new CartView(List.of(CartLine.of(catalogue.find("EBK-001").orElseThrow(), 1)),
				new BigDecimal("8.99"));

		PricedCart priced = pricing.price(cart, country);

		assertThat(priced.shipping()).isNull();
		assertThat(priced.currency()).isEqualTo(currency);
		assertThat(priced.lines().get(0).price().vatPercent()).isEqualByComparingTo(vatPercent);
		assertThat(priced.net()).isEqualTo(new BigDecimal(net));
		assertThat(priced.vat()).isEqualTo(new BigDecimal(vat));
		assertThat(priced.gross()).isEqualTo(new BigDecimal(gross));
	}

	@Test
	void theQuarterOfTheOrderDateDecidesTheExchangeRate() {
		CartView cart = new CartView(List.of(CartLine.of(catalogue.find("EBK-001").orElseThrow(), 1)),
				new BigDecimal("8.99"));

		PricedCart inQ3 = pricing.price(cart, Country.PL, Instant.parse("2026-08-15T10:00:00Z"));
		PricedCart inQ4 = pricing.price(cart, Country.PL, Instant.parse("2026-11-15T10:00:00Z"));

		assertThat(inQ3.rateQuarter()).isEqualTo("2026-Q3");
		assertThat(inQ3.eurPerUnit()).isEqualByComparingTo("0.23");
		assertThat(inQ3.net()).isEqualTo(new BigDecimal("39.09"));
		assertThat(inQ4.rateQuarter()).isEqualTo("2026-Q4");
		assertThat(inQ4.eurPerUnit()).isEqualByComparingTo("0.33");
		assertThat(inQ4.net()).isEqualTo(new BigDecimal("27.24"));
	}

	@Test
	void theQuarterChangesAtMidnightInTheNetherlandsNotInUtc() {
		CartView cart = new CartView(List.of(CartLine.of(catalogue.find("EBK-001").orElseThrow(), 1)),
				new BigDecimal("8.99"));

		// 2026-10-01 00:00 in Amsterdam (UTC+2) is 2026-09-30 22:00 UTC
		PricedCart justBefore = pricing.price(cart, Country.GB, Instant.parse("2026-09-30T21:59:59Z"));
		PricedCart justAfter = pricing.price(cart, Country.GB, Instant.parse("2026-09-30T22:00:00Z"));

		assertThat(justBefore.rateQuarter()).isEqualTo("2026-Q3");
		assertThat(justBefore.eurPerUnit()).isEqualByComparingTo("1.20");
		assertThat(justAfter.rateQuarter()).isEqualTo("2026-Q4");
		assertThat(justAfter.eurPerUnit()).isEqualByComparingTo("1.25");
	}

	@Test
	void aQuarterWithoutRatesIsAnErrorNotAFallbackToAnotherQuarter() {
		CartView cart = new CartView(List.of(CartLine.of(catalogue.find("EBK-001").orElseThrow(), 1)),
				new BigDecimal("8.99"));
		Instant noTableYet = Instant.parse("2027-01-10T10:00:00Z");

		assertThatThrownBy(() -> pricing.price(cart, Country.PL, noTableYet))
				.isInstanceOf(MissingExchangeRatesException.class)
				.hasMessageContaining("2027-Q1");
		// euro storefronts need no exchange rate
		assertThat(pricing.price(cart, Country.DE, noTableYet).eurPerUnit()).isEqualByComparingTo("1");
	}

	@Test
	void digitalOnlyCartHasNoShipping() {
		Product ebook = product("T-3", Category.EBOOK, "10.00", "5");

		PricedCart priced = pricing.price(new CartView(List.of(CartLine.of(ebook, 1)), new BigDecimal("10.00")), Country.PL);

		assertThat(priced.shipping()).isNull();
		assertThat(priced.gross()).isEqualTo(priced.lines().get(0).price().gross());
	}

	@Test
	void emptyCartCostsNothing() {
		PricedCart priced = pricing.price(new CartView(List.of(), BigDecimal.ZERO), Country.PL);

		assertThat(priced.lines()).isEmpty();
		assertThat(priced.shipping()).isNull();
		assertThat(priced.gross()).isEqualByComparingTo("0");
	}

	@Test
	void usesTheVatOfTheProductNotOfItsCategory() {
		Product zeroRatedLaptop = product("T-1", Category.LAPTOP, "100.00", "0");

		Price price = pricing.price(new BigDecimal("100.00"), zeroRatedLaptop, Country.PL);

		assertThat(price.vatPercent()).isEqualByComparingTo("0");
		assertThat(price.vat()).isEqualByComparingTo("0");
	}

	private static Product product(String id, Category category, String price, String polishVatPercent) {
		return new Product(id, id, category, new BigDecimal(price), Country.NL,
				Map.of(Country.PL, new BigDecimal(polishVatPercent)));
	}

}
