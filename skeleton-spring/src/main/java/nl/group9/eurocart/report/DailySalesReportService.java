package nl.group9.eurocart.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import nl.group9.eurocart.order.Order;
import nl.group9.eurocart.order.OrderService;
import nl.group9.eurocart.order.OrderStatus;
import nl.group9.eurocart.pricing.ExchangeRates;

import org.springframework.stereotype.Service;

/**
 * The sales of one day: the orders that were paid on that day (in the time zone of the shop), and what they brought
 * in, as the gross total in EUR. These are the same orders, and the same amounts, the sales person was told about
 * one by one; an order without a known EUR value was not announced and is not counted.
 */
@Service
public class DailySalesReportService {

	private final OrderService orders;

	public DailySalesReportService(OrderService orders) {
		this.orders = orders;
	}

	public DailySalesReport reportFor(LocalDate day) {
		List<BigDecimal> amounts = orders.all().stream()
				.filter(order -> order.status() == OrderStatus.PAID && order.paidAt() != null)
				.filter(order -> order.paidAt().atZone(ExchangeRates.SHOP_ZONE).toLocalDate().equals(day))
				.map(order -> order.pricing().grossEur())
				.filter(amount -> amount != null)
				.toList();
		BigDecimal total = amounts.stream().reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
		return new DailySalesReport(amounts.size(), total);
	}

}
