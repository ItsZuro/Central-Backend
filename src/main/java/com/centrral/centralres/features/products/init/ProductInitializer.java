package com.centrral.centralres.features.products.init;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.centrral.centralres.core.security.repository.UserRepository;
import com.centrral.centralres.features.customers.model.Customer;
import com.centrral.centralres.features.customers.model.Review;
import com.centrral.centralres.features.customers.repository.CustomerRepository;
import com.centrral.centralres.features.customers.repository.ReviewRepository;
import com.centrral.centralres.features.products.enums.DiscountType;
import com.centrral.centralres.features.products.enums.MovementSource;
import com.centrral.centralres.features.products.enums.MovementType;
import com.centrral.centralres.features.products.model.Category;
import com.centrral.centralres.features.products.model.ComboProductItem;
import com.centrral.centralres.features.products.model.Ingredient;
import com.centrral.centralres.features.products.model.Inventory;
import com.centrral.centralres.features.products.model.InventoryMovement;
import com.centrral.centralres.features.products.model.Product;
import com.centrral.centralres.features.products.model.ProductExtra;
import com.centrral.centralres.features.products.model.ProductIngredient;
import com.centrral.centralres.features.products.model.Promotion;
import com.centrral.centralres.features.products.model.Unit;
import com.centrral.centralres.features.products.repository.CategoryRepository;
import com.centrral.centralres.features.products.repository.ComboProductItemRepository;
import com.centrral.centralres.features.products.repository.IngredientRepository;
import com.centrral.centralres.features.products.repository.InventoryMovementRepository;
import com.centrral.centralres.features.products.repository.InventoryRepository;
import com.centrral.centralres.features.products.repository.ProductExtraRepository;
import com.centrral.centralres.features.products.repository.ProductIngredientRepository;
import com.centrral.centralres.features.products.repository.ProductRepository;
import com.centrral.centralres.features.products.repository.PromotionRepository;
import com.centrral.centralres.features.products.repository.UnitRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@Order(5)
public class ProductInitializer implements CommandLineRunner {

        private final CategoryRepository categoryRepository;
        private final ProductRepository productRepository;
        private final UnitRepository unitRepository;
        private final IngredientRepository ingredientRepository;
        private final ProductIngredientRepository productIngredientRepository;
        private final ProductExtraRepository productExtraRepository;
        private final InventoryRepository inventoryRepository;
        private final InventoryMovementRepository inventoryMovementRepository;
        private final PromotionRepository promotionRepository;
        private final ComboProductItemRepository comboProductItemRepository;
        private final ReviewRepository reviewRepository;
        private final CustomerRepository customerRepository;
        private final UserRepository userRepository;

        @Override
        public void run(String... args) throws Exception {
                initUnits();
                initCategoriesAndProducts();
                initIngredients();
                initProductIngredients();
                initExtras();
                initInventories();
                initInventoryMovements();
                initPromotions();
                initCombos();
                initReviews();
        }

        private void initUnits() {
                if (unitRepository.count() > 0) {
                        log.info(">>> Unidades ya inicializadas");
                        return;
                }

                log.info(">>> Inicializando unidades...");

                List<Unit> units = List.of(
                                Unit.builder().name("Gramo").symbol("g").build(),
                                Unit.builder().name("Kilogramo").symbol("kg").build(),
                                Unit.builder().name("Litro").symbol("l").build(),
                                Unit.builder().name("Mililitro").symbol("ml").build(),
                                Unit.builder().name("Unidad").symbol("u").build(),
                                Unit.builder().name("Cucharada").symbol("tbsp").build(),
                                Unit.builder().name("Cucharadita").symbol("tsp").build());

                unitRepository.saveAll(units);
                log.info(">>> Unidades inicializadas correctamente");
        }

