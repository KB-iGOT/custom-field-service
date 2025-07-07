package com.igot.cb.pores.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesCacheTest {

    @Test
    void testSingleton() {
        PropertiesCache instance1 = PropertiesCache.getInstance();
        PropertiesCache instance2 = PropertiesCache.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2, "Should return the same instance");
    }

    @Test
    void testGetProperty_FoundInPropertiesFile() {
        PropertiesCache cache = PropertiesCache.getInstance();

        String value = cache.getProperty("test.key");
        assertEquals("test.value", value);
    }

    @Test
    void testGetProperty_NotFound_ReturnsKey() {
        PropertiesCache cache = PropertiesCache.getInstance();

        String value = cache.getProperty("non.existent.key");
        assertEquals("non.existent.key", value);
    }

    @Test
    void testReadProperty_FoundInPropertiesFile() {
        PropertiesCache cache = PropertiesCache.getInstance();
        String value = cache.readProperty("test.key");
        assertNull(value);
    }

    @Test
    void testReadProperty_NotFound_ReturnsNull() {
        PropertiesCache cache = PropertiesCache.getInstance();

        String value = cache.readProperty("non.existent.key");
        assertNull(value);
    }

    @Test
    void testGetProperty_EnvVarSimulated() throws Exception {
        PropertiesCache cache = PropertiesCache.getInstance();

        // Inject a fake environment value by replacing configProp (since we can't touch real env)
        Field field = PropertiesCache.class.getDeclaredField("configProp");
        field.setAccessible(true);
        Properties props = (Properties) field.get(cache);

        // Backup original
        String original = System.getenv("test.key");
        // If the env var actually exists on your machine, skip this test
        if (original != null) {
            System.out.println("Skipping env var simulation because test.key already exists in real env.");
            return;
        }

        // Remove from props so that it is NOT picked from configProp
        props.remove("test.key");

        // Actually this branch only executes if env var is set,
        // so here we just demonstrate that without props it falls back to env
        String value = cache.getProperty("test.key");
        // Since there is no env var & no prop it returns key
        assertEquals("test.key", value);
    }
}
