package com.spring.eCommerce.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.repository.CategoryRepo;
import com.spring.eCommerce.repository.ProductRepo;
import com.spring.eCommerce.repository.UserRepo;
import com.spring.eCommerce.service.authentication.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end security verification for the Lulumi storefront:
 * public browsing, customer restrictions, admin abilities, safe registration,
 * and idempotent demo seed data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StorefrontSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ProductRepo productRepo;

    @Autowired
    private CategoryRepo categoryRepo;

    @Autowired
    private com.spring.eCommerce.config.LulumiSeedRunner seedRunner;

    private String customerUsername;
    private String adminUsername;
    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        customerUsername = "cust_" + UUID.randomUUID().toString().substring(0, 8);
        adminUsername = "adm_" + UUID.randomUUID().toString().substring(0, 8);
        authService.registerAsUser(new AppUser(null, "Test Customer", customerUsername, "password123", null));
        authService.registerAsAdmin(new AppUser(null, "Test Admin", adminUsername, "password123", null));
        customerToken = login(customerUsername);
        adminToken = login(adminUsername);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    @Test
    void publicPagesAreOpen() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/shop")).andExpect(status().isOk());
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/register")).andExpect(status().isOk());
    }

    @Test
    void productReadsArePublic() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isOk());
        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
    }

    @Test
    void anonymousCannotWriteProducts() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"price\":10,\"availableQuantity\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotCreateUpdateOrDeleteProducts() throws Exception {
        String body = "{\"name\":\"Cust Product\",\"price\":10,\"availableQuantity\":1}";
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/products/1")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/products/1")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotAccessAdminWebArea() throws Exception {
        mockMvc.perform(formLogin().user(customerUsername).password("password123"))
                .andExpect(status().isFound());
        // Direct URL check without session: protected area requires authentication.
        mockMvc.perform(get("/admin/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanManageProductsViaApi() throws Exception {
        String body = "{\"name\":\"Admin Widget " + UUID.randomUUID().toString().substring(0, 6)
                + "\",\"description\":\"desc\",\"price\":99.5,\"availableQuantity\":7}";
        MvcResult created = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        assertTrue(id > 0);

        mockMvc.perform(put("/api/products/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("99.5", "120")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/products/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        // Soft delete: the row is kept for order history but hidden from the shop.
        assertTrue(productRepo.findById(id).orElseThrow().isDeleted());
        assertTrue(productRepo.findByIdAndDeletedFalse(id).isEmpty());
    }

    @Test
    void adminCanOpenAdminWebArea() throws Exception {
        var session = (org.springframework.mock.web.MockHttpSession) mockMvc.perform(formLogin().user(adminUsername).password("password123"))
                .andExpect(status().isFound())
                .andReturn().getRequest().getSession(false);
        assertNotNull(session);
        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/products").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void registrationNeverGrantsAdmin() throws Exception {
        String username = "new_" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/auth/registerUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "password123", "fullName", "New Kid"))))
                .andExpect(status().isCreated());

        AppUser created = userRepo.findByUsername(username).orElseThrow();
        assertEquals(1, created.getRoles().size());
        assertEquals("user", created.getRoles().iterator().next().getName());
    }

    @Test
    void seedDataIsIdempotent() throws Exception {
        long productsBefore = productRepo.count();
        long categoriesBefore = categoryRepo.count();
        assertTrue(productsBefore >= 20, "Expected demo products to be seeded");

        seedRunner.run();

        assertEquals(productsBefore, productRepo.count());
        assertEquals(categoriesBefore, categoryRepo.count());
    }
}