        private void initCategoriesAndProducts() {
                if (categoryRepository.count() > 0) {
                        log.info(">>> Categorías y productos ya inicializados");
                        return;
                }

                log.info(">>> Inicializando categorías y productos...");

                // Crear categorías
                Category beverages = Category.builder().name("Bebidas").build();
                Category appetizers = Category.builder().name("Entradas").build();
                Category mainDishes = Category.builder().name("Platos principales").build();
                Category desserts = Category.builder().name("Postres").build();

                List<Category> categories = List.of(beverages, appetizers, mainDishes, desserts);
                categoryRepository.saveAll(categories);
                log.info(">>> Categorías inicializadas: Bebidas, Entradas, Platos principales, Postres");

                // Crear productos
                List<Product> products = List.of(
                                Product.builder()
                                                .name("Coca-Cola 500ml")
                                                .description("Refresco de cola clásico en botella de 500ml")
                                                .price(new BigDecimal("3.50"))
                                                .imageUrl("https://lacanga.com/cdn/shop/files/SLFk8fwFmHSQ7qcTv-sintitulo2556.png?v=1685580356")
                                                .category(beverages)
                                                .preparationTimeMinutes(2)
                                                .active(true)
                                                .isCombo(false)
                                                .build(),
                                Product.builder()
                                                .name("Jugo de Naranja Natural")
                                                .description("Jugo de naranja exprimido al momento, sin azúcar añadida")
                                                .price(new BigDecimal("5.00"))
                                                .imageUrl("https://image.tuasaude.com/media/article/go/jh/suco-de-laranja_67324.jpg")
                                                .category(beverages)
                                                .preparationTimeMinutes(3)
                                                .active(true)
                                                .isCombo(false)
                                                .build(),
                                Product.builder()
                                                .name("Nachos con Queso")
                                                .description("Porción de nachos crujientes servidos con salsa de queso cheddar")
                                                .price(new BigDecimal("12.00"))
                                                .imageUrl("https://www.divinacocina.es/wp-content/uploads/nachos-con-salsa-queso.jpg")
                                                .category(appetizers)
                                                .preparationTimeMinutes(8)
                                                .active(true)
                                                .isCombo(false)
                                                .build(),
                                Product.builder()
                                                .name("Alitas BBQ")
                                                .description("Alitas de pollo fritas bañadas en salsa barbacoa casera")

                                                .price(new BigDecimal("15.00"))
                                                .imageUrl(
                                                                "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTR0GgvGRXZ9rGXssme3fuDO5SlUbf7tB8sOw&s")
                                                .category(appetizers)
                                                .preparationTimeMinutes(10)
                                                .active(true)
                                                .isCombo(false)
                                                .build(),
                                Product.builder()
                                                .name("Hamburguesa Clásica")
                                                .description("Jugosa hamburguesa de res con queso cheddar, lechuga y tomate")
                                                .price(new BigDecimal("18.00"))
                                                .imageUrl("https://tofuu.getjusto.com/orioneat-local/resized2/4Zg3b29e8fYXFT9ww-2400-x.webp")
                                                .category(mainDishes)
                                                .preparationTimeMinutes(15)
                                                .active(true)
                                                .isCombo(false)
                                                .build(),
                                Product.builder()
                                                .name("Lomo Saltado")
                                                .description("Trozos de lomo de res salteados con cebolla, tomate y papas fritas")
                                                .price(new BigDecimal("22.00"))
                                                .imageUrl("https://origin.cronosmedia.glr.pe/large/2024/05/15/lg_664520c66ade8d4879400887.jpg")
                                                .category(mainDishes)
                                                .preparationTimeMinutes(20)
                                                .active(true)
                                                .isCombo(false)
                                                .build(),
                                Product.builder()
                                                .name("Cheesecake")
                                                .description("Delicioso cheesecake cremoso con base de galleta y cobertura de fresa")
                                                .price(new BigDecimal("10.00"))
                                                .imageUrl(
                                                                "https://www.recetasnestle.com.ec/sites/default/files/styles/recipe_detail_desktop_new/public/srh_recipes/7f9ebeaceea909a80306da27f0495c59.jpg?itok=_Xp6MoSe")
                                                .category(desserts)
                                                .preparationTimeMinutes(6)
                                                .active(true)
                                                .isCombo(false)
                                                .build(),
                                Product.builder()
                                                .name("Brownie con Helado")
                                                .description("Brownie de chocolate caliente servido con helado de vainilla")
                                                .price(new BigDecimal("12.00"))
                                                .imageUrl("https://www.johaprato.com/files/brownie_y_helado.jpg")
                                                .category(desserts)
                                                .preparationTimeMinutes(7)
                                                .active(true)
                                                .isCombo(false)
                                                .build());

                productRepository.saveAll(products);
                log.info(">>> Productos inicializados correctamente");
        }

