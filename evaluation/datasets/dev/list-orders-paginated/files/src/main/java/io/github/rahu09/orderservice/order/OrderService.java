package io.github.rahu09.orderservice.order;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

	static final int MAX_PAGE_SIZE = 100;

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

	public PageResponse<Order> list(int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
		return new PageResponse<>(repository.findPage(safePage, safeSize), safePage, safeSize, repository.count());
	}

	public Order get(UUID id) {
		return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
	}
}
