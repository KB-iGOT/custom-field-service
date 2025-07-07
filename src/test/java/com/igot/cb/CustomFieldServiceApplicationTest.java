package com.igot.cb;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

class CustomFieldServiceApplicationTest {

    @Test
    void testRestTemplateBeanCreation() {
        CustomFieldServiceApplication app = new CustomFieldServiceApplication();
        RestTemplate restTemplate = app.restTemplate();

        assertNotNull(restTemplate, "RestTemplate should not be null");
        assertNotNull(restTemplate.getRequestFactory(), "RequestFactory should not be null");
    }

    @Test
    void testGetClientHttpRequestFactory() {
        CustomFieldServiceApplication app = new CustomFieldServiceApplication();
        ClientHttpRequestFactory factory = invokeGetClientHttpRequestFactory(app);

        assertNotNull(factory, "ClientHttpRequestFactory should not be null");
    }

    // Helper to call private method via reflection
    private ClientHttpRequestFactory invokeGetClientHttpRequestFactory(CustomFieldServiceApplication app) {
        try {
            var method = CustomFieldServiceApplication.class.getDeclaredMethod("getClientHttpRequestFactory");
            method.setAccessible(true);
            return (ClientHttpRequestFactory) method.invoke(app);
        } catch (Exception e) {
            fail("Failed to invoke getClientHttpRequestFactory: " + e.getMessage());
            return null;
        }
    }
}
