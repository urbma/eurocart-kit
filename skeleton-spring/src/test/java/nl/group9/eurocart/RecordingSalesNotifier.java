package nl.group9.eurocart;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import nl.group9.eurocart.notification.SalesNotifier;
import nl.group9.eurocart.order.Order;

/** Replaces the real notifier in tests: nothing is ever sent, the orders it was asked about are kept. */
public class RecordingSalesNotifier implements SalesNotifier {

	private final List<Order> notified = new CopyOnWriteArrayList<>();

	@Override
	public void orderPaid(Order order) {
		notified.add(order);
	}

	public List<Order> notified() {
		return notified;
	}

	public void clear() {
		notified.clear();
	}

}
