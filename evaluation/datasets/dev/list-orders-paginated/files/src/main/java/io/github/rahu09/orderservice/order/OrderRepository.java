package io.github.rahu09.orderservice.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

	Order save(Order order);

	List<Order> findPage(int page, int size);

	long count();

	Optional<Order> findById(UUID id);
}
