package nl.group9.eurocart.api;

import java.util.Optional;

import nl.group9.eurocart.ShopTest;
import nl.group9.eurocart.geo.IpCountryLookup;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ShopTest
class GeoApiTest {

	@TestConfiguration
	static class StubLookup {

		@Bean
		@Primary
		IpCountryLookup ipCountryLookup() {
			return ip -> ip.equals("93.184.216.34") ? Optional.of("DE") : Optional.empty();
		}

	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void usesTheCountryOfTheForwardedClientIp() throws Exception {
		mockMvc.perform(get("/api/geo/country").header("X-Forwarded-For", "93.184.216.34, 10.0.0.1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.country").value("DE"))
				.andExpect(jsonPath("$.located").value(true));
	}

	@Test
	void fallsBackToPolandForALocalVisitor() throws Exception {
		mockMvc.perform(get("/api/geo/country"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.country").value("PL"))
				.andExpect(jsonPath("$.located").value(false));
	}

	@Test
	void fallsBackToPolandWhenTheIpCannotBeLocated() throws Exception {
		mockMvc.perform(get("/api/geo/country").header("X-Forwarded-For", "203.0.113.9"))
				.andExpect(jsonPath("$.country").value("PL"))
				.andExpect(jsonPath("$.located").value(false));
	}

}
