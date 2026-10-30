package nl.group9.eurocart.notification;

import java.math.BigDecimal;

import nl.group9.eurocart.order.Order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Posts the amount of a paid order to a Power Automate flow, which mails the sales person:
 * {@code {"bedrag": 123.45, "valuta": "EUR"}}. The amount is the gross total of the order in EUR (converted with the
 * rate stored on the order), rounded to 2 decimals in {@link nl.group9.eurocart.pricing.PricedCart#grossEur()}.
 *
 * <p>A notification is sent in the background: the customer has paid and must not notice a slow or failing flow.
 * Without a URL nothing is sent.
 */
@Component
public class PowerAutomateSalesNotifier implements SalesNotifier {

	private static final Logger log = LoggerFactory.getLogger(PowerAutomateSalesNotifier.class);

	static final String CURRENCY = "EUR";

	private final FlowClient flow;

	public PowerAutomateSalesNotifier(@Value("${eurocart.sales-notification.url:}") String url, ObjectMapper objectMapper) {
		this.flow = new FlowClient(url, "eurocart.sales-notification.url", objectMapper);
		if (!flow.configured()) {
			log.info("No sales notification URL configured: orders are not announced to the sales person");
		}
	}

	private record Payload(BigDecimal bedrag, String valuta) {
	}

	@Override
	public void orderPaid(Order order) {
		if (flow.configured()) {
			Thread.ofVirtual().name("sales-notification-" + order.id()).start(() -> send(order));
		}
	}

	/** Sends the notification now. Never throws; returns whether the flow accepted it. */
	boolean send(Order order) {
		BigDecimal amount = order.pricing().grossEur();
		if (amount == null) {
			log.warn("Sales notification of order {} skipped: the order has no exchange rate", order.id());
			return false;
		}
		return flow.post(new Payload(amount, CURRENCY), "Sales notification of order " + order.id());
	}

}
