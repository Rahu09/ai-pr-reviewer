package io.github.rahu09.orderservice.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrderControllerTest {

	private final MockMvc mvc = MockMvcBuilders
			.standaloneSetup(new OrderController(new OrderService(new InMemoryOrderRepository())))
			.setControllerAdvice(new ApiExceptionHandler())
			.build();

	@Test
	void createReturns201WithLocation() throws Exception {
		mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
				{"customerId":"c-1","items":[{"sku":"sku-1","quantity":2,"unitPrice":5.00}]}
				"""))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.status").value("CREATED"));
	}

	@Test
	void listIsPaginated() throws Exception {
		for (int i = 0; i < 3; i++) {
			mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
					{"customerId":"c-1","items":[{"sku":"sku-1","quantity":1,"unitPrice":5.00}]}
					"""));
		}

		mvc.perform(get("/orders").param("page", "1").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.total").value(3));
	}

	@Test
	void oversizedPageIsCappedAt100() throws Exception {
		mvc.perform(get("/orders").param("size", "5000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(100));
	}

	@Test
	void invalidRequestReturns400() throws Exception {
		mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
				{"customerId":"","items":[]}
				"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unknownOrderReturns404() throws Exception {
		mvc.perform(get("/orders/" + UUID.randomUUID())).andExpect(status().isNotFound());
	}
}
