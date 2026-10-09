package com.spring.eCommerce.service.order;

import com.spring.eCommerce.dto.cart.CartItemRequestDto;
import com.spring.eCommerce.dto.order.OrderResponseDto;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.entity.Product;
import com.spring.eCommerce.entity.enums.OrderStatus;
import com.spring.eCommerce.repository.OrderRepo;
import com.spring.eCommerce.repository.ProductRepo;
import com.spring.eCommerce.repository.UserRepo;
import com.spring.eCommerce.security.AppUserDetail;
import com.spring.eCommerce.service.authentication.AuthService;
import com.spring.eCommerce.service.cart.CartService;
import com.spring.eCommerce.web.AdminOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stock concurrency against the real database (the project's MySQL test setup, same engine as production):
 * every request runs in its own thread and its own committed transaction through the Spring proxies.
 * Not @Transactional on purpose; created users (with their carts and orders) and products are removed afterwards.
 */
@SpringBootTest
class OrderStockConcurrencyTest {

    private static final long TIMEOUT_SECONDS = 60;
    private final List<String> usernames = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();
    @Autowired
    private OrderService orderService;
    @Autowired
    private AdminOrderService adminOrderService;
    @Autowired
    private CartService cartService;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private ProductRepo productRepo;
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private static long successes(List<Result> results) {
        return results.stream().filter(Result::succeeded).count();
    }

    // ---------- Order creation races ----------

    /**
     * Every failure must be the application's insufficient-stock error, never a lock/deadlock/SQL error.
     */
    private static void assertInsufficientStock(List<Result> results) {
        results.stream().filter(r -> !r.succeeded()).forEach(r -> {
            assertInstanceOf(IllegalStateException.class, r.error(), String.valueOf(r.error()));
            assertTrue(r.error().getMessage().startsWith("Not enough quantity available"), r.error().getMessage());
        });
    }

    private static Map<Product, Integer> orderedMap(Product first, int firstQty, Product second, int secondQty) {
        Map<Product, Integer> map = new LinkedHashMap<>();
        map.put(first, firstQty);
        map.put(second, secondQty);
        return map;
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> usernames.forEach(name ->
                userRepo.findByUsername(name).ifPresent(userRepo::delete)));
        tx.executeWithoutResult(status -> productRepo.deleteAllById(productIds));
    }

    @Test
    void lastUnitCanBeOrderedOnlyOnceByTwoConcurrentCustomers() throws Exception {
        Product product = product(1);
        String a = customer(Map.of(product, 1));
        String b = customer(Map.of(product, 1));

        List<Result> results = race(List.of(placeOrder(a), placeOrder(b)));

        assertEquals(1, successes(results));
        assertInsufficientStock(results);
        assertEquals(0, stock(product));
        assertEquals(1, ordersFor(product));
    }

    @Test
    void lastUnitCanBeOrderedOnlyOnceByManyConcurrentCustomers() throws Exception {
        Product product = product(1);
        List<Callable<OrderResponseDto>> requests = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            requests.add(placeOrder(customer(Map.of(product, 1))));
        }

        List<Result> results = race(requests);

        assertEquals(1, successes(results));
        assertInsufficientStock(results);
        assertEquals(0, stock(product));
        assertEquals(1, ordersFor(product));
    }

    // ---------- Rollback and validation ----------

    @Test
    void twoConcurrentRequestsForThreeOfFiveOnlyOneSucceeds() throws Exception {
        Product product = product(5);
        String a = customer(Map.of(product, 3));
        String b = customer(Map.of(product, 3));

        List<Result> results = race(List.of(placeOrder(a), placeOrder(b)));

        assertEquals(1, successes(results));
        assertInsufficientStock(results);
        assertEquals(2, stock(product));
    }

    @Test
    void concurrentReservationsNeverExceedAvailableStock() throws Exception {
        Product product = product(5);
        int[] quantities = {1, 2, 3, 4};
        List<Callable<OrderResponseDto>> requests = new ArrayList<>();
        for (int quantity : quantities) {
            requests.add(placeOrder(customer(Map.of(product, quantity))));
        }

        List<Result> results = race(requests);

        int reserved = 0;
        for (int i = 0; i < quantities.length; i++) {
            if (results.get(i).succeeded()) {
                reserved += quantities[i];
            }
        }
        assertInsufficientStock(results);
        assertTrue(successes(results) >= 1);
        assertTrue(reserved <= 5, "reserved " + reserved + " of 5");
        int remaining = stock(product);
        assertEquals(5 - reserved, remaining);
        assertEquals(reserved, unitsOrdered(product));
        // Stock only goes down, so a rejected request must have asked for more than what is left now.
        for (int i = 0; i < quantities.length; i++) {
            if (!results.get(i).succeeded()) {
                assertTrue(quantities[i] > remaining, "request for " + quantities[i] + " rejected with " + remaining + " left");
            }
        }
    }

    @Test
    void overlappingProductsAddedInOppositeOrderDoNotOversellOrDeadlock() throws Exception {
        Product first = product(1);
        Product second = product(1);
        String a = customer(orderedMap(first, 1, second, 1));
        String b = customer(orderedMap(second, 1, first, 1));

        List<Result> results = race(List.of(placeOrder(a), placeOrder(b)));

        assertEquals(1, successes(results));
        assertInsufficientStock(results);
        assertEquals(0, stock(first));
        assertEquals(0, stock(second));
    }

    // ---------- Stock restoration ----------

    @Test
    void insufficientSecondProductRollsBackEarlierReservation() {
        Product plenty = product(5);
        Product scarce = product(1);
        String customer = customer(orderedMap(plenty, 2, scarce, 1));
        setStock(scarce, 0); // sold elsewhere after it was put in this cart

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> as(customer, () -> orderService.createOrder("Cairo")));

        assertTrue(error.getMessage().startsWith("Not enough quantity available"));
        assertEquals(5, stock(plenty));
        assertEquals(0, stock(scarce));
        assertEquals(0, ordersFor(plenty));
    }

    @Test
    void orderPersistenceFailureAfterReservationRollsStockBack() {
        Product product = product(3);
        String customer = customer(Map.of(product, 2));
        String tooLongAddress = "x".repeat(1000); // exceeds the shipping_address column

        assertThrows(Exception.class, () -> as(customer, () -> orderService.createOrder(tooLongAddress)));

        assertEquals(3, stock(product));
        assertEquals(0, ordersFor(product));
    }

    // ---------- Helpers ----------

    @Test
    void zeroAndNegativeQuantitiesAreRejectedWithoutChangingStock() {
        Product product = product(5);
        String customer = customer(Map.of(product, 1));

        for (int invalid : new int[]{0, -2}) {
            setCartQuantity(customer, invalid);
            assertThrows(IllegalStateException.class, () -> as(customer, () -> orderService.createOrder("Cairo")));
            assertEquals(5, stock(product));
        }
        // Repository guard (Spring wraps repository IllegalArgumentExceptions in InvalidDataAccessApiUsageException).
        for (Runnable call : List.<Runnable>of(
                () -> productRepo.reserveStock(product.getId(), 0),
                () -> productRepo.reserveStock(product.getId(), -3),
                () -> productRepo.releaseStock(product.getId(), -3))) {
            Exception error = assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class, call::run);
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
        }
        assertEquals(5, stock(product));
        assertEquals(0, ordersFor(product));
    }

    @Test
    void concurrentCancellationsRestoreStockExactlyOnce() throws Exception {
        Product product = product(3);
        String customer = customer(Map.of(product, 2));
        Long orderId = as(customer, () -> orderService.createOrder("Cairo")).id();
        assertEquals(1, stock(product));

        List<Result> results = race(List.of(
                () -> as(customer, () -> orderService.cancelOrder(orderId)),
                () -> as(customer, () -> orderService.cancelOrder(orderId)),
                () -> adminOrderService.changeStatus(orderId, OrderStatus.CANCELLED)));

        assertEquals(1, successes(results));
        results.stream().filter(r -> !r.succeeded()).forEach(r ->
                assertTrue(r.error() instanceof com.spring.eCommerce.exception.BusinessException, String.valueOf(r.error())));
        assertEquals(3, stock(product));
        assertEquals(OrderStatus.CANCELLED, orderStatus(orderId));

        assertThrows(com.spring.eCommerce.exception.BusinessException.class,
                () -> as(customer, () -> orderService.cancelOrder(orderId)));
        assertEquals(3, stock(product));
    }

    @Test
    void cancellationRacingANewOrderNeverLosesOrDuplicatesStock() throws Exception {
        Product product = product(1);
        String holder = customer(Map.of(product, 1));
        Long orderId = as(holder, () -> orderService.createOrder("Cairo")).id();
        assertEquals(0, stock(product));
        String buyer = customer(Map.of(product, 1), true);

        List<Result> results = race(List.of(
                () -> as(holder, () -> orderService.cancelOrder(orderId)),
                placeOrder(buyer)));

        assertTrue(results.get(0).succeeded(), String.valueOf(results.get(0).error()));
        int expected = results.get(1).succeeded() ? 0 : 1;
        if (!results.get(1).succeeded()) {
            assertInsufficientStock(List.of(results.get(1)));
        }
        assertEquals(expected, stock(product));
    }

    /**
     * Starts all tasks at the same instant on separate threads and waits for every transaction to finish.
     */
    private List<Result> race(List<? extends Callable<?>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Result>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return new Result(task.call(), null);
                    } catch (Throwable e) {
                        return new Result(null, e);
                    }
                }));
            }
            assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            start.countDown();
            List<Result> results = new ArrayList<>();
            for (Future<Result> future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<OrderResponseDto> placeOrder(String username) {
        return () -> as(username, () -> orderService.createOrder("Cairo"));
    }

    private <T> T as(String username, Supplier<T> action) {
        AppUserDetail principal = new TransactionTemplate(transactionManager).execute(status ->
                new AppUserDetail(userRepo.findByUsername(username).orElseThrow()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Product product(int stock) {
        Product product = productRepo.save(Product.builder()
                .name("stock-test-" + UUID.randomUUID())
                .price(new BigDecimal("10.00"))
                .availableQuantity(stock)
                .build());
        productIds.add(product.getId());
        return product;
    }

    private String customer(Map<Product, Integer> cart) {
        return customer(cart, false);
    }

    /**
     * Registers a customer and fills the cart; {@code skipStockCheck} fills it while the product is sold out.
     */
    private String customer(Map<Product, Integer> cart, boolean skipStockCheck) {
        String username = "stock_" + UUID.randomUUID().toString().substring(0, 12);
        authService.registerAsUser(new AppUser(null, "Stock Tester", username, "password123", null));
        usernames.add(username);
        cart.forEach((product, quantity) -> {
            if (skipStockCheck) {
                int original = stock(product);
                setStock(product, quantity);
                as(username, () -> cartService.addItemToCart(new CartItemRequestDto(product.getId(), quantity)));
                setStock(product, original);
            } else {
                as(username, () -> cartService.addItemToCart(new CartItemRequestDto(product.getId(), quantity)));
            }
        });
        return username;
    }

    private void setStock(Product product, int stock) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                productRepo.findById(product.getId()).orElseThrow().setAvailableQuantity(stock));
    }

    private void setCartQuantity(String username, int quantity) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                userRepo.findByUsername(username).orElseThrow().getCart().getCartItems()
                        .forEach(item -> item.setQuantity(quantity)));
    }

    private int stock(Product product) {
        return productRepo.findById(product.getId()).orElseThrow().getAvailableQuantity();
    }

    private OrderStatus orderStatus(Long orderId) {
        return orderRepo.findById(orderId).orElseThrow().getOrderStatus();
    }

    private long ordersFor(Product product) {
        return new TransactionTemplate(transactionManager).execute(status -> orderRepo.findAll().stream()
                .filter(order -> order.getOrderStatus() != OrderStatus.CANCELLED)
                .filter(order -> order.getOrderItems().stream()
                        .anyMatch(item -> item.getProduct().getId().equals(product.getId())))
                .count());
    }

    private int unitsOrdered(Product product) {
        return new TransactionTemplate(transactionManager).execute(status -> orderRepo.findAll().stream()
                .filter(order -> order.getOrderStatus() != OrderStatus.CANCELLED)
                .flatMap(order -> order.getOrderItems().stream())
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .mapToInt(item -> item.getQuantity())
                .sum());
    }

    private record Result(Object value, Throwable error) {
        boolean succeeded() {
            return error == null;
        }
    }
}
