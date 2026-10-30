package nl.group9.eurocart.finance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import nl.group9.eurocart.order.Order;
import nl.group9.eurocart.order.OrderService;
import nl.group9.eurocart.order.OrderStatus;
import nl.group9.eurocart.pricing.PricedCart;
import nl.group9.eurocart.pricing.PricedLine;

import org.springframework.stereotype.Service;

/** Builds the order overview for finance from the saved orders: net, VAT and gross, plus EUR equivalents. */
@Service
public class FinanceService {

	private final OrderService orders;

	public FinanceService(OrderService orders) {
		this.orders = orders;
	}

	public FinanceOverview overview() {
		List<FinanceOrder> all = orders.all().stream().map(FinanceService::toFinanceOrder).toList();
		Map<String, List<FinanceOrder>> paidByCurrency = all.stream()
				.filter(order -> order.status().equals(OrderStatus.PAID.name()))
				.collect(Collectors.groupingBy(FinanceOrder::currency, TreeMap::new, Collectors.toList()));
		List<CurrencyTotal> totals = paidByCurrency.entrySet().stream()
				.map(entry -> total(entry.getKey(), entry.getValue()))
				.toList();
		return new FinanceOverview(all, totals);
	}

	private static FinanceOrder toFinanceOrder(Order order) {
		PricedCart pricing = order.pricing();
		BigDecimal shippingNet = pricing.shipping() == null ? BigDecimal.ZERO.setScale(2) : pricing.shipping().net();
		List<FinanceLine> lines = pricing.lines().stream().map(FinanceService::toFinanceLine).toList();
		return new FinanceOrder(order.id(), order.createdAt(), order.paidAt(), order.status().name(), order.email(),
				pricing.currency(), pricing.eurPerUnit(), pricing.net(), shippingNet, pricing.vat(), pricing.gross(),
				pricing.grossEur(), lines);
	}

	private static FinanceLine toFinanceLine(PricedLine line) {
		return new FinanceLine(line.product().id(), line.product().name(), line.product().category().name(),
				line.quantity(), line.price().vatPercent(), line.price().net(), line.price().vat(), line.price().gross());
	}

	private static CurrencyTotal total(String currency, List<FinanceOrder> orders) {
		return new CurrencyTotal(currency, orders.size(),
				sum(orders, FinanceOrder::net), sum(orders, FinanceOrder::vat),
				sum(orders, FinanceOrder::gross), sumOrNull(orders, FinanceOrder::grossEur));
	}

	/** Like {@link #sum}, but null as soon as one order has an unknown amount: an incomplete total would mislead. */
	private static BigDecimal sumOrNull(List<FinanceOrder> orders, Function<FinanceOrder, BigDecimal> amount) {
		return orders.stream().anyMatch(order -> amount.apply(order) == null) ? null : sum(orders, amount);
	}

	private static BigDecimal sum(List<FinanceOrder> orders, Function<FinanceOrder, BigDecimal> amount) {
		return orders.stream().map(amount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
	}


}
