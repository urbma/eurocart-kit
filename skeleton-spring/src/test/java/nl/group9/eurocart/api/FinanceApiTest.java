package nl.group9.eurocart.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.group9.eurocart.cart.CartService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FinanceApiTest {

	private static final Path ORDERS_DIR = createOrdersDir();

	private static final String ADDRESS = """
			{"fullName":"Anna Kowalska","street":"Marszalkowska","houseNumber":"10/5",
			 "postalCode":"00-950","city":"Warszawa","country":"PL"}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CartService cartService;

	@DynamicPropertySource
	static void ordersDirectory(DynamicPropertyRegistry registry) {
		registry.add("eurocart.orders.dir", ORDERS_DIR::toString);
	}

	@Test
	void listsOrdersNewestFirstWithTotalsOfThePaidOnes() throws Exception {
		cartService.clear();
		cartService.add("LAP-001", 1);
		String paidId = place("{\"termsAccepted\":true,\"email\":\"anna@example.pl\",\"shipping\":" + ADDRESS + "}");
		mockMvc.perform(post("/api/orders/" + paidId + "/payment")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"paypalOrderId\":\"SIM-1\"}"))
				.andExpect(status().isOk());
		cartService.add("EBK-001", 1);
		place("{\"termsAccepted\":true,\"email\":\"jan@example.pl\"}");

		mockMvc.perform(get("/api/finance/orders"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.orders.length()").value(2))
				.andExpect(jsonPath("$.orders[0].status").value("PENDING"))
				.andExpect(jsonPath("$.orders[0].shippingNet").value(0.00))
				.andExpect(jsonPath("$.orders[1].id").value(paidId))
				.andExpect(jsonPath("$.orders[1].currency").value("PLN"))
				.andExpect(jsonPath("$.orders[1].eurPerUnit").value(0.23))
				.andExpect(jsonPath("$.orders[1].net").value(3930.44))
				.andExpect(jsonPath("$.orders[1].shippingNet").value(21.74))
				.andExpect(jsonPath("$.orders[1].vat").value(904.00))
				.andExpect(jsonPath("$.orders[1].gross").value(4834.44))
				.andExpect(jsonPath("$.orders[1].grossEur").value(1111.92))
				.andExpect(jsonPath("$.orders[1].lines[0].productId").value("LAP-001"))
				.andExpect(jsonPath("$.orders[1].lines[0].vatPercent").value(23))
				// only the paid order counts towards the totals
				.andExpect(jsonPath("$.paidTotals.length()").value(1))
				.andExpect(jsonPath("$.paidTotals[0].currency").value("PLN"))
				.andExpect(jsonPath("$.paidTotals[0].orders").value(1))
				.andExpect(jsonPath("$.paidTotals[0].gross").value(4834.44))
				.andExpect(jsonPath("$.paidTotals[0].grossEur").value(1111.92));
	}

	private String place(String body) throws Exception {
		String json = mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-f-]{36})\"").matcher(json);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private static Path createOrdersDir() {
		try {
			return Files.createTempDirectory("eurocart-finance-orders");
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
