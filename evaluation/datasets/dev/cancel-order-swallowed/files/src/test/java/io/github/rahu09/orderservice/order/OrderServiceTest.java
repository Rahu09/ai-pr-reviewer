package io.github.rahu09.orderservice.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderServiceTest {

	private final OrderService service = new OrderService(new InMemoryOrderRepository());

	private CreateOrderRequest request() {
		return new CreateOrderRequest("c-1", List.of(new CreateOrderRequest.Item("sku-1", 2, new BigDecimal("5.00"))));
	}

	@Test
	void createsAndFindsAnOrder() {
		Order created = service.create(request());

		assertThat(service.get(created.id())).isEqualTo(created);
		assertThat(created.status()).isEqualTo(OrderStatus.CREATED);
		assertThat(created.total()).isEqualByComparingTo("10.00");
	}

	@Test
	void cancelsAnOrder() {
		Order created = service.create(request());

		assertThat(service.cancel(created.id()).status()).isEqualTo(OrderStatus.CANCELLED);
	}

	@Test
	void unknownOrderIsNotFound() {
		assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(OrderNotFoundException.class);
	}
}