        private void initIngredients() {
                if (ingredientRepository.count() > 0) {
                        log.info(">>> Ingredientes ya inicializados");
                        return;
                }

                log.info(">>> Inicializando ingredientes...");

                Unit g = unitRepository.findBySymbol("g").orElseThrow();
                Unit u = unitRepository.findBySymbol("u").orElseThrow();
                Unit ml = unitRepository.findBySymbol("ml").orElseThrow();

                List<Ingredient> ingredients = List.of(
                                Ingredient.builder().name("Pollo").unit(g).build(),
                                Ingredient.builder().name("Papa").unit(g).build(),
                                Ingredient.builder().name("Arroz").unit(g).build(),
                                Ingredient.builder().name("Aceite").unit(ml).build(),
                                Ingredient.builder().name("Pan").unit(u).build(),
                                Ingredient.builder().name("Queso").unit(g).build(),
                                Ingredient.builder().name("Carne de res").unit(g).build());

                ingredientRepository.saveAll(ingredients);
                log.info(">>> Ingredientes inicializados correctamente");
        }

        private void initProductIngredients() {
                if (productIngredientRepository.count() > 0) {
                        log.info(">>> ProductIngredients ya inicializados");
                        return;
                }

                log.info(">>> Inicializando relaciones producto-ingredientes...");

                Product hamburguesa = productRepository.findByName("Hamburguesa Clásica").orElseThrow();
                Product lomoSaltado = productRepository.findByName("Lomo Saltado").orElseThrow();

                Ingredient carne = ingredientRepository.findByName("Carne de res").orElseThrow();
                Ingredient papa = ingredientRepository.findByName("Papa").orElseThrow();
                Ingredient arroz = ingredientRepository.findByName("Arroz").orElseThrow();
                Ingredient pan = ingredientRepository.findByName("Pan").orElseThrow();
                Ingredient queso = ingredientRepository.findByName("Queso").orElseThrow();

                List<ProductIngredient> relations = List.of(
                                ProductIngredient.builder().product(hamburguesa).ingredient(carne).quantity(200.0)
                                                .build(),
                                ProductIngredient.builder().product(hamburguesa).ingredient(pan).quantity(1.0).build(),
                                ProductIngredient.builder().product(hamburguesa).ingredient(queso).quantity(30.0)
                                                .build(),

                                ProductIngredient.builder().product(lomoSaltado).ingredient(carne).quantity(150.0)
                                                .build(),
                                ProductIngredient.builder().product(lomoSaltado).ingredient(papa).quantity(200.0)
                                                .build(),
                                ProductIngredient.builder().product(lomoSaltado).ingredient(arroz).quantity(150.0)
                                                .build());

                productIngredientRepository.saveAll(relations);
                log.info(">>> Relaciones producto-ingredientes inicializadas");
        }

        private void initExtras() {
                if (productExtraRepository.count() > 0) {
                        log.info(">>> Extras de productos ya inicializados");
                        return;
                }

                log.info(">>> Inicializando extras de productos...");

                Product hamburguesa = productRepository.findByName("Hamburguesa Clásica").orElse(null);

                if (hamburguesa != null) {
                        List<ProductExtra> extras = List.of(
                                        ProductExtra.builder()
                                                        .name("Tocino Extra")
                                                        .price(new BigDecimal("3.00"))
                                                        .product(hamburguesa)
                                                        .active(true)
                                                        .build(),
                                        ProductExtra.builder()
                                                        .name("Queso Cheddar Extra")
                                                        .price(new BigDecimal("2.50"))
                                                        .product(hamburguesa)
                                                        .active(true)
                                                        .build(),
                                        ProductExtra.builder()
                                                        .name("Huevo Frito")
                                                        .price(new BigDecimal("2.00"))
                                                        .product(hamburguesa)
                                                        .active(true)
                                                        .build());

                        productExtraRepository.saveAll(extras);
                        log.info(">>> Extras para Hamburguesa Clásica creados correctamente");
                } else {
                        log.warn(">>> No se encontró la Hamburguesa Clásica para asignarle extras");
                }
        }

        private void initInventories() {
                if (inventoryRepository.count() > 0) {
                        log.info(">>> Inventarios ya inicializados");
                        return;
                }

                log.info(">>> Inicializando inventarios...");

                List<Ingredient> ingredients = ingredientRepository.findAll();

                List<Inventory> inventories = ingredients.stream()
                                .map(ing -> {
                                        BigDecimal minStock = getDefaultMinStock(ing.getName());
                                        BigDecimal initialStock = minStock.multiply(BigDecimal.valueOf(2));

                                        return Inventory.builder()
                                                        .ingredient(ing)
                                                        .currentStock(initialStock)
                                                        .minimumStock(minStock)
                                                        .build();
                                })
                                .toList();

                inventoryRepository.saveAll(inventories);
                log.info(">>> Inventarios inicializados correctamente");
        }

