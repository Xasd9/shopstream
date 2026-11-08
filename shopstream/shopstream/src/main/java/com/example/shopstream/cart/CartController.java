package com.example.shopstream.cart;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getMyCart(Authentication authentication) {
        return cartService.getMyCart(authentication.getName());
    }

    @PostMapping("/items")
    public CartResponse addToCart(Authentication authentication, @Valid @RequestBody AddToCartRequest request) {
        return cartService.addToCart(authentication.getName(), request);
    }

    @PatchMapping("/items/{itemId}")
    public CartResponse updateCartItem(Authentication authentication,
                                       @PathVariable Long itemId,
                                       @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateCartItem(authentication.getName(), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse removeCartItem(Authentication authentication, @PathVariable Long itemId) {
        return cartService.removeCartItem(authentication.getName(), itemId);
    }
}