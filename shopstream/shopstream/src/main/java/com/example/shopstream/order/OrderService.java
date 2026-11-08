package com.example.shopstream.order;

import com.example.shopstream.cart.Cart;
import com.example.shopstream.cart.CartItem;
import com.example.shopstream.cart.CartRepository;
import com.example.shopstream.outbox.OutboxEvent;
import com.example.shopstream.outbox.OutboxRepository;
import com.example.shopstream.user.User;
import com.example.shopstream.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final OutboxRepository outboxRepository;

    public OrderService(OrderRepository orderRepository,
                        CartRepository cartRepository,
                        UserRepository userRepository,
                        OutboxRepository outboxRepository) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public OrderResponse createOrder(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart not found"));

        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.CREATED);
        order.setCreatedAt(LocalDateTime.now());

        BigDecimal totalPrice = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cart.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(cartItem.getProduct().getPrice());
            orderItems.add(orderItem);

            totalPrice = totalPrice.add(cartItem.getProduct().getPrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        order.setItems(orderItems);
        order.setTotalPrice(totalPrice);
        Order savedOrder = orderRepository.save(order);

        // Создаем событие для Outbox (вручную собираем JSON)
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("Order");
        outboxEvent.setAggregateId(savedOrder.getId());
        outboxEvent.setEventType("order.created");
        outboxEvent.setEventId(UUID.randomUUID());
        outboxEvent.setCreatedAt(LocalDateTime.now());

        // Простой JSON без LocalDateTime и вложенных сущностей
        String payload = String.format(
                "{\"orderId\": %d, \"items\": %s}",
                savedOrder.getId(),
                savedOrder.getItems().stream()
                        .map(item -> String.format("{\"productId\": %d, \"quantity\": %d}",
                                item.getProduct().getId(), item.getQuantity()))
                        .collect(Collectors.joining(",", "[", "]"))
        );
        outboxEvent.setPayload(payload);

        outboxRepository.save(outboxEvent);

        // Очищаем корзину
        cart.getItems().clear();
        cartRepository.save(cart);

        return new OrderResponse(savedOrder.getId(), savedOrder.getStatus(), savedOrder.getTotalPrice());
    }
}