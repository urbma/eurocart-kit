package nl.group9.eurocart.finance;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import nl.group9.eurocart.order.Order;
import nl.group9.eurocart.order.OrderRepository;
import nl.group9.eurocart.order.OrderService;
import nl.group9.eurocart.order.OrderStatus;
import nl.group9.eurocart.pricing.PricedCart;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceServiceTest {

	@Test
	void orderWithoutStoredRateHasUnknownEurValueInsteadOfFailing() {
		PricedCart legacy = new PricedCart("PLN", null, List.of(), null,
				new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.00"));
		PricedCart current = new PricedCart("PLN", new BigDecimal("0.23"), List.of(), null,
				new BigDecimal("100.00"), new BigDecimal("23.00"), new BigDecimal("123.00"));
		FinanceService finance = financeWith(
				order("1", Instant.parse("2026-10-30T10:00:00Z"), legacy),
				order("2", Instant.parse("2026-10-30T11:00:00Z"), current));

		FinanceOverview overview = finance.overview();

		assertThat(overview.orders()).extracting(FinanceOrder::id).containsExactly("2", "1");
		assertThat(overview.orders().get(0).grossEur()).isEqualTo(new BigDecimal("28.29"));
		assertThat(overview.orders().get(1).grossEur()).isNull();
		assertThat(overview.paidTotals()).hasSize(1);
		assertThat(overview.paidTotals().get(0).gross()).isEqualTo(new BigDecimal("246.00"));
		assertThat(overview.paidTotals().get(0).grossEur()).isNull();
	}

	private static Order order(String id, Instant createdAt, PricedCart pricing) {
		return new Order(id, createdAt, OrderStatus.PAID, "a@b.pl", null, pricing, "SIM-" + id, createdAt, createdAt);
	}

	private static FinanceService financeWith(Order... saved) {
		OrderRepository repository = new OrderRepository() {
			@Override
			public void save(Order order) {
			}

			@Override
			public Optional<Order> find(String id) {
				return Optional.empty();
			}

			@Override
			public List<Order> findAll() {
				return List.of(saved);
			}
		};
		return new FinanceService(new OrderService(null, null, repository, Clock.systemUTC()));
	}

}
