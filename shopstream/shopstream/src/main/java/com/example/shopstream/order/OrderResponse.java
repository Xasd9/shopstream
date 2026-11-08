package com.example.shopstream.order;

import java.math.BigDecimal;

public record OrderResponse(Long id, OrderStatus status, BigDecimal totalPrice) {}