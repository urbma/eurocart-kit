package nl.group9.eurocart.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The cart priced in {@code currency}; one unit of that currency was worth {@code eurPerUnit} EUR in the quarter
 * {@code rateQuarter}. {@code shipping} is null when the cart has only digital products. The totals are the sums of
 * the already rounded lines and the shipping.
 */
public record PricedCart(String currency, BigDecimal eurPerUnit, String rateQuarter, List<PricedLine> lines, Price shipping, BigDecimal net, BigDecimal vat, BigDecimal gross) {

	/**
	 * The gross amount converted to EUR with the exchange rate of the order, rounded to 2 decimals. Null for orders
	 * saved without a rate (by an older version): their EUR value is unknown.
	 */
	public BigDecimal grossEur() {
		return eurPerUnit == null ? null : gross.multiply(eurPerUnit).setScale(2, RoundingMode.HALF_UP);
	}

}
