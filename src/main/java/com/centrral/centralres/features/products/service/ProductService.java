package com.centrral.centralres.features.products.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.centrral.centralres.core.aws.service.FileService;
import com.centrral.centralres.features.orders.repository.OrderDetailRepository;
import com.centrral.centralres.features.products.dto.product.request.ProductRequest;
import com.centrral.centralres.features.products.dto.product.response.ProductResponse;
import com.centrral.centralres.features.products.dto.productingredient.response.ProductIngredientResponse;
import com.centrral.centralres.features.products.exceptions.CategoryNotFoundException;
import com.centrral.centralres.features.products.exceptions.ProductNotFoundException;
import com.centrral.centralres.features.products.model.Category;
import com.centrral.centralres.features.products.model.ComboProductItem;
import com.centrral.centralres.features.products.model.Ingredient;
import com.centrral.centralres.features.products.model.Product;
import com.centrral.centralres.features.products.model.ProductExtra;
import com.centrral.centralres.features.products.model.ProductIngredient;
import com.centrral.centralres.features.products.repository.CategoryRepository;
import com.centrral.centralres.features.products.repository.IngredientRepository;
import com.centrral.centralres.features.products.repository.ProductRepository;
import com.centrral.centralres.features.reports.dto.response.ProductSalesReportResponse;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

        private final ProductRepository productRepository;
        private final CategoryRepository categoryRepository;
        private final IngredientRepository ingredientRepository;
        private final OrderDetailRepository orderDetailRepository;
        private final FileService fileService;

        public List<ProductResponse> getAllActive() {
                return productRepository.findAllActive().stream()
                                .map(this::mapToResponse)
                                .toList();
        }

        public List<ProductResponse> getAllIncludingInactive() {
                return productRepository.findAllIncludingInactive().stream()
                                .map(this::mapToResponse)
                                .toList();
        }

        public ProductResponse getByIdSmart(Long id) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();

                boolean isAuthenticated = auth != null && auth.isAuthenticated()
                                && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()));

                boolean isAdmin = isAuthenticated && auth.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_ADMIN")
                                                || a.getAuthority().equalsIgnoreCase("ADMIN"));

                boolean isClient = isAuthenticated && auth.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_CLIENT")
                                                || a.getAuthority().equalsIgnoreCase("CLIENT"));

                Product product = productRepository.findByIdIncludingInactive(id)
                                .orElseThrow(() -> new ProductNotFoundException(
                                                "Producto no encontrado con id: " + id));

                if (product.isActive()) {
                        return mapToResponse(product);
                }

                if (isAdmin)
                        return mapToResponse(product);

                if (isClient) {
                        boolean orderedBefore = orderDetailRepository.existsByProduct_IdAndOrder_Customer_User_Username(
                                        id,
                                        auth.getName());
                        if (orderedBefore)
                                return mapToResponse(product);
                }

                throw new ProductNotFoundException("Producto no disponible actualmente");
        }

        public List<ProductResponse> getFeaturedProducts(int limit) {
                Pageable pageable = PageRequest.of(0, limit);

                return productRepository.findTopRated(pageable).stream()
                                .map(this::mapToResponse)
                                .toList();
        }

        public List<ProductResponse> getByCategory(Long categoryId) {
                categoryRepository.findById(categoryId)
                                .orElseThrow(() -> new CategoryNotFoundException(
                                                "Categoría no encontrada con id: " + categoryId));

                return productRepository.findByCategoryId(categoryId).stream()
                                .filter(Product::isActive)
                                .map(this::mapToResponse)
                                .toList();
        }

        @Transactional
        public ProductResponse create(ProductRequest request) {
                Category category = categoryRepository.findById(request.getCategoryId())
                                .orElseThrow(() -> new CategoryNotFoundException(
                                                "Categoría no encontrada con id: " + request.getCategoryId()));

                Product product = Product.builder()
                                .name(request.getName())
                                .description(request.getDescription())
                                .price(request.getPrice())
                                .category(category)
                                .imageUrl(request.getImageUrl())
                                .preparationTimeMinutes(request.getPreparationTimeMinutes())
                                .active(request.getActive() != null ? request.getActive() : true)
                                .isCombo(request.isCombo())
                                .build();

                if (request.getIngredients() != null && !request.getIngredients().isEmpty()) {
                        Set<ProductIngredient> ingredients = request.getIngredients().stream()
                                        .map(ingReq -> {
                                                Ingredient ingredient = ingredientRepository
                                                                .findById(ingReq.getIngredientId())
                                                                .orElseThrow(() -> new EntityNotFoundException(
                                                                                "Ingrediente no encontrado con id: "
                                                                                                + ingReq.getIngredientId()));

                                                return ProductIngredient.builder()
                                                                .product(product)
                                                                .ingredient(ingredient)
                                                                .quantity(ingReq.getQuantity())
                                                                .build();
                                        })
                                        .collect(Collectors.toSet());

                        product.replaceIngredients(ingredients);
                }

                if (request.isCombo() && request.getComboItems() != null && !request.getComboItems().isEmpty()) {
                        log.info("Añadiendo productos al combo...");
                        Set<ComboProductItem> comboItems = request.getComboItems().stream()
                                        .map(comboReq -> {
                                                Product simpleProduct = productRepository
                                                                .findById(comboReq.getSimpleProductId())
                                                                .orElseThrow(() -> new ProductNotFoundException(
                                                                                "Producto (item) no encontrado con id: "
                                                                                                + comboReq.getSimpleProductId()));

                                                if (simpleProduct.isCombo()) {
                                                        throw new IllegalArgumentException(
                                                                        "No se puede añadir un combo dentro de otro combo.");
                                                }

                                                return ComboProductItem.builder()
                                                                .comboProduct(product)
                                                                .simpleProduct(simpleProduct)
                                                                .quantity(comboReq.getQuantity())
                                                                .build();
                                        })
                                        .collect(Collectors.toSet());

                        product.setComboItems(comboItems);
                }

                if (request.getExtras() != null && !request.getExtras().isEmpty()) {
                        Set<ProductExtra> extras = request.getExtras().stream()
                                        .map(extraReq -> ProductExtra.builder()
                                                        .name(extraReq.getName())
                                                        .price(extraReq.getPrice())
                                                        .product(product)
                                                        .active(true)
                                                        .build())
                                        .collect(Collectors.toSet());

                        product.getExtras().addAll(extras);
                }

                Product saved = productRepository.save(product);
                return mapToResponse(saved);
        }

        @Transactional
        public ProductResponse update(Long id, ProductRequest request) {
                Product product = productRepository.findByIdIncludingInactive(id)
                                .orElseThrow(() -> new ProductNotFoundException(
                                                "Producto no encontrado con id: " + id));

                String previousImageUrl = product.getImageUrl();
                if (request.getName() != null && !request.getName().isBlank()) {
                        product.setName(request.getName());
                }
                if (request.getPrice() != null && request.getPrice().compareTo(BigDecimal.ZERO) >= 0) {
                        product.setPrice(request.getPrice());
                }
                if (request.getDescription() != null && !request.getDescription().isBlank()) {
                        product.setDescription(request.getDescription());
                }
                if (request.getCategoryId() != null) {
                        Category category = categoryRepository.findById(request.getCategoryId())
                                        .orElseThrow(() -> new CategoryNotFoundException(
                                                        "Categoría no encontrada con id: " + request.getCategoryId()));
                        product.setCategory(category);
                }
                if (request.getImageUrl() == null || request.getImageUrl().isBlank()) {
                        if (previousImageUrl != null && !previousImageUrl.isBlank()) {
                                fileService.deleteFileByUrl(previousImageUrl);
                        }
                        product.setImageUrl(null);
                } else if (!request.getImageUrl().equals(previousImageUrl)) {
                        if (previousImageUrl != null && !previousImageUrl.isBlank()) {
                                fileService.deleteFileByUrl(previousImageUrl);
                        }
                        product.setImageUrl(request.getImageUrl());
                }
                if (request.getActive() != null) {
                        product.setActive(request.getActive());
                }
                if (request.getPreparationTimeMinutes() != null && request.getPreparationTimeMinutes() > 0) {
                        product.setPreparationTimeMinutes(request.getPreparationTimeMinutes());
                }

                product.setCombo(request.isCombo());

                if (request.isCombo()) {
                        log.info("Actualizando como Combo...");
                        product.replaceIngredients(null);

                        Set<ComboProductItem> comboItems = null;
                        if (request.getComboItems() != null) {
                                comboItems = request.getComboItems().stream()
                                                .map(comboReq -> {
                                                        Product simpleProduct = productRepository
                                                                        .findById(comboReq.getSimpleProductId())
                                                                        .orElseThrow(() -> new ProductNotFoundException(
                                                                                        "Producto (item) no encontrado con id: "
                                                                                                        + comboReq.getSimpleProductId()));
                                                        if (simpleProduct.isCombo()) {
                                                                throw new IllegalArgumentException(
                                                                                "No se puede añadir un combo dentro de otro combo.");
                                                        }
                                                        return ComboProductItem.builder()
                                                                        .comboProduct(product)
                                                                        .simpleProduct(simpleProduct)
                                                                        .quantity(comboReq.getQuantity())
                                                                        .build();
                                                })
                                                .collect(Collectors.toSet());
                        }
                        product.replaceComboItems(comboItems);

                } else {
                        log.info("Actualizando como Producto Simple...");
                        product.replaceComboItems(null);

                        Set<ProductIngredient> ingredients = null;
                        if (request.getIngredients() != null) {
                                ingredients = request.getIngredients().stream()
                                                .map(ingReq -> {
                                                        Ingredient ingredient = ingredientRepository
                                                                        .findById(ingReq.getIngredientId())
                                                                        .orElseThrow(() -> new EntityNotFoundException(
                                                                                        "Ingrediente no encontrado con id: "
                                                                                                        + ingReq.getIngredientId()));
                                                        return ProductIngredient.builder()
                                                                        .product(product)
                                                                        .ingredient(ingredient)
                                                                        .quantity(ingReq.getQuantity())
                                                                        .build();
                                                })
                                                .collect(Collectors.toSet());
                        }
                        if (request.getIngredients() != null) {
                                product.replaceIngredients(ingredients);
                        }
                }

                if (request.getExtras() != null) {
                        product.getExtras().clear();

                        Set<ProductExtra> newExtras = request.getExtras().stream()
                                        .map(extraReq -> ProductExtra.builder()
                                                        .name(extraReq.getName())
                                                        .price(extraReq.getPrice())
                                                        .product(product)
                                                        .active(true)
                                                        .build())
                                        .collect(Collectors.toSet());

                        product.getExtras().addAll(newExtras);
                }

                Product updated = productRepository.save(product);
                log.info("Producto guardado: {} - esCombo: {}", updated.getName(), updated.isCombo());
                return mapToResponse(updated);
        }

        @Transactional
        public ProductResponse toggleActive(Long id) {
                Product product = productRepository.findByIdIncludingInactive(id)
                                .orElseThrow(() -> new ProductNotFoundException(
                                                "Producto no encontrado con id: " + id));

                product.setActive(!product.isActive());
                Product updated = productRepository.save(product);

                return mapToResponse(updated);
        }

        @Transactional
        public void delete(Long id) {
                Product product = productRepository.findByIdIncludingInactive(id)
                                .orElseThrow(() -> new ProductNotFoundException(
                                                "Producto no encontrado con id: " + id));

                productRepository.delete(product);
        }

        public List<ProductSalesReportResponse> getTopSellingProducts() {
                return orderDetailRepository.findTopSellingProducts().stream()
                                .map((Object[] row) -> {
                                        Number idNum = (Number) row[0];
                                        Long productId = idNum != null ? idNum.longValue() : null;

                                        String productName = row[1] != null ? row[1].toString() : null;

                                        Number qtyNum = (Number) row[2];
                                        Long totalQuantitySold = qtyNum != null ? qtyNum.longValue() : 0L;

                                        BigDecimal totalRevenue;
                                        if (row[3] == null) {
                                                totalRevenue = BigDecimal.ZERO;
                                        } else if (row[3] instanceof BigDecimal) {
                                                totalRevenue = (BigDecimal) row[3];
                                        } else {
                                                totalRevenue = new BigDecimal(row[3].toString());
                                        }

                                        return ProductSalesReportResponse.builder()
                                                        .productId(productId)
                                                        .productName(productName)
                                                        .totalQuantitySold(totalQuantitySold)
                                                        .totalRevenue(totalRevenue)
                                                        .build();
                                })
                                .collect(Collectors.toList());
        }

        private ProductResponse mapToResponse(Product product) {

                List<ProductIngredientResponse> ingredientResponses = product.getIngredients() == null
                                ? List.of()
                                : product.getIngredients().stream()
                                                .map(pi -> ProductIngredientResponse.builder()
                                                                .ingredientId(pi.getIngredient().getId())
                                                                .ingredientName(pi.getIngredient().getName())
                                                                .unitName(pi.getIngredient().getUnit().getName())
                                                                .unitSymbol(pi.getIngredient().getUnit().getSymbol())
                                                                .quantity(pi.getQuantity())
                                                                .build())
                                                .toList();

                List<ProductResponse.ComboItemResponseStub> comboItemResponses = product.getComboItems() == null
                                ? List.of()
                                : product.getComboItems().stream()
                                                .map(ci -> ProductResponse.ComboItemResponseStub.builder()
                                                                .simpleProductId(ci.getSimpleProduct().getId())
                                                                .simpleProductName(ci.getSimpleProduct().getName())
                                                                .quantity(ci.getQuantity())
                                                                .build())
                                                .toList();

                List<ProductResponse.ProductExtraResponse> extraResponses = product.getExtras() == null
                                ? List.of()
                                : product.getExtras().stream()
                                                .filter(ProductExtra::isActive) 
                                                .map(pe -> ProductResponse.ProductExtraResponse.builder()
                                                                .id(pe.getId())
                                                                .name(pe.getName())
                                                                .price(pe.getPrice())
                                                                .build())
                                                .toList();

                return ProductResponse.builder()
                                .id(product.getId())
                                .name(product.getName())
                                .description(product.getDescription())
                                .price(product.getPrice())
                                .categoryId(product.getCategory().getId())
                                .categoryName(product.getCategory().getName())
                                .imageUrl(product.getImageUrl())
                                .preparationTimeMinutes(product.getPreparationTimeMinutes())
                                .ingredients(ingredientResponses)
                                .active(product.isActive())
                                .isCombo(product.isCombo())
                                .comboItems(comboItemResponses)
                                .extras(extraResponses) // Set extras
                                .build();
        }
}
