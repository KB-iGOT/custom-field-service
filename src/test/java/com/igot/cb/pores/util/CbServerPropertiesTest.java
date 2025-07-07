package com.igot.cb.pores.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CbServerPropertiesTest {

    @Test
    void testGettersAndSetters() {
        CbServerProperties props = new CbServerProperties();

        props.setSearchResultRedisTtl(100L);
        props.setCustomFieldEntity("entity");
        props.setCustomFieldElasticMappingJsonPath("mapping.json");
        props.setCustomFieldValidationFilePath("validation.json");
        props.setCustomFieldMaxLevel(2);
        props.setCustomFieldListValidationFilePath("list-validation.json");
        props.setAllowedExtensions("pdf");
        props.setAllowedContentTypes("application/pdf");
        props.setCustomFieldListUpdateValidationFilePath("list-update-validation.json");
        props.setCustomFieldStatusUpdateValidationFilePath("status-update-validation.json");
        props.setCustomFieldMaxAllowedCount(5);

        assertEquals(100L, props.getSearchResultRedisTtl());
        assertEquals("entity", props.getCustomFieldEntity());
        assertEquals("mapping.json", props.getCustomFieldElasticMappingJsonPath());
        assertEquals("validation.json", props.getCustomFieldValidationFilePath());
        assertEquals(2, props.getCustomFieldMaxLevel());
        assertEquals("list-validation.json", props.getCustomFieldListValidationFilePath());
        assertEquals("pdf", props.getAllowedExtensions());
        assertEquals("application/pdf", props.getAllowedContentTypes());
        assertEquals("list-update-validation.json", props.getCustomFieldListUpdateValidationFilePath());
        assertEquals("status-update-validation.json", props.getCustomFieldStatusUpdateValidationFilePath());
        assertEquals(5, props.getCustomFieldMaxAllowedCount());
    }
}
