package com.igot.cb.customFields.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

class CustomFieldEntityTest {

    @Test
    void testAllArgsConstructorAndGetters() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode().put("field", "value");

        String id = "cf1";
        Boolean mandatory = true;
        Boolean active = true;
        Timestamp createdOn = new Timestamp(System.currentTimeMillis());
        Timestamp updatedOn = new Timestamp(System.currentTimeMillis());

        CustomFieldEntity entity = new CustomFieldEntity(
                id,
                jsonNode,
                mandatory,
                active,
                createdOn,
                updatedOn
        );

        assertEquals(id, entity.getCustomFiledId());
        assertEquals(jsonNode, entity.getCustomFieldData());
        assertEquals(mandatory, entity.getIsMandatory());
        assertEquals(active, entity.getIsActive());
        assertEquals(createdOn, entity.getCreatedOn());
        assertEquals(updatedOn, entity.getUpdatedOn());
    }

    @Test
    void testNoArgsConstructorAndSetters() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode().put("field", "value");

        CustomFieldEntity entity = new CustomFieldEntity();

        entity.setCustomFiledId("cf2");
        entity.setCustomFieldData(jsonNode);
        entity.setIsMandatory(false);
        entity.setIsActive(false);

        Timestamp now = new Timestamp(System.currentTimeMillis());
        entity.setCreatedOn(now);
        entity.setUpdatedOn(now);

        assertEquals("cf2", entity.getCustomFiledId());
        assertEquals(jsonNode, entity.getCustomFieldData());
        assertFalse(entity.getIsMandatory());
        assertFalse(entity.getIsActive());
        assertEquals(now, entity.getCreatedOn());
        assertEquals(now, entity.getUpdatedOn());
    }
}