        private void initInventoryMovements() {
                if (inventoryMovementRepository.count() > 0) {
                        log.info(">>> Movimientos de inventario ya inicializados");
                        return;
                }

                log.info(">>> Inicializando movimientos de inventario...");

                Ingredient pollo = ingredientRepository.findByName("Pollo").orElseThrow();
                Ingredient papa = ingredientRepository.findByName("Papa").orElseThrow();

                List<InventoryMovement> movements = List.of(
                                InventoryMovement.builder()
                                                .ingredient(pollo)
                                                .type(MovementType.ENTRY)
                                                .quantity(BigDecimal.valueOf(2000))
                                                .reason("Compra inicial de pollo")
                                                .source(MovementSource.PURCHASE)
                                                .referenceId(null)
                                                .build(),
                                InventoryMovement.builder()
                                                .ingredient(papa)
                                                .type(MovementType.EXIT)
                                                .quantity(BigDecimal.valueOf(500))
                                                .reason("Consumo en pruebas de cocina")
                                                .source(MovementSource.MANUAL)
                                                .referenceId(null)
                                                .build());

                inventoryMovementRepository.saveAll(movements);
                log.info(">>> Movimientos de inventario inicializados");
        }

        private BigDecimal getDefaultMinStock(String name) {
                return switch (name) {
                        case "Pollo", "Aceite", "Carne de res" -> BigDecimal.valueOf(1000.0);
                        case "Papa" -> BigDecimal.valueOf(2000.0);
                        case "Arroz" -> BigDecimal.valueOf(3000.0);
                        case "Pan" -> BigDecimal.valueOf(20.0);
                        case "Queso" -> BigDecimal.valueOf(500.0);
                        default -> BigDecimal.valueOf(100.0);
                };
        }

        private void initPromotions() {
                if (promotionRepository.count() > 0) {
                        log.info(">>> Promociones ya inicializadas");
                        return;
                }

                log.info(">>> Inicializando promociones...");

                Product hamburguesa = productRepository.findByName("Hamburguesa Clásica").orElse(null);

                Category bebidas = categoryRepository.findByName("Bebidas").orElse(null);

                Product cheesecake = productRepository.findByName("Cheesecake").orElse(null);
                Product brownie = productRepository.findByName("Brownie con Helado").orElse(null);

                List<Promotion> promotions = List.of(

                                Promotion.builder()
                                                .name("Promo Burger 20%")
                                                .description("20% de descuento en nuestra Hamburguesa Clásica. ¡Solo por hoy!")
                                                .startDate(LocalDateTime.now().minusDays(1))
                                                .endDate(LocalDateTime.now().plusDays(1))
                                                .active(true)
                                                .discountType(DiscountType.PERCENTAGE)
                                                .discountValue(new BigDecimal("20.00"))
                                                .applicableProducts(
                                                                hamburguesa != null ? Set.of(hamburguesa) : Set.of())

                                                .applicableCategories(Set.of())
                                                .build(),

                                Promotion.builder()
                                                .name("Bebidas Refresh")
                                                .description("S/ 1.00 de descuento en todas las bebidas.")
                                                .startDate(LocalDateTime.now().minusDays(7))
                                                .endDate(LocalDateTime.now().plusMonths(1))
                                                .active(true)
                                                .discountType(DiscountType.FIXED_AMOUNT)
                                                .discountValue(new BigDecimal("1.00"))
                                                .applicableProducts(Set.of())
                                                .applicableCategories(bebidas != null ? Set.of(bebidas) : Set.of())

                                                .build(),

                                Promotion.builder()
                                                .name("Dulce Final 10%")
                                                .description("10% de descuento en todos los postres.")
                                                .startDate(LocalDateTime.now().minusDays(2))
                                                .endDate(LocalDateTime.now().plusDays(5))
                                                .active(true)
                                                .discountType(DiscountType.PERCENTAGE)
                                                .discountValue(new BigDecimal("10.00"))
                                                .applicableProducts(cheesecake != null && brownie != null
                                                                ? Set.of(cheesecake, brownie)
                                                                : Set.of())
                                                .applicableCategories(Set.of())
                                                .build());

                promotionRepository.saveAll(promotions);
                log.info(">>> Promociones inicializadas correctamente");
        }

