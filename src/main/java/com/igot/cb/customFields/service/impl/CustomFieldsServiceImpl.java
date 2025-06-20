package com.igot.cb.customFields.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.customFields.entity.CustomFieldEntity;
import com.igot.cb.customFields.repository.CustomFieldRepository;
import com.igot.cb.customFields.service.CustomFieldsService;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.util.*;
import com.igot.cb.transactional.cassandrautils.CassandraOperationImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class CustomFieldsServiceImpl implements CustomFieldsService {
    @Autowired
    private CustomFieldRepository customFieldRepository;
    @Autowired
    private PayloadValidation payloadValidation;
    @Autowired
    private AccessTokenValidator accessTokenValidator;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EsUtilService esUtilService;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private CbServerProperties cbServerProperties;
    @Autowired
    private CassandraOperationImpl cassandraOperation;

    @Override
    public ApiResponse createCustomFields(JsonNode customFieldsData, String token) {
        log.info("CustomFieldsServiceImpl::createCustomFields:creating custom fields");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.CREATE_CUSTOM_FIELD_API);
        payloadValidation.validatePayload(cbServerProperties.getCustomFieldValidationFilePath(), customFieldsData);

        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                response.setResponseCode(HttpStatus.UNAUTHORIZED);
                return response;
            }

            // Create a mutable copy of the JSON data as ObjectNode
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);
            ObjectNode customFieldsDataObjectNode = (ObjectNode) customFieldsData;
            customFieldsDataObjectNode.put(Constants.CREATED_BY, userId);
            customFieldsDataObjectNode.put(Constants.CREATED_ON, formattedCurrentTime);
            customFieldsDataObjectNode.put(Constants.UPDATED_ON, formattedCurrentTime);
            customFieldsDataObjectNode.put(Constants.IS_ACTIVE, true);
            customFieldsDataObjectNode.put(Constants.IS_ENABLED, false);
            customFieldsDataObjectNode.put(Constants.IS_MANDATORY, customFieldsData.get(Constants.IS_MANDATORY).asBoolean(false));
            // Create and populate entity
            CustomFieldEntity customField = new CustomFieldEntity();
            String customFieldId = UUID.randomUUID().toString();
            customField.setCustomFiledId(customFieldId);
            customField.setCustomFieldData(customFieldsDataObjectNode);
            customField.setIsMandatory(customFieldsData.get(Constants.IS_MANDATORY).asBoolean(false));
            customField.setIsActive(true);
            customField.setCreatedOn(currentTime);
            customField.setUpdatedOn(currentTime);

            // Save to database
            customFieldRepository.save(customField);

            // Convert to map for response and ES
            Map<String, Object> customFieldMap = objectMapper.convertValue(customFieldsDataObjectNode, Map.class);
            customFieldMap.put(Constants.CUSTOM_FIELD_ID, customFieldId);

            esUtilService.addDocument(cbServerProperties.getCustomFieldEntity(), customFieldId, customFieldMap, cbServerProperties.getCustomFieldElasticMappingJsonPath());

            // Store in Redis cache
            JsonNode responseNode = objectMapper.valueToTree(customFieldMap);
            cacheService.putCache("CUSTOM_FIELD_" + customFieldId, responseNode);

            // Set success response
            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setMessage(Constants.SUCCESS);
            response.setResult(customFieldMap);
        } catch (Exception e) {
            log.error("Failed to create custom field: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            return response;
        }
        return response;
    }

    @Override
    public ApiResponse readCustomField(String customFieldId, String token) {
        log.info("CustomFieldsServiceImpl::readCustomField: Getting custom field with ID: {}", customFieldId);
        ApiResponse response = new ApiResponse("customField.read");

        try {
            // Validate user token
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                response.setResponseCode(HttpStatus.UNAUTHORIZED);
                return response;
            }

            // Try to fetch from Redis cache first
            String cachedData = null;//cacheService.getCache("CUSTOM_FIELD_" + customFieldId);
            if (cachedData != null) {
                Map<String, Object> customFieldMap = objectMapper.convertValue(cachedData, Map.class);
                response.setResponseCode(HttpStatus.OK);
                response.setMessage(Constants.SUCCESS);
                response.setResult(customFieldMap);
                return response;
            }

            // If not in cache, fetch from database
            Optional<CustomFieldEntity> customFieldOpt = customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId);
            if (customFieldOpt.isEmpty()) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg("Custom field not found with ID: " + customFieldId);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                return response;
            }

            CustomFieldEntity customField = customFieldOpt.get();
            JsonNode customFieldData = customField.getCustomFieldData();
            Map<String, Object> customFieldMap = objectMapper.convertValue(customFieldData, Map.class);
            customFieldMap.put(Constants.CUSTOM_FIELD_ID, customFieldId);

            // Cache result for future requests
            cacheService.putCache("CUSTOM_FIELD_" + customFieldId, customFieldMap);

            response.setResponseCode(HttpStatus.OK);
            response.setMessage(Constants.SUCCESS);
            response.setResult(customFieldMap);

        } catch (Exception e) {
            log.error("Failed to read custom field: {}", e.getMessage(), e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setErrMsg("FAILED_TO_READ_CUSTOM_FIELD");
            response.setMessage(Constants.FAILED);
        }

        return response;
    }

    @Override
    public ApiResponse updateCustomField(String customFieldId, JsonNode customFieldsData, String token) {
        log.info("CustomFieldsServiceImpl::updateCustomField: Updating custom field with ID: {}", customFieldId);
        ApiResponse response = new ApiResponse("customField.update");

        try {
            // Validate payload
            payloadValidation.validatePayload(cbServerProperties.getCustomFieldValidationFilePath(), customFieldsData);

            // Validate user token
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                ProjectUtil.returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
                return response;
            }

            // Check if field exists and is active
            Optional<CustomFieldEntity> customFieldOpt = customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId);
            if (customFieldOpt.isEmpty()) {
                ProjectUtil.returnErrorMsg("Custom field not found or is inactive with ID: " + customFieldId, HttpStatus.NOT_FOUND, response, Constants.FAILED);
                return response;
            }

            CustomFieldEntity customField = customFieldOpt.get();
            JsonNode originalData = customField.getCustomFieldData();

            if(originalData.has(Constants.ORGANISATION_ID) && !originalData.get(Constants.ORGANISATION_ID).asText().equals(customFieldsData.get(Constants.ORGANISATION_ID).asText())) {
                ProjectUtil.returnErrorMsg("Organisation ID cannot be changed", HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            // Update entity data
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);

            ObjectNode customFieldsDataObjectNode = (ObjectNode) customFieldsData;
            customFieldsDataObjectNode.put(Constants.ORGANISATION_ID, customFieldOpt.get().getCustomFieldData().get(Constants.ORGANISATION_ID).asText());
            customFieldsDataObjectNode.put(Constants.UPDATED_BY, userId); // Add the updated by field
            customFieldsDataObjectNode.put(Constants.UPDATED_ON, formattedCurrentTime);
            customFieldsDataObjectNode.put(Constants.IS_ENABLED, false);
            customFieldsDataObjectNode.put(Constants.IS_MANDATORY, customFieldsData.get(Constants.IS_MANDATORY).asBoolean(originalData.get(Constants.IS_MANDATORY).asBoolean()));
            customFieldsDataObjectNode.put(Constants.IS_ACTIVE, originalData.get(Constants.IS_ACTIVE).asBoolean(originalData.get(Constants.IS_ACTIVE).asBoolean()));

            // Keep original creation info
            if (originalData.has(Constants.CREATED_BY)) {
                customFieldsDataObjectNode.put(Constants.CREATED_BY, originalData.get(Constants.CREATED_BY).asText());
            }
            if (originalData.has(Constants.CREATED_ON)) {
                customFieldsDataObjectNode.put(Constants.CREATED_ON, originalData.get(Constants.CREATED_ON).asText());
            }

            // Rest of your existing code...
            customField.setCustomFieldData(customFieldsDataObjectNode);
            customField.setIsMandatory(customFieldsData.get(Constants.IS_MANDATORY).asBoolean(originalData.get(Constants.IS_MANDATORY).asBoolean()));
            customField.setUpdatedOn(currentTime);
            customFieldRepository.save(customField);

            // Update ES and cache
            Map<String, Object> customFieldMap = objectMapper.convertValue(customFieldsDataObjectNode, Map.class);
            customFieldMap.put(Constants.CUSTOM_FIELD_ID, customFieldId);

            esUtilService.updateDocument(
                    cbServerProperties.getCustomFieldEntity(),
                    customFieldId,
                    customFieldMap,
                    cbServerProperties.getCustomFieldElasticMappingJsonPath()
            );

            // Update Redis cache
            JsonNode responseNode = objectMapper.valueToTree(customFieldMap);
            cacheService.putCache("CUSTOM_FIELD_" + customFieldId, responseNode);
            response.setResponseCode(HttpStatus.OK);
            response.setMessage(Constants.SUCCESS);
            response.setResult(customFieldMap);

        } catch (Exception e) {
            ProjectUtil.returnErrorMsg("Failed to update custom field: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            log.error("Failed to update custom field: {}", e.getMessage(), e);
            return response;
        }
        return response;
    }

    @Override
    public ApiResponse deleteCustomField(String customFieldId, String token) {
        log.info("CustomFieldsServiceImpl::deleteCustomField: Soft deleting custom field with ID: {}", customFieldId);
        ApiResponse response = new ApiResponse("customField.delete");

        try {
            // Validate user token
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                ProjectUtil.returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
                return response;
            }

            // Check if field exists
            Optional<CustomFieldEntity> customFieldOpt = customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId);
            if (customFieldOpt.isEmpty()) {
                ProjectUtil.returnErrorMsg("Custom field not found with ID: " + customFieldId, HttpStatus.NOT_FOUND, response, Constants.FAILED);
                return response;
            }

            // Soft delete by updating isActive flag
            CustomFieldEntity customField = customFieldOpt.get();
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);

            // Update the JSON data to reflect the deletion
            JsonNode customFieldData = customField.getCustomFieldData();
            ObjectNode customFieldDataNode = (ObjectNode) customFieldData;
            customFieldDataNode.put(Constants.IS_ACTIVE, false);
            customFieldDataNode.put(Constants.UPDATED_BY, userId);
            customFieldDataNode.put(Constants.UPDATED_ON, formattedCurrentTime);

            // Update the entity
            customField.setCustomFieldData(customFieldDataNode);
            customField.setIsActive(false);
            customField.setUpdatedOn(currentTime);
            customFieldRepository.save(customField);

            // Update ES document instead of deleting it
            Map<String, Object> customFieldMap = objectMapper.convertValue(customFieldDataNode, Map.class);
            customFieldMap.put(Constants.CUSTOM_FIELD_ID, customFieldId);

            esUtilService.updateDocument(
                    cbServerProperties.getCustomFieldEntity(),
                    customFieldId,
                    customFieldMap,
                    cbServerProperties.getCustomFieldElasticMappingJsonPath()
            );

            // Remove from cache
            cacheService.deleteCache("CUSTOM_FIELD_" + customFieldId);

            // Set success response
            response.setResponseCode(HttpStatus.OK);
            response.setMessage(Constants.SUCCESS);
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put(Constants.CUSTOM_FIELD_ID_PARAM, customFieldId);
            resultMap.put(Constants.STATUS, Constants.DELETED);
            response.setResult(resultMap);

        } catch (Exception e) {
            log.error("Failed to delete custom field: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg("Failed to delete custom field: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            return response;
        }

        return response;
    }

    private String getFormattedCurrentTime(Timestamp currentTime) {
        ZonedDateTime zonedDateTime = currentTime.toInstant().atZone(ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.TIME_FORMAT);
        return zonedDateTime.format(formatter);
    }

    @Override
    public ApiResponse searchCustomFields(SearchCriteria searchCriteria) {
        log.info("CustomFieldsServiceImpl::searchCustomFields: Searching custom fields");
        ApiResponse response = new ApiResponse("customField.search");

        try {

            // Default to active records if not specified
            if (!searchCriteria.getFilterCriteriaMap().containsKey(Constants.IS_ACTIVE)) {
                searchCriteria.getFilterCriteriaMap().put(Constants.IS_ACTIVE, true);
            }

            // Execute search in Elasticsearch
            SearchResult searchResult = esUtilService.searchDocuments(
                    cbServerProperties.getCustomFieldEntity(),
                    searchCriteria,
                    cbServerProperties.getCustomFieldElasticMappingJsonPath()
            );

            response.setResponseCode(HttpStatus.OK);
            response.setMessage(Constants.SUCCESS);
            response.getResult().put(Constants.SEARCH_RESULTS, searchResult);
        } catch (Exception e) {
            log.error("Failed to search custom fields: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg("Failed to search custom fields: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            return response;
        }
        return response;
    }

    @Override
    public ApiResponse uploadMasterListCustomField(MultipartFile file, String customFieldsMasterDataJson, String token) {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.CREATE_CUSTOM_FIELD_API);
        log.info("CustomFieldsServiceImpl::uploadMasterListCustomField: Uploading master list custom field");

        Map<String, Object> customFieldsData;
        JsonNode payloadNode;
        try {
            customFieldsData = objectMapper.readValue(customFieldsMasterDataJson, Map.class);
            payloadNode = objectMapper.valueToTree(customFieldsData);
        } catch (Exception e) {
            ProjectUtil.returnErrorMsg(Constants.INVALID_JSON_CUSTOM_FIELDS_MASTER_DATA + e.getMessage(), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            return response;
        }
        payloadValidation.validatePayload(cbServerProperties.getCustomFieldListValidationFilePath(), payloadNode);

        String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
        if (StringUtils.isBlank(userId)) {
            ProjectUtil.returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
            return response;
        }

        if (file == null || file.isEmpty()) {
            ProjectUtil.returnErrorMsg(Constants.UPLOADED_FILE_IS_EMPTY, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            return response;
        }

        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        List<String> extensions = Arrays.asList(cbServerProperties.getAllowedExtensions().split(","));
        List<String> contentTypes = Arrays.asList(cbServerProperties.getAllowedContentTypes().split(","));

        if (fileName == null ||
                extensions.stream().noneMatch(fileName::endsWith) ||
                contentType == null ||
                contentTypes.stream().noneMatch(contentType::equals)) {
            ProjectUtil.returnErrorMsg(Constants.ONLY_EXCEL_FILES_ALLOWED, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            return response;
        }

        Object customFieldDataObj = customFieldsData.get(Constants.CUSTOM_FIELD_DATA);
        List<?> originalCustomFieldData = (List<?>) customFieldDataObj;
        customFieldsData.put(Constants.LEVELS, originalCustomFieldData.size());
        if (!(customFieldDataObj instanceof List<?> customFieldDataList) || customFieldDataList.isEmpty()) {
            ProjectUtil.returnErrorMsg(Constants.CUSTOM_FIELD_DATA_NON_EMPTY, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            return response;
        }

        int maxLevel = cbServerProperties.getCustomFieldMaxLevel();
        if (customFieldDataList.size() > maxLevel) {
            ProjectUtil.returnErrorMsg(String.format(Constants.CUSTOM_FIELD_DATA_LEVELS_EXCEED, maxLevel), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            return response;
        }

        String[] headers;
        int levels;
        List<Row> dataRows = new ArrayList<>();

        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                ProjectUtil.returnErrorMsg(Constants.EXCEL_HEADER_ROW_REQUIRED, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            int excelColumns = headerRow.getLastCellNum();
            int jsonLevels = customFieldDataList.size();

            if (excelColumns > jsonLevels) {
                ProjectUtil.returnErrorMsg(
                        String.format(Constants.EXCEL_MORE_COLUMNS_THAN_LEVELS, excelColumns, jsonLevels),
                        HttpStatus.BAD_REQUEST, response, Constants.FAILED
                );
                return response;
            }

            if (excelColumns > maxLevel) {
                ProjectUtil.returnErrorMsg(String.format(Constants.EXCEL_MORE_THAN_MAX_LEVELS, maxLevel), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            levels = jsonLevels;

            headers = new String[levels];
            for (int j = 0; j < levels; j++) {
                headers[j] = headerRow.getCell(j).getStringCellValue();
            }
            for (int i = 0; i < levels; i++) {
                Cell headerCell = headerRow.getCell(i);
                if (headerCell == null) continue;
                String header = headerCell.getStringCellValue().trim();
                Map<?, ?> fieldMeta = (Map<?, ?>) customFieldDataList.get(i);

                String expectedAttribute = String.valueOf(fieldMeta.get(Constants.ATTRIBUTE_NAME));
                int expectedLevel = Integer.parseInt(String.valueOf(fieldMeta.get(Constants.LEVEL)));
                if (!header.equalsIgnoreCase(expectedAttribute)) {
                    ProjectUtil.returnErrorMsg(String.format(Constants.HEADER_MISMATCH, header, expectedAttribute, (i + 1)), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                    return response;
                }
                if (expectedLevel != (i + 1)) {
                    ProjectUtil.returnErrorMsg(String.format(Constants.LEVEL_MISMATCH, (i + 1), expectedLevel, (i + 1)), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                    return response;
                }
            }
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) dataRows.add(row);
            }
        } catch (Exception e) {
            ProjectUtil.returnErrorMsg(String.format(Constants.ERROR_READING_EXCEL, e.getMessage()), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            return response;
        }

        // Use the new method to get both hierarchies
        Map<String, ArrayNode> hierarchies = parseHierarchyWithReversed(headers, dataRows, levels);

        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String formattedCurrentTime = getFormattedCurrentTime(currentTime);
        customFieldsData.put(Constants.CREATED_BY, userId);
        customFieldsData.put(Constants.CREATED_ON, formattedCurrentTime);
        customFieldsData.put(Constants.UPDATED_ON, formattedCurrentTime);
        customFieldsData.put(Constants.CUSTOM_FIELD_DATA, hierarchies.get(Constants.CUSTOM_FIELD_DATA));
        customFieldsData.put(Constants.REVERSED_ORDER_CUSTOM_FIELD_DATA, hierarchies.get(Constants.REVERSED_ORDER_CUSTOM_FIELD_DATA));
        customFieldsData.put(Constants.IS_ACTIVE, true);
        customFieldsData.put(Constants.IS_ENABLED, false);
        customFieldsData.put(Constants.ORIGINAL_CUSTOM_FIELD_DATA, originalCustomFieldData);

        JsonNode jsonNode = objectMapper.valueToTree(customFieldsData);
        CustomFieldEntity customField = new CustomFieldEntity();
        String customFieldId = UUID.randomUUID().toString();
        customField.setCustomFiledId(customFieldId);
        customField.setCustomFieldData(jsonNode);
        customField.setIsActive(true);
        customField.setCreatedOn(currentTime);
        customField.setUpdatedOn(currentTime);

        customFieldRepository.save(customField);

        Map<String, Object> customFieldMap = objectMapper.convertValue(customFieldsData, Map.class);
        customFieldMap.put(Constants.CUSTOM_FIELD_ID, customFieldId);
        esUtilService.addDocument(cbServerProperties.getCustomFieldEntity(), customFieldId, customFieldMap, cbServerProperties.getCustomFieldElasticMappingJsonPath());
        cacheService.putCache(Constants.CUSTOM_FIELD + customFieldId, objectMapper.valueToTree(customFieldMap));

        response.setResponseCode(HttpStatus.OK);
        response.getParams().setStatus(Constants.SUCCESS);
        response.setMessage(Constants.SUCCESS);
        response.setResult(customFieldMap);
        return response;
    }

    private Map<String, ArrayNode> parseHierarchyWithReversed(String[] headers, List<Row> dataRows, int levels) {
        ArrayNode root = objectMapper.createArrayNode();
        // List of maps for each level to track existing nodes
        List<Map<String, ObjectNode>> levelMaps = new ArrayList<>();
        for (int i = 0; i < levels; i++) {
            levelMaps.add(new HashMap<>());
        }

        for (Row row : dataRows) {
            ArrayNode currentArray = root;
            ObjectNode parentNode = null;
            String parentFieldName = null;
            String parentFieldValue = null;

            for (int j = 0; j < levels; j++) {
                Cell valueCell = row.getCell(j);
                String value = (valueCell != null) ? valueCell.toString().trim() : null;
                if (value == null || value.isEmpty()) break;
                String fieldName = headers[j];
                String key = fieldName + ":" + value + (parentFieldName != null ? "|" + parentFieldName + ":" + parentFieldValue : "");

                Map<String, ObjectNode> currentLevelMap = levelMaps.get(j);
                ObjectNode foundNode = currentLevelMap.get(key);

                if (foundNode == null) {
                    ObjectNode newNode = objectMapper.createObjectNode();
                    newNode.put(Constants.FIELD_NAME, fieldName);
                    newNode.put(Constants.FIELD_VALUE, value);
                    newNode.put(Constants.FIELD_ATTRIBUTE, fieldName);
                    if (parentFieldName != null && parentFieldValue != null) {
                        newNode.put(Constants.PARENT_FIELD_NAME, parentFieldName);
                        newNode.put(Constants.PARENT_FIELD_VALUE, parentFieldValue);
                    }
                    ArrayNode childArray = objectMapper.createArrayNode();
                    newNode.set(Constants.FIELD_VALUES, childArray);
                    currentArray.add(newNode);
                    currentLevelMap.put(key, newNode);
                    foundNode = newNode;
                }
                parentFieldName = fieldName;
                parentFieldValue = value;
                parentNode = foundNode;
                currentArray = (ArrayNode) foundNode.get(Constants.FIELD_VALUES);
            }
        }

        // Helper for reversed hierarchy
        List<ObjectNode> reversedList = new ArrayList<>();
        buildReversedHierarchyList(root, new ArrayList<>(), reversedList);

        ArrayNode reversedOrderCustomFieldData = objectMapper.valueToTree(reversedList);

        Map<String, ArrayNode> result = new HashMap<>();
        result.put(Constants.CUSTOM_FIELD_DATA, root);
        result.put(Constants.REVERSED_ORDER_CUSTOM_FIELD_DATA, reversedOrderCustomFieldData);
        return result;
    }

    private void buildReversedHierarchyList(JsonNode node, List<ObjectNode> path, List<ObjectNode> reversedList) {
        // Handle array nodes - process each child separately
        if (node.isArray()) {
            for (JsonNode child : node) {
                buildReversedHierarchyList(child, new ArrayList<>(), reversedList);
            }
            return;
        }

        // Add current node to the path
        path.add((ObjectNode) node);

        // Get children if any
        ArrayNode children = (ArrayNode) node.get(Constants.FIELD_VALUES);

        if (children == null || children.isEmpty()) {
            // This is a leaf node - create the reversed hierarchy
            ObjectNode reversedNode = null;

            // Start from leaf (last in path) and work up to root
            for (int i = 0; i < path.size(); i++) {
                ObjectNode current = objectMapper.createObjectNode();
                final ObjectNode nodeAtI = path.get(i);

                // Copy all fields except fieldValues
                nodeAtI.fieldNames().forEachRemaining(field -> {
                    if (!Constants.FIELD_VALUES.equals(field)) {
                        current.set(field, nodeAtI.get(field));
                    }
                });

                if (reversedNode == null) {
                    // First node (leaf) gets empty fieldValues
                    current.set(Constants.FIELD_VALUES, objectMapper.createArrayNode());
                    reversedNode = current;
                } else {
                    // Parent nodes get the previous node as child
                    ArrayNode arr = objectMapper.createArrayNode();
                    arr.add(reversedNode);
                    current.set(Constants.FIELD_VALUES, arr);
                    reversedNode = current;
                }
            }

            // Add the reversed hierarchy to the result list
            reversedList.add(reversedNode);
        } else {
            // For non-leaf nodes, process each child
            for (JsonNode child : children) {
                // Use a copy of the path for each child branch
                buildReversedHierarchyList(child, new ArrayList<>(path), reversedList);
            }
        }
    }

    @Override
    public ApiResponse updateMasterListCustomField(MultipartFile file, String customFieldsMasterDataJson, String token) {
        ApiResponse response = ProjectUtil.createDefaultResponse("customFields.update.masterList");
        log.info("CustomFieldsServiceImpl::updateMasterListCustomField: Updating master list custom field");
        try {
            Map<String, Object> customFieldsData;
            JsonNode payloadNode;
            try {
                customFieldsData = objectMapper.readValue(customFieldsMasterDataJson, Map.class);
                payloadNode = objectMapper.valueToTree(customFieldsData);
            } catch (Exception e) {
                ProjectUtil.returnErrorMsg(Constants.INVALID_JSON_CUSTOM_FIELDS_MASTER_DATA + e.getMessage(), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            // Validate payload - this will check for required customFieldId
            payloadValidation.validatePayload(cbServerProperties.getCustomFieldListUpdateValidationFilePath(), payloadNode);

            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                ProjectUtil.returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
                return response;
            }

            // Check if custom field exists
            Optional<CustomFieldEntity> customFieldOpt = customFieldRepository.findByCustomFiledIdAndIsActiveTrue(
                    payloadNode.get(Constants.CUSTOM_FIELD_ID).asText()
            );
            if (customFieldOpt.isEmpty()) {
                ProjectUtil.returnErrorMsg("Custom field not found with ID: " + payloadNode.get(Constants.CUSTOM_FIELD_ID).asText(), HttpStatus.NOT_FOUND, response, Constants.FAILED);
                return response;
            }

            CustomFieldEntity existingCustomField = customFieldOpt.get();
            JsonNode existingData = existingCustomField.getCustomFieldData();

            if (file == null || file.isEmpty()) {
                ProjectUtil.returnErrorMsg(Constants.UPLOADED_FILE_IS_EMPTY, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            String fileName = file.getOriginalFilename();
            String contentType = file.getContentType();
            List<String> extensions = Arrays.asList(cbServerProperties.getAllowedExtensions().split(","));
            List<String> contentTypes = Arrays.asList(cbServerProperties.getAllowedContentTypes().split(","));

            if (fileName == null ||
                    extensions.stream().noneMatch(fileName::endsWith) ||
                    contentType == null ||
                    contentTypes.stream().noneMatch(contentType::equals)) {
                ProjectUtil.returnErrorMsg(Constants.ONLY_EXCEL_FILES_ALLOWED, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            Object customFieldDataObj = customFieldsData.get(Constants.CUSTOM_FIELD_DATA);
            if (!(customFieldDataObj instanceof List<?> customFieldDataList) || customFieldDataList.isEmpty()) {
                ProjectUtil.returnErrorMsg(Constants.CUSTOM_FIELD_DATA_NON_EMPTY, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            // originalCustomFieldData is used to preserve the original data for response
            List<?> originalCustomFieldData = (List<?>) customFieldDataObj;
            customFieldsData.put(Constants.LEVELS, originalCustomFieldData.size());

            int maxLevel = cbServerProperties.getCustomFieldMaxLevel();
            if (customFieldDataList.size() > maxLevel) {
                ProjectUtil.returnErrorMsg(String.format(Constants.CUSTOM_FIELD_DATA_LEVELS_EXCEED, maxLevel), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            String[] headers;
            int levels;
            List<Row> dataRows = new ArrayList<>();

            try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
                Sheet sheet = workbook.getSheetAt(0);
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    ProjectUtil.returnErrorMsg(Constants.EXCEL_HEADER_ROW_REQUIRED, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                    return response;
                }

                int excelColumns = headerRow.getLastCellNum();
                int jsonLevels = customFieldDataList.size();

                if (excelColumns > jsonLevels) {
                    ProjectUtil.returnErrorMsg(
                            String.format(Constants.EXCEL_MORE_COLUMNS_THAN_LEVELS, excelColumns, jsonLevels),
                            HttpStatus.BAD_REQUEST, response, Constants.FAILED
                    );
                    return response;
                }

                if (excelColumns > maxLevel) {
                    ProjectUtil.returnErrorMsg(String.format(Constants.EXCEL_MORE_THAN_MAX_LEVELS, maxLevel), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                    return response;
                }

                levels = jsonLevels;

                headers = new String[levels];
                for (int j = 0; j < levels; j++) {
                    headers[j] = headerRow.getCell(j).getStringCellValue();
                }
                for (int i = 0; i < levels; i++) {
                    Cell headerCell = headerRow.getCell(i);
                    if (headerCell == null) continue;
                    String header = headerCell.getStringCellValue().trim();
                    Map<?, ?> fieldMeta = (Map<?, ?>) customFieldDataList.get(i);

                    String expectedAttribute = String.valueOf(fieldMeta.get(Constants.ATTRIBUTE_NAME));
                    int expectedLevel = Integer.parseInt(String.valueOf(fieldMeta.get(Constants.LEVEL)));
                    if (!header.equalsIgnoreCase(expectedAttribute)) {
                        ProjectUtil.returnErrorMsg(String.format(Constants.HEADER_MISMATCH, header, expectedAttribute, (i + 1)), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                        return response;
                    }
                    if (expectedLevel != (i + 1)) {
                        ProjectUtil.returnErrorMsg(String.format(Constants.LEVEL_MISMATCH, (i + 1), expectedLevel, (i + 1)), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                        return response;
                    }
                }

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row != null) dataRows.add(row);
                }
            } catch (Exception e) {
                ProjectUtil.returnErrorMsg(String.format(Constants.ERROR_READING_EXCEL, e.getMessage()), HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            // Use the method to get both hierarchies
            Map<String, ArrayNode> hierarchies = parseHierarchyWithReversed(headers, dataRows, levels);

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);

            // Preserve original creation info and other fields
            ObjectNode existingDataNode = (ObjectNode) existingData;
            if (existingDataNode.has(Constants.CREATED_BY)) {
                customFieldsData.put(Constants.CREATED_BY, existingDataNode.get(Constants.CREATED_BY).asText());
            }
            if (existingDataNode.has(Constants.CREATED_ON)) {
                customFieldsData.put(Constants.CREATED_ON, existingDataNode.get(Constants.CREATED_ON).asText());
            }

            customFieldsData.put(Constants.UPDATED_BY, userId);
            customFieldsData.put(Constants.UPDATED_ON, formattedCurrentTime);
            customFieldsData.put(Constants.CUSTOM_FIELD_DATA, hierarchies.get(Constants.CUSTOM_FIELD_DATA));
            customFieldsData.put(Constants.REVERSED_ORDER_CUSTOM_FIELD_DATA, hierarchies.get(Constants.REVERSED_ORDER_CUSTOM_FIELD_DATA));
            customFieldsData.put(Constants.ORIGINAL_CUSTOM_FIELD_DATA, originalCustomFieldData);
            customFieldsData.put(Constants.IS_ENABLED, false);
            customFieldsData.put(Constants.IS_ACTIVE, true);

            // Preserve other important fields if present in existing data
            if (existingDataNode.has(Constants.IS_MANDATORY)) {
                customFieldsData.put(Constants.IS_MANDATORY, existingDataNode.get(Constants.IS_MANDATORY).asBoolean());
            }

            JsonNode jsonNode = objectMapper.valueToTree(customFieldsData);
            existingCustomField.setCustomFieldData(jsonNode);
            existingCustomField.setUpdatedOn(currentTime);

            customFieldRepository.save(existingCustomField);

            Map<String, Object> customFieldMap = objectMapper.convertValue(customFieldsData, Map.class);
            customFieldMap.put(Constants.CUSTOM_FIELD_ID, payloadNode.get(Constants.CUSTOM_FIELD_ID).asText());

            // Update ES document
            esUtilService.updateDocument(
                    cbServerProperties.getCustomFieldEntity(),
                    payloadNode.get(Constants.CUSTOM_FIELD_ID).asText(),
                    customFieldMap,
                    cbServerProperties.getCustomFieldElasticMappingJsonPath()
            );

            // Update Redis cache
            cacheService.putCache(Constants.CUSTOM_FIELD + payloadNode.get(Constants.CUSTOM_FIELD_ID).asText(), objectMapper.valueToTree(customFieldMap));

            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setMessage(Constants.SUCCESS);
            response.setResult(customFieldMap);
            return response;
        } catch (Exception e) {
            log.error("Exception in updateMasterListCustomField: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg("Failed to update master list custom field: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            return response;
        }
    }

    @Override
    public ApiResponse updateCustomFieldStatus(JsonNode updateCustomFieldStatusData, String token) {
        ApiResponse response = ProjectUtil.createDefaultResponse("customField.updateStatus");
        payloadValidation.validatePayload(cbServerProperties.getCustomFieldStatusUpdateValidationFilePath(), updateCustomFieldStatusData);
        try {
            String userId = accessTokenValidator.fetchUserIdFromAccessToken(token);
            if (StringUtils.isBlank(userId)) {
                ProjectUtil.returnErrorMsg(Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED, response, Constants.FAILED);
                return response;
            }

            String customFieldId = updateCustomFieldStatusData.get(Constants.CUSTOM_FIELD_ID).asText();
            boolean isEnabled = updateCustomFieldStatusData.get(Constants.IS_ENABLED).asBoolean();
            if (StringUtils.isBlank(customFieldId)) {
                ProjectUtil.returnErrorMsg("CustomFieldId is required", HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            Optional<CustomFieldEntity> customFieldOpt = customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId);
            if (customFieldOpt.isEmpty()) {
                ProjectUtil.returnErrorMsg("Custom field not found with ID: " + customFieldId, HttpStatus.NOT_FOUND, response, Constants.FAILED);
                return response;
            }

            CustomFieldEntity customField = customFieldOpt.get();
            JsonNode customFieldData = customField.getCustomFieldData();
            boolean currentStatus = customFieldData.has(Constants.IS_ENABLED) &&
                    customFieldData.get(Constants.IS_ENABLED).asBoolean();

            if (isEnabled && currentStatus) {
                ProjectUtil.returnErrorMsg("isEnabled is already true for this custom field", HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }
            if (!isEnabled && !currentStatus) {
                ProjectUtil.returnErrorMsg("isEnabled is already false for this custom field", HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                return response;
            }

            if (isEnabled) {
                Map<String, Object> propertyMap = new HashMap<>();
                propertyMap.put(Constants.ID, customFieldData.get(Constants.ORGANIZATION_ID).asText());
                List<Map<String, Object>> orgList = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                        Constants.KEYSPACE_SUNBIRD, Constants.ORG_TABLE, propertyMap, Arrays.asList(Constants.CUSTOM_FIELDS_DATA), null);

                // Prepare data to store in organization
                String fieldIdFromData = customFieldId; // Using the customFieldId directly as it's more reliable

                // Calculate how many fields this custom field accounts for
                int fieldCount = 1; // Default for text fields

                // Check if it's a masterList type and get levels
                if (customFieldData.has(Constants.TYPE) && Constants.MASTER_LIST.equals(customFieldData.get(Constants.TYPE).asText())) {
                    fieldCount = customFieldData.get(Constants.LEVELS).asInt();
                }


                Map<String, Object> customFieldsData;

                if (CollectionUtils.isEmpty(orgList) || StringUtils.isBlank((String) orgList.get(0).get(Constants.CUSTOM_FIELDS_DATA))) {
                    customFieldsData = new HashMap<>();
                    List<String> customFieldIds = new ArrayList<>();
                    customFieldIds.add(fieldIdFromData);
                    customFieldsData.put(Constants.CUSTOM_FIELD_IDS, customFieldIds);
                    customFieldsData.put(Constants.CUSTOM_FIELDS_COUNT, fieldCount);
                } else {
                    // Update existing organization record
                    customFieldsData = objectMapper.readValue((String) orgList.get(0).get(Constants.CUSTOM_FIELDS_DATA), Map.class);

                    // If customFieldsData doesn't have proper structure, initialize it
                    if (!customFieldsData.containsKey(Constants.CUSTOM_FIELD_IDS)) {
                        customFieldsData.put(Constants.CUSTOM_FIELD_IDS, new ArrayList<String>());
                    }
                    if (!customFieldsData.containsKey(Constants.CUSTOM_FIELDS_COUNT)) {
                        customFieldsData.put(Constants.CUSTOM_FIELDS_COUNT, 0);
                    }
                    int currentCount = ((Number) customFieldsData.get(Constants.CUSTOM_FIELDS_COUNT)).intValue();

                    // Check if enabling this field would exceed the limit
                    if (currentCount + fieldCount > cbServerProperties.getCustomFieldMaxAllowedCount()) {
                        ProjectUtil.returnErrorMsg(
                                "Cannot enable this custom field. The maximum limit of " + cbServerProperties.getCustomFieldMaxLevel() + " enabled custom fields would be exceeded.",
                                HttpStatus.BAD_REQUEST, response, Constants.FAILED);
                        return response;
                    }

                    List<String> customFieldIds = (List<String>) customFieldsData.get(Constants.CUSTOM_FIELD_IDS);
                    // Add the field ID and update the count
                    if (CollectionUtils.isEmpty(customFieldIds) || !customFieldIds.contains(fieldIdFromData)) {
                        customFieldIds.add(fieldIdFromData);
                        customFieldsData.put(Constants.CUSTOM_FIELD_IDS, customFieldIds);
                        customFieldsData.put(Constants.CUSTOM_FIELDS_COUNT, currentCount + fieldCount);
                    }
                }

                Map<String, Object> orgUpdateData = new HashMap<>();
                orgUpdateData.put(Constants.ID, customFieldData.get(Constants.ORGANIZATION_ID).asText());
                orgUpdateData.put(Constants.CUSTOM_FIELDS_DATA, objectMapper.writeValueAsString(customFieldsData));
                cassandraOperation.updateRecord(Constants.KEYSPACE_SUNBIRD, Constants.ORG_TABLE, orgUpdateData);
            } else {
                Map<String, Object> propertyMap = new HashMap<>();
                propertyMap.put(Constants.ID, customFieldData.get(Constants.ORGANIZATION_ID).asText());
                List<Map<String, Object>> orgList = cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                        Constants.KEYSPACE_SUNBIRD, Constants.ORG_TABLE, propertyMap, Arrays.asList(Constants.CUSTOM_FIELDS_DATA), null);

                if (!CollectionUtils.isEmpty(orgList) && !StringUtils.isBlank((String) orgList.get(0).get(Constants.CUSTOM_FIELDS_DATA))) {

                    Map<String, Object> customFieldsData = objectMapper.readValue((String) orgList.get(0).get(Constants.CUSTOM_FIELDS_DATA), Map.class);

                    // Only proceed if customFieldsData has the expected structure
                    if (customFieldsData.containsKey(Constants.CUSTOM_FIELD_IDS) && customFieldsData.containsKey(Constants.CUSTOM_FIELDS_COUNT)) {
                        List<String> customFieldIds = (List<String>) customFieldsData.get(Constants.CUSTOM_FIELD_IDS);

                        // Remove the field ID if it exists
                        if (!CollectionUtils.isEmpty(customFieldIds) && customFieldIds.contains(customFieldId)) {
                            customFieldIds.remove(customFieldId);
                            customFieldsData.put(Constants.CUSTOM_FIELD_IDS, customFieldIds);

                            // Calculate field count using same logic as the enable case
                            int fieldCount = 1;
                            if (customFieldData.has(Constants.TYPE) && Constants.MASTER_LIST.equals(customFieldData.get(Constants.TYPE).asText())) {
                                fieldCount = customFieldData.get(Constants.LEVELS).asInt();
                            }

                            // Reduce the fields count
                            int currentCount = ((Number) customFieldsData.get(Constants.CUSTOM_FIELDS_COUNT)).intValue();
                            customFieldsData.put(Constants.CUSTOM_FIELDS_COUNT, Math.max(0, currentCount - fieldCount));

                            // Update the organization record
                            Map<String, Object> orgUpdateData = new HashMap<>();
                            orgUpdateData.put(Constants.ID, customFieldData.get(Constants.ORGANIZATION_ID).asText());
                            orgUpdateData.put(Constants.CUSTOM_FIELDS_DATA, objectMapper.writeValueAsString(customFieldsData));
                            cassandraOperation.updateRecord(Constants.KEYSPACE_SUNBIRD, Constants.ORG_TABLE, orgUpdateData);
                        }
                    }
                }
            }

            ObjectNode customFieldDataNode = (ObjectNode) customFieldData;
            customFieldDataNode.put(Constants.IS_ENABLED, isEnabled);
            customFieldDataNode.put(Constants.UPDATED_BY, userId);

            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);
            customFieldDataNode.put(Constants.UPDATED_ON, formattedCurrentTime);

            customField.setCustomFieldData(customFieldDataNode);
            customField.setUpdatedOn(currentTime);
            customFieldRepository.save(customField);

            Map<String, Object> customFieldMap = objectMapper.convertValue(customFieldDataNode, Map.class);
            customFieldMap.put(Constants.CUSTOM_FIELD_ID, customFieldId);

            esUtilService.updateDocument(
                    cbServerProperties.getCustomFieldEntity(),
                    customFieldId,
                    customFieldMap,
                    cbServerProperties.getCustomFieldElasticMappingJsonPath()
            );

            cacheService.putCache(Constants.CUSTOM_FIELD + customFieldId, objectMapper.valueToTree(customFieldMap));

            response.setResponseCode(HttpStatus.OK);
            response.getParams().setStatus(Constants.SUCCESS);
            response.setMessage(Constants.SUCCESS);
            return response;
        } catch (Exception e) {
            log.error("Failed to update custom field status: {}", e.getMessage(), e);
            ProjectUtil.returnErrorMsg("Failed to update custom field status: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, response, Constants.FAILED);
            return response;
        }
    }
}
