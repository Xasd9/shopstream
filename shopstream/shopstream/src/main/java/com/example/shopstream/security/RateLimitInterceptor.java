package com.example.shopstream.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    private static final int MAX_REQUESTS_PER_MINUTE = 10;

    public RateLimitInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Если пользователь не авторизован, пропускаем
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return true;
        }

        String username = auth.getName();
        String key = "rate_limit:cart:" + username;

        // Увеличиваем счетчик
        Long currentRequests = redisTemplate.opsForValue().increment(key);

        // Если это первый запрос в минуте, ставим время жизни ключа 1 минута
        if (currentRequests != null && currentRequests == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }

        // Если превысили лимит — возвращаем 429
        if (currentRequests != null && currentRequests > MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60"); // Скажем клиенту подождать 60 секунд
            response.getWriter().write("Too many requests. Please try again later.");
            return false;
        }

        return true;
    }
}