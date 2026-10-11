package io.github.rahu09.orderservice.order;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryOrderRepository implements OrderRepository {

	private final Map<UUID, Order> orders = new ConcurrentHashMap<>();

	@Override
	public Order save(Order order) {
		orders.put(order.id(), order);
		return order;
	}

	@Override
	public List<Order> findAll() {
		return new ArrayList<>(orders.values());
	}

	@Override
	public Optional<Order> findById(UUID id) {
		return Optional.ofNullable(orders.get(id));
	}
}
