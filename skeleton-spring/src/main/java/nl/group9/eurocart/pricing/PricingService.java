package nl.group9.eurocart.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import nl.group9.eurocart.cart.CartLine;
import nl.group9.eurocart.cart.CartView;
import nl.group9.eurocart.catalogue.Category;
import nl.group9.eurocart.catalogue.Country;
import nl.group9.eurocart.catalogue.Product;
import nl.group9.eurocart.catalogue.ProductCatalogue;

import org.springframework.stereotype.Service;

/**
 * Prices catalogue amounts (EUR, excl. VAT) for the storefront of a country: converts to the currency of that
 * country at the exchange rate of the quarter of the order date and adds the VAT the product has in that country.
 * Prices that are only shown (no order yet) use the rate of today.
 * Rounding is HALF_UP to 2 decimals, first the net amount, then the VAT over that net amount.
 * Physical products add a flat shipping fee per order.
 */
@Service
public class PricingService {

	/** Flat shipping fee per order with physical products, in EUR excl. VAT. */
	static final BigDecimal SHIPPING_EUR = new BigDecimal("5.00");

	/**
	 * VAT percentage on shipping per country: the high (standard) rate everywhere, except Switzerland, which
	 * uses its low rate. To be confirmed by compliance for the countries that are not live yet.
	 */
	static final Map<Country, BigDecimal> SHIPPING_VAT_PERCENT = Map.of(
			Country.NL, new BigDecimal("21"),
			Country.DE, new BigDecimal("19"),
			Country.FR, new BigDecimal("20"),
			Country.PL, new BigDecimal("23"),
			Country.CH, new BigDecimal("2.6"),
			Country.GB, new BigDecimal("20"));

	private final ProductCatalogue catalogue;

	private final ExchangeRates rates;

	private final Clock clock;

	public PricingService(ProductCatalogue catalogue, ExchangeRates rates, Clock clock) {
		this.catalogue = catalogue;
		this.rates = rates;
		this.clock = clock;
	}

	/** Price per unit of every catalogue product in the storefront of {@code country} today, by product id. */
	public Map<String, Price> productPrices(Country country) {
		BigDecimal eurPerUnit = rates.eurPerUnit(country.currency(), clock.instant());
		return catalogue.all().stream()
				.collect(Collectors.toMap(Product::id, product -> price(product.price(), product.vatPercent(country), eurPerUnit)));
	}

	/** The cart priced with the exchange rate of today. */
	public PricedCart price(CartView cart, Country country) {
		return price(cart, country, clock.instant());
	}

	/** The cart priced as at {@code orderedAt}: that moment decides which quarterly exchange rate applies. */
	public PricedCart price(CartView cart, Country country, Instant orderedAt) {
		BigDecimal eurPerUnit = rates.eurPerUnit(country.currency(), orderedAt);
		List<PricedLine> lines = cart.lines().stream().map(line -> price(line, country, eurPerUnit)).toList();
		boolean physical = cart.lines().stream().anyMatch(line -> line.product().category() != Category.EBOOK);
		Price shipping = physical ? shipping(country, eurPerUnit) : null;
		return new PricedCart(country.currency(), eurPerUnit, rates.quarterOf(orderedAt), lines, shipping,
				sum(lines, shipping, Price::net), sum(lines, shipping, Price::vat), sum(lines, shipping, Price::gross));
	}

	/** The flat shipping fee of an order with physical products in the storefront of {@code country} today. */
	public Price shipping(Country country) {
		return shipping(country, rates.eurPerUnit(country.currency(), clock.instant()));
	}

	private Price shipping(Country country, BigDecimal eurPerUnit) {
		return price(SHIPPING_EUR, SHIPPING_VAT_PERCENT.get(country), eurPerUnit);
	}

	/** Prices {@code euros} (EUR, excl. VAT) of {@code product} in the storefront of {@code country} today. */
	Price price(BigDecimal euros, Product product, Country country) {
		return price(euros, product.vatPercent(country), rates.eurPerUnit(country.currency(), clock.instant()));
	}

	/** Prices {@code euros} (EUR, excl. VAT) with the given VAT percentage; one unit of the currency is worth {@code eurPerUnit} EUR. */
	private static Price price(BigDecimal euros, BigDecimal vatPercent, BigDecimal eurPerUnit) {
		BigDecimal net = euros.divide(eurPerUnit, 2, RoundingMode.HALF_UP);
		BigDecimal vat = net.multiply(vatPercent).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
		return new Price(vatPercent, net, vat, net.add(vat));
	}

	private static PricedLine price(CartLine line, Country country, BigDecimal eurPerUnit) {
		return new PricedLine(line.product(), line.quantity(),
				price(line.lineTotal(), line.product().vatPercent(country), eurPerUnit));
	}

	private static BigDecimal sum(List<PricedLine> lines, Price shipping, Function<Price, BigDecimal> amount) {
		BigDecimal goods = lines.stream()
				.map(line -> amount.apply(line.price()))
				.reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
		return shipping == null ? goods : goods.add(amount.apply(shipping));
	}

}
