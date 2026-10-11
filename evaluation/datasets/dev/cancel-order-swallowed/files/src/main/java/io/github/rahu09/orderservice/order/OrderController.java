package io.github.rahu09.orderservice.order;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

	private final OrderService service;

	public OrderController(OrderService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<Order> create(@Valid @RequestBody CreateOrderRequest request) {
		Order order = service.create(request);
		return ResponseEntity.created(URI.create("/orders/" + order.id())).body(order);
	}

	@PostMapping("/{id}/cancel")
	public Order cancel(@PathVariable UUID id) {
		return service.cancel(id);
	}

	@GetMapping("/{id}")
	public Order get(@PathVariable UUID id) {
		return service.get(id);
	}
}
