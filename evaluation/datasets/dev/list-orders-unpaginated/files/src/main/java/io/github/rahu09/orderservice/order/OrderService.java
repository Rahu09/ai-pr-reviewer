package io.github.rahu09.orderservice.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

	private final OrderRepository repository;

	public OrderService(OrderRepository repository) {
		this.repository = repository;
	}

	public Order create(CreateOrderRequest request) {
		var items = request.items().stream()
				.map(i -> new OrderItem(i.sku(), i.quantity(), i.unitPrice()))
				.toList();
		return repository.save(new Order(UUID.randomUUID(), request.customerId(), items, OrderStatus.CREATED, Instant.now()));
	}

	public List<Order> list() {
		return repository.findAll();
	}

	public Order get(UUID id) {
		return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
	}
}
