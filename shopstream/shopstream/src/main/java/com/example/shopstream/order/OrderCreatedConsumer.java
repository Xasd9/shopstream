package com.example.shopstream.order;

import com.example.shopstream.product.Product;
import com.example.shopstream.product.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderCreatedConsumer {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    public OrderCreatedConsumer(OrderRepository orderRepository,
                                ProductRepository productRepository,
                                ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.created", groupId = "shopstream-group")
    @Transactional
    public void consumeOrderCreated(String payload) {
        try {
            // Парсим JSON в дерево
            JsonNode root = objectMapper.readTree(payload);
            Long orderId = root.get("orderId").asLong();

            System.out.println("🔥 Получено событие из Kafka! Заказ ID: " + orderId);

            // Находим заказ в базе
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

            // Вычитаем товары со склада
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                if (product.getStock() < item.getQuantity()) {
                    throw new RuntimeException("Not enough stock for product: " + product.getName());
                }
                product.setStock(product.getStock() - item.getQuantity());
                productRepository.save(product);
            }

            // Меняем статус
            order.setStatus(OrderStatus.RESERVED);
            orderRepository.save(order);

            System.out.println("✅ Заказ " + orderId + " успешно зарезервирован!");

        } catch (Exception e) {
            System.err.println("❌ Ошибка при обработке заказа: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}