package io.github.rahu09.orderservice.order;

import java.util.Comparator;
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
	public List<Order> findPage(int page, int size) {
		return orders.values().stream()
				.sorted(Comparator.comparing(Order::createdAt).thenComparing(Order::id))
				.skip((long) page * size)
				.limit(size)
				.toList();
	}

	@Override
	public long count() {
		return orders.size();
	}

	@Override
	public Optional<Order> findById(UUID id) {
		return Optional.ofNullable(orders.get(id));
	}
}
