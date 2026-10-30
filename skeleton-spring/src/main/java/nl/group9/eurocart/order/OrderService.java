package nl.group9.eurocart.order;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import nl.group9.eurocart.cart.CartService;
import nl.group9.eurocart.cart.CartView;
import nl.group9.eurocart.catalogue.Category;
import nl.group9.eurocart.catalogue.Country;
import nl.group9.eurocart.pricing.PricedCart;
import nl.group9.eurocart.pricing.PricingService;

import org.springframework.stereotype.Service;

@Service
public class OrderService {

	/** Polish postal code, e.g. 00-950. */
	private static final Pattern POLISH_POSTAL_CODE = Pattern.compile("\\d{2}-\\d{3}");

	private final CartService cart;

	private final PricingService pricing;

	private final OrderRepository orders;

	private final Clock clock;

	public OrderService(CartService cart, PricingService pricing, OrderRepository orders, Clock clock) {
		this.cart = cart;
		this.pricing = pricing;
		this.orders = orders;
		this.clock = clock;
	}

	/** Saves the current cart as a pending order, priced as it is right now. The cart itself is left alone. */
	public Order place(OrderRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Order details are required");
		}
		CartView view = cart.view();
		if (view.lines().isEmpty()) {
			throw new IllegalArgumentException("Your cart is empty");
		}
		String email = validEmail(request.email());
		boolean physical = view.lines().stream().anyMatch(line -> line.product().category() != Category.EBOOK);
		ShippingAddress shipping = physical ? validShipping(request.shipping()) : null;
		if (!Boolean.TRUE.equals(request.termsAccepted())) {
			throw new IllegalArgumentException("You must accept the terms and conditions");
		}
		PricedCart priced = pricing.price(view, request.storefront() == null ? Country.PL : request.storefront());
		Instant now = clock.instant();
		Order order = new Order(UUID.randomUUID().toString(), now, OrderStatus.PENDING,
				email, shipping, priced, null, null, now);
		orders.save(order);
		return order;
	}

	/** Marks the order as paid and empties the cart. */
	public Order pay(String id, PaymentRequest request) {
		Order order = get(id);
		String paypalOrderId = required(request == null ? null : request.paypalOrderId(), "PayPal order id");
		if (order.status() == OrderStatus.PAID) {
			if (paypalOrderId.equals(order.paypalOrderId())) {
				return order;
			}
			throw new IllegalArgumentException("Order " + id + " is already paid");
		}
		Order paid = order.paid(paypalOrderId, clock.instant());
		orders.save(paid);
		cart.clear();
		return paid;
	}

	/** All saved orders, newest first. */
	public List<Order> all() {
		return orders.findAll().stream()
				.sorted(Comparator.comparing(Order::createdAt).reversed())
				.toList();
	}

	public Order get(String id) {
		return orders.find(id).orElseThrow(() -> new OrderNotFoundException(id));
	}

	private static String validEmail(String email) {
		String value = required(email, "Email");
		if (!value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
			throw new IllegalArgumentException("Email is not a valid email address");
		}
		return value;
	}

	private static ShippingAddress validShipping(ShippingAddress address) {
		if (address == null) {
			throw new IllegalArgumentException("Shipping details are required for physical products");
		}
		String postalCode = required(address.postalCode(), "Postal code");
		if (!POLISH_POSTAL_CODE.matcher(postalCode).matches()) {
			throw new IllegalArgumentException("Postal code must look like 00-950");
		}
		String country = required(address.country(), "Country").toUpperCase();
		if (!country.equals("PL")) {
			throw new IllegalArgumentException("We currently only deliver to Poland (PL)");
		}
		String phone = address.phone() == null || address.phone().isBlank() ? null : address.phone().trim();
		return new ShippingAddress(
				required(address.fullName(), "Full name"),
				required(address.street(), "Street"),
				required(address.houseNumber(), "House number"),
				postalCode,
				required(address.city(), "City"),
				country,
				phone);
	}

	private static String required(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + " is required");
		}
		return value.trim();
	}

}
