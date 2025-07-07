package com.igot.cb.customFields.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.transactional.cassandrautils.CassandraOperationImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomFieldsServiceImplMethodTest {

    @InjectMocks
    private CustomFieldsServiceImpl service;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CassandraOperationImpl cassandraOperation;

    @Mock
    private CbServerProperties cbServerProperties;


    @Test
    void test_removeCustomFieldFromOrg_avoidsNPE_whenDataIsValid() throws Exception {
        // Arrange
        String customFieldId = "field123";
        String orgId = "org456";

        String customFieldsJson = "{ \"customFieldIds\": [\"field123\", \"field456\"], \"customFieldsCount\": 2 }";

        Map<String, Object> orgMap = new HashMap<>();
        orgMap.put(Constants.CUSTOM_FIELDS_DATA, customFieldsJson);
        orgMap.put(Constants.ID, orgId);
        List<Map<String, Object>> orgList = Collections.singletonList(orgMap);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.ORG_TABLE),
                anyMap(),
                anyList(),
                isNull()))
                .thenReturn(orgList);

        Map<String, Object> customFieldsDataMap = new HashMap<>();
        customFieldsDataMap.put(Constants.CUSTOM_FIELD_IDS, new ArrayList<>(Arrays.asList("field123", "field456")));
        customFieldsDataMap.put(Constants.CUSTOM_FIELDS_COUNT, 2);

        when(objectMapper.readValue(eq(customFieldsJson), eq(Map.class)))
                .thenReturn(customFieldsDataMap);

        ObjectNode mockJsonNode = mock(ObjectNode.class);
        when(mockJsonNode.get(Constants.ORGANIZATION_ID)).thenReturn(new TextNode(orgId));
        when(mockJsonNode.has(Constants.TYPE)).thenReturn(false);

        // Act
        Method method = CustomFieldsServiceImpl.class.getDeclaredMethod("removeCustomFieldFromOrg", String.class, JsonNode.class);
        method.setAccessible(true);

        method.invoke(service, customFieldId, mockJsonNode);

        // Assert
        verify(cassandraOperation).updateRecord(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.ORG_TABLE), anyMap());
    }


    @Test
    void test_addCustomFieldToOrg_maxLimitExceeded() throws Exception {
        // Arrange
        String customFieldId = "cf1";
        String orgId = "org1";

        JsonNode customFieldData = mock(JsonNode.class);
        JsonNode orgIdNode = mock(JsonNode.class);

        when(customFieldData.get(Constants.ORGANIZATION_ID)).thenReturn(orgIdNode);
        when(orgIdNode.asText()).thenReturn(orgId);
        when(customFieldData.has(Constants.TYPE)).thenReturn(false);

        Map<String, Object> existingFields = new HashMap<>();
        existingFields.put(Constants.CUSTOM_FIELD_IDS, new ArrayList<>(List.of("cf2", "cf3")));
        existingFields.put(Constants.CUSTOM_FIELDS_COUNT, 10);

        String existingFieldsJson = "{ \"customFieldIds\": [\"cf2\", \"cf3\"], \"customFieldsCount\": 10 }";

        Map<String, Object> orgMap = new HashMap<>();
        orgMap.put(Constants.CUSTOM_FIELDS_DATA, existingFieldsJson);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                anyString(), anyString(), anyMap(), anyList(), isNull()))
                .thenReturn(List.of(orgMap));

        when(objectMapper.readValue(eq(existingFieldsJson), eq(Map.class)))
                .thenReturn(existingFields);

        when(cbServerProperties.getCustomFieldMaxAllowedCount()).thenReturn(10);

        // Act
        Method method = CustomFieldsServiceImpl.class.getDeclaredMethod("addCustomFieldToOrg", String.class, JsonNode.class);
        method.setAccessible(true);

        Object result = method.invoke(service, customFieldId, customFieldData);

        // Assert
        assertNotNull(result);
        assertTrue(result.toString().contains("Cannot enable this custom field"),
                "Should return message about exceeding the maximum limit");
    }
}
