package nl.group9.eurocart.order;

import java.time.Instant;

import nl.group9.eurocart.pricing.PricedCart;

/**
 * A saved order. {@code pricing} is a snapshot (exchange rate, VAT percentages and rounded amounts at the
 * time of ordering) so the order can be audited later. {@code shipping} is null for digital-only orders.
 * {@code termsAcceptedAt} is when the customer accepted the terms and conditions; null for orders from before
 * that was required.
 */
public record Order(
		String id,
		Instant createdAt,
		OrderStatus status,
		String email,
		ShippingAddress shipping,
		PricedCart pricing,
		String paypalOrderId,
		Instant paidAt,
		Instant termsAcceptedAt) {

	public Order paid(String paypalOrderId, Instant paidAt) {
		return new Order(id, createdAt, OrderStatus.PAID, email, shipping, pricing, paypalOrderId, paidAt,
				termsAcceptedAt);
	}

}
