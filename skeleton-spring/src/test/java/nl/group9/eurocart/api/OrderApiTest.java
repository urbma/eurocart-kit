package nl.group9.eurocart.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.group9.eurocart.RecordingSalesNotifier;
import nl.group9.eurocart.ShopTest;
import nl.group9.eurocart.cart.CartService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ShopTest
class OrderApiTest {

	private static final Path ORDERS_DIR = createOrdersDir();

	private static final String ADDRESS = """
			{"fullName":"Anna Kowalska","street":"Marszalkowska","houseNumber":"10/5",
			 "postalCode":"00-950","city":"Warszawa","country":"PL","phone":"+48 600 100 200"}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CartService cartService;

	@Autowired
	private RecordingSalesNotifier salesNotifier;

	@DynamicPropertySource
	static void ordersDirectory(DynamicPropertyRegistry registry) {
		registry.add("eurocart.orders.dir", ORDERS_DIR::toString);
	}

	@BeforeEach
	void emptyCart() {
		cartService.clear();
		salesNotifier.clear();
	}

	@Test
	void savesAPendingOrderWithShippingAndPricingSnapshot() throws Exception {
		cartService.add("LAP-001", 1);

		String response = mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"termsAccepted\":true,\"email\":\"anna@example.pl\",\"shipping\":" + ADDRESS + "}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.termsAcceptedAt").exists())
				.andExpect(jsonPath("$.email").value("anna@example.pl"))
				.andExpect(jsonPath("$.shipping.postalCode").value("00-950"))
				.andExpect(jsonPath("$.pricing.currency").value("PLN"))
				.andExpect(jsonPath("$.pricing.eurPerUnit").value(0.33))
				.andExpect(jsonPath("$.pricing.rateQuarter").value("2026-Q4"))
				.andExpect(jsonPath("$.pricing.lines[0].price.vatPercent").value(23))
				.andExpect(jsonPath("$.pricing.shipping.gross").value(18.63))
				.andExpect(jsonPath("$.pricing.gross").value(3369.45))
				.andReturn().getResponse().getContentAsString();

		String id = idOf(response);
		assertThat(ORDERS_DIR.resolve(id + ".json")).exists();
		mockMvc.perform(get("/api/orders/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.shipping.fullName").value("Anna Kowalska"))
				.andExpect(jsonPath("$.shipping.phone").value("+48 600 100 200"))
				.andExpect(jsonPath("$.pricing.gross").value(3369.45));
	}

	@Test
	void digitalOnlyOrderNeedsNoShippingAddress() throws Exception {
		cartService.add("EBK-001", 1);

		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"termsAccepted\":true,\"email\":\"anna@example.pl\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shipping").doesNotExist())
				.andExpect(jsonPath("$.pricing.shipping").doesNotExist());
	}

	@Test
	void pricesTheOrderInTheStorefrontItWasPlacedIn() throws Exception {
		cartService.add("EBK-001", 1);

		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"termsAccepted\":true,\"email\":\"jane@example.co.uk\",\"storefront\":\"GB\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.pricing.currency").value("GBP"))
				.andExpect(jsonPath("$.pricing.eurPerUnit").value(1.25))
				.andExpect(jsonPath("$.pricing.lines[0].price.vatPercent").value(0));
	}

	@Test
	void physicalProductsRequireShipping() throws Exception {
		cartService.add("LAP-001", 1);

		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"anna@example.pl\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Shipping details are required for physical products"));
	}

	@Test
	void rejectsIncompleteOrInvalidDetails() throws Exception {
		cartService.add("LAP-001", 1);

		placeExpecting("{\"email\":\"anna@example.pl\",\"shipping\":" + ADDRESS.replace("\"street\":\"Marszalkowska\"", "\"street\":\" \"") + "}",
				"Street is required");
		placeExpecting("{\"email\":\"anna@example.pl\",\"shipping\":" + ADDRESS.replace("00-950", "950") + "}",
				"Postal code must look like 00-950");
		placeExpecting("{\"email\":\"anna@example.pl\",\"shipping\":" + ADDRESS.replace("\"PL\"", "\"DE\"") + "}",
				"We currently only deliver to Poland (PL)");
		placeExpecting("{\"email\":\"not-an-email\",\"shipping\":" + ADDRESS + "}",
				"Email is not a valid email address");
	}

	@Test
	void requiresAcceptanceOfTheTermsAndConditions() throws Exception {
		cartService.add("LAP-001", 1);
		cartService.add("EBK-001", 1);

		placeExpecting("{\"email\":\"anna@example.pl\",\"shipping\":" + ADDRESS + "}",
				"You must accept the terms and conditions");
		placeExpecting("{\"termsAccepted\":false,\"email\":\"anna@example.pl\",\"shipping\":" + ADDRESS + "}",
				"You must accept the terms and conditions");
	}

	@Test
	void rejectsEmptyCart() throws Exception {
		placeExpecting("{\"email\":\"anna@example.pl\"}", "Your cart is empty");
	}

	@Test
	void paymentMarksOrderPaidAndEmptiesCart() throws Exception {
		cartService.add("LAP-001", 1);
		String id = idOf(mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"termsAccepted\":true,\"email\":\"anna@example.pl\",\"shipping\":" + ADDRESS + "}"))
				.andReturn().getResponse().getContentAsString());

		mockMvc.perform(post("/api/orders/" + id + "/payment")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"paypalOrderId\":\"PAYPAL-123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"))
				.andExpect(jsonPath("$.paypalOrderId").value("PAYPAL-123"));

		assertThat(cartService.view().lines()).isEmpty();
		mockMvc.perform(get("/api/orders/" + id))
				.andExpect(jsonPath("$.status").value("PAID"))
				.andExpect(jsonPath("$.paidAt").exists());
	}

	@Test
	void everyPaidOrderIsAnnouncedToTheSalesPersonOnce() throws Exception {
		cartService.add("EBK-002", 1);
		String id = idOf(mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"termsAccepted\":true,\"email\":\"anna@example.pl\",\"storefront\":\"PL\"}"))
				.andReturn().getResponse().getContentAsString());
		// placing the order is not a sale yet
		assertThat(salesNotifier.notified()).isEmpty();

		mockMvc.perform(post("/api/orders/" + id + "/payment")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"paypalOrderId\":\"SIM-1\"}"))
				.andExpect(status().isOk());

		assertThat(salesNotifier.notified()).hasSize(1);
		assertThat(salesNotifier.notified().get(0).id()).isEqualTo(id);
		// 47.69 PLN * 0.33 = 15.74 EUR
		assertThat(salesNotifier.notified().get(0).pricing().grossEur()).isEqualTo(new java.math.BigDecimal("15.74"));

		// the same payment again changes nothing and is not announced again
		mockMvc.perform(post("/api/orders/" + id + "/payment")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"paypalOrderId\":\"SIM-1\"}"))
				.andExpect(status().isOk());
		assertThat(salesNotifier.notified()).hasSize(1);
	}

	@Test
	void aRejectedPaymentIsNotAnnounced() throws Exception {
		cartService.add("EBK-002", 1);
		String id = idOf(mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"termsAccepted\":true,\"email\":\"anna@example.pl\"}"))
				.andReturn().getResponse().getContentAsString());

		mockMvc.perform(post("/api/orders/" + id + "/payment")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest());

		assertThat(salesNotifier.notified()).isEmpty();
	}

	@Test
	void theEurAmountIsNotPartOfTheSavedOrder() throws Exception {
		cartService.add("EBK-002", 1);

		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"termsAccepted\":true,\"email\":\"anna@example.pl\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.pricing.grossEur").doesNotExist());
	}

	@Test
	void unknownOrderGivesNotFound() throws Exception {
		mockMvc.perform(get("/api/orders/00000000-0000-0000-0000-000000000000"))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/orders/not-a-uuid"))
				.andExpect(status().isNotFound());
	}

	private void placeExpecting(String body, String detail) throws Exception {
		mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(detail));
	}

	private static String idOf(String json) {
		Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"").matcher(json);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private static Path createOrdersDir() {
		try {
			return Files.createTempDirectory("eurocart-orders");
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
