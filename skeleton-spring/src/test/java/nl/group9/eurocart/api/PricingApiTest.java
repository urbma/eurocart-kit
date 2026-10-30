package nl.group9.eurocart.api;

import nl.group9.eurocart.ShopTest;
import nl.group9.eurocart.cart.CartService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ShopTest
class PricingApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CartService cartService;

	@BeforeEach
	void emptyCart() {
		cartService.clear();
	}

	@Test
	void pricesEveryProductInPln() throws Exception {
		mockMvc.perform(get("/api/pricing/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(51))
				.andExpect(jsonPath("$['LAP-001'].vatPercent").value(23))
				.andExpect(jsonPath("$['LAP-001'].net").value(2724.24))
				.andExpect(jsonPath("$['LAP-001'].vat").value(626.58))
				.andExpect(jsonPath("$['LAP-001'].gross").value(3350.82));
	}

	@Test
	void pricesTheCart() throws Exception {
		cartService.add("LAP-001", 1);
		cartService.add("BOK-001", 2);

		mockMvc.perform(get("/api/pricing/cart"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currency").value("PLN"))
				.andExpect(jsonPath("$.lines.length()").value(2))
				.andExpect(jsonPath("$.lines[1].price.gross").value(79.55))
				.andExpect(jsonPath("$.shipping.net").value(15.15))
				.andExpect(jsonPath("$.shipping.gross").value(18.63))
				.andExpect(jsonPath("$.net").value(2815.15))
				.andExpect(jsonPath("$.vat").value(633.85))
				.andExpect(jsonPath("$.gross").value(3449.00));
	}

	@Test
	void listsAStorefrontPerCountry() throws Exception {
		mockMvc.perform(get("/api/pricing/storefronts"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(6))
				.andExpect(jsonPath("$[0].country").value("NL"))
				.andExpect(jsonPath("$[0].currency").value("EUR"));
	}

	@Test
	void pricesForTheRequestedStorefront() throws Exception {
		cartService.add("LAP-001", 1);

		mockMvc.perform(get("/api/pricing/products").param("country", "NL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$['LAP-001'].vatPercent").value(21))
				.andExpect(jsonPath("$['LAP-001'].gross").value(1087.79));
		mockMvc.perform(get("/api/pricing/cart").param("country", "NL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currency").value("EUR"))
				.andExpect(jsonPath("$.eurPerUnit").value(1))
				.andExpect(jsonPath("$.shipping.gross").value(6.05))
				.andExpect(jsonPath("$.gross").value(1093.84));
	}

	@Test
	void pricesTheShippingFeeForTheRequestedStorefront() throws Exception {
		mockMvc.perform(get("/api/pricing/shipping").param("country", "PL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.net").value(15.15))
				.andExpect(jsonPath("$.gross").value(18.63));
		mockMvc.perform(get("/api/pricing/shipping").param("country", "NL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.vatPercent").value(21))
				.andExpect(jsonPath("$.gross").value(6.05));
	}

	@Test
	void rejectsAnUnknownStorefront() throws Exception {
		mockMvc.perform(get("/api/pricing/cart").param("country", "XX"))
				.andExpect(status().isBadRequest());
	}

}
