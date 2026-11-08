package com.example.shopstream.product;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // ВНИМАНИЕ: Убрали @Cacheable, так как Spring не может сериализовать Page в Redis
    public Page<ProductResponse> getAll(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductResponse::from);
    }

    // Кэшируем отдельный товар по ID (это простой объект)
    @Cacheable(value = "product", key = "#id")
    public ProductResponse getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        return ProductResponse.from(product);
    }

    // При создании очищаем кэш (на всякий случай, если мы кэшировали списки)
    @Transactional
    @CacheEvict(value = {"product", "products_list"}, allEntries = true)
    public ProductResponse create(ProductRequest req) {
        Product product = new Product();
        product.setName(req.name());
        product.setDescription(req.description());
        product.setPrice(req.price());
        product.setStock(req.stock());
        product.setCategory(req.category());
        product.setCreatedAt(LocalDateTime.now());
        return ProductResponse.from(productRepository.save(product));
    }

    // При обновлении очищаем и кэш списка, и кэш конкретного товара.
    @Transactional
    @CacheEvict(value = {"product", "products_list"}, allEntries = true)
    public ProductResponse update(Long id, ProductRequest req) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        product.setName(req.name());
        product.setDescription(req.description());
        product.setPrice(req.price());
        product.setStock(req.stock());
        product.setCategory(req.category());

        return ProductResponse.from(productRepository.save(product));
    }

    // При удалении тоже очищаем оба кэша.
    @Transactional
    @CacheEvict(value = {"product", "products_list"}, allEntries = true)
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        productRepository.deleteById(id);
    }
}