        private void initCombos() {
                if (productRepository.findByName("Combo Clásico").isPresent()) {
                        log.info(">>> Combos ya inicializados");
                        return;
                }
                log.info(">>> Inicializando combos...");

                Product hamburguesa = productRepository.findByName("Hamburguesa Clásica")
                                .orElseThrow(() -> new RuntimeException(
                                                "Producto 'Hamburguesa Clásica' no encontrado"));
                Product cocaCola = productRepository.findByName("Coca-Cola 500ml")
                                .orElseThrow(() -> new RuntimeException("Producto 'Coca-Cola 500ml' no encontrado"));

                Category mainDishes = categoryRepository.findByName("Platos principales")
                                .orElseThrow(() -> new RuntimeException(
                                                "Categoría 'Platos principales' no encontrada"));

                Product comboClasico = Product.builder()
                                .name("Combo Clásico")
                                .description("Nuestra Hamburguesa Clásica con una Coca-Cola 500ml a un precio especial.")
                                .price(new BigDecimal("20.00"))
                                .imageUrl("https://tofuu.getjusto.com/orioneat-local/resized2/4Zg3b29e8fYXFT9ww-2400-x.webp")
                                .category(mainDishes)
                                .preparationTimeMinutes(15)
                                .active(true)
                                .isCombo(true)
                                .build();

                Product savedCombo = productRepository.save(comboClasico);

                ComboProductItem itemHamburguesa = ComboProductItem.builder()
                                .comboProduct(savedCombo)
                                .simpleProduct(hamburguesa)
                                .quantity(1)
                                .build();

                ComboProductItem itemCola = ComboProductItem.builder()
                                .comboProduct(savedCombo)
                                .simpleProduct(cocaCola)
                                .quantity(1)
                                .build();

                comboProductItemRepository.saveAll(List.of(itemHamburguesa, itemCola));

                log.info(">>> Combo 'Combo Clásico' creado exitosamente");
        }

        private Customer getCustomerByUsername(String username) {
                return userRepository.findByUsername(username)
                                .flatMap(user -> customerRepository.findByUserId(user.getId()))
                                .orElse(null);
        }

        private void initReviews() {
                if (reviewRepository.count() > 0) {
                        log.info(">>> Reviews ya inicializadas");
                        return;
                }
                log.info(">>> Inicializando reseñas...");

                Customer cliente1 = getCustomerByUsername("cliente1");
                Customer cliente2 = getCustomerByUsername("cliente2");
                Customer publico = getCustomerByUsername("publico_general");

                Customer fallback = customerRepository.findAll().stream().findFirst().orElse(null);
                if (cliente1 == null)
                        cliente1 = fallback;
                if (cliente2 == null)
                        cliente2 = (fallback != null) ? fallback : cliente1;

                if (cliente1 == null) {
                        log.warn(">>> No se encontraron clientes para crear reseñas. Saltando.");
                        return;
                }

                Optional<Product> lomoOpt = productRepository.findByName("Lomo Saltado");
                if (lomoOpt.isPresent()) {
                        Product lomo = lomoOpt.get();
                        reviewRepository.saveAll(List.of(
                                        Review.builder().customer(cliente1).product(lomo).rating(5)
                                                        .comment("¡Increíble sabor ahumado!")
                                                        .date(LocalDateTime.now().minusDays(2)).build(),
                                        Review.builder().customer(cliente2).product(lomo).rating(4)
                                                        .comment("Muy bueno, pero mucha cebolla para mi gusto.")
                                                        .date(LocalDateTime.now().minusDays(5)).build(),
                                        Review.builder().customer(publico != null ? publico : cliente1).product(lomo)
                                                        .rating(5).comment("Excelente presentación.")
                                                        .date(LocalDateTime.now().minusDays(10)).build()));
                }

                Optional<Product> cheeseOpt = productRepository.findByName("Cheesecake");
                if (cheeseOpt.isPresent()) {
                        Product cheese = cheeseOpt.get();
                        reviewRepository.saveAll(List.of(
                                        Review.builder().customer(cliente2).product(cheese).rating(5)
                                                        .comment("El mejor cheesecake que he probado.")
                                                        .date(LocalDateTime.now().minusDays(1)).build(),
                                        Review.builder().customer(cliente1).product(cheese).rating(4)
                                                        .comment("Rico, buena textura.")
                                                        .date(LocalDateTime.now().minusDays(3)).build()));
                }

                Optional<Product> cocaOpt = productRepository.findByName("Coca-Cola 500ml");
                if (cocaOpt.isPresent()) {
                        Product coca = cocaOpt.get();
                        reviewRepository.saveAll(List.of(
                                        Review.builder().customer(cliente1).product(coca).rating(3)
                                                        .comment("Llegó fría, todo bien.")
                                                        .date(LocalDateTime.now().minusDays(7)).build()));
                }

                log.info(">>> Reseñas inicializadas correctamente con clientes reales.");
        }

}
