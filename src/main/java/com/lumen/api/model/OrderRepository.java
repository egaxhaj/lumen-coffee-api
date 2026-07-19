package com.lumen.api.model;

import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory order store. No database. Not thread-safe beyond what a demo needs
 * (a real synchronized map would be used under real concurrency).
 */
@Repository
public class OrderRepository {

    private final Map<Long, Order> orders = new LinkedHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    public synchronized Order save(Order orderWithoutId) {
        long id = idGenerator.incrementAndGet();
        Order stored = new Order(id, orderWithoutId.getProductId(), orderWithoutId.getProductName(),
                orderWithoutId.getQuantity(), orderWithoutId.getUnitPrice(), orderWithoutId.getTotalPrice(),
                orderWithoutId.getCreatedAt(), orderWithoutId.getStatus());
        orders.put(id, stored);
        return stored;
    }

    public List<Order> findAll() {
        return List.copyOf(orders.values());
    }

    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(orders.get(id));
    }
}
