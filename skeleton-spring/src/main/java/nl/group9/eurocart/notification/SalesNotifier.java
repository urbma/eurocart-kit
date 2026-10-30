package nl.group9.eurocart.notification;

import nl.group9.eurocart.order.Order;

/** Tells the sales person about an order. */
public interface SalesNotifier {

	/** Called once for every order that has been paid. Must never make the payment fail. */
	void orderPaid(Order order);

}
