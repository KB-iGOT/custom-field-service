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
import lombok.extern.slf4j.Slf4j;
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
            customFieldsDataObjectNode.put(Constants.IS_ENABLED, true);
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
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                response.setResponseCode(HttpStatus.UNAUTHORIZED);
                return response;
            }

            // Check if field exists and is active
            Optional<CustomFieldEntity> customFieldOpt = customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId);
            if (customFieldOpt.isEmpty()) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg("Custom field not found or is inactive with ID: " + customFieldId);
                response.setResponseCode(HttpStatus.NOT_FOUND);
                return response;
            }

            CustomFieldEntity customField = customFieldOpt.get();

            // Update entity data
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);

            ObjectNode customFieldsDataObjectNode = (ObjectNode) customFieldsData;
            customFieldsDataObjectNode.put(Constants.ORGANISATION_ID, customFieldOpt.get().getCustomFieldData().get(Constants.ORGANISATION_ID).asText());
            customFieldsDataObjectNode.put(Constants.UPDATED_BY, userId); // Add the updated by field
            customFieldsDataObjectNode.put(Constants.UPDATED_ON, formattedCurrentTime);
            customFieldsDataObjectNode.put(Constants.IS_MANDATORY, customFieldsData.get(Constants.IS_MANDATORY).asBoolean(false));
            customFieldsDataObjectNode.put(Constants.IS_ACTIVE, true);

            // Keep original creation info
            JsonNode originalData = customField.getCustomFieldData();
            if (originalData.has(Constants.CREATED_BY)) {
                customFieldsDataObjectNode.put(Constants.CREATED_BY, originalData.get(Constants.CREATED_BY).asText());
            }
            if (originalData.has(Constants.CREATED_ON)) {
                customFieldsDataObjectNode.put(Constants.CREATED_ON, originalData.get(Constants.CREATED_ON).asText());
            }

            // Rest of your existing code...
            customField.setCustomFieldData(customFieldsDataObjectNode);
            customField.setIsMandatory(customFieldsData.get(Constants.IS_MANDATORY).asBoolean(false));
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
            log.error("Failed to update custom field: {}", e.getMessage(), e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setErrMsg("FAILED_TO_UPDATE_CUSTOM_FIELD");
            response.setMessage(Constants.FAILED);
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
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg(Constants.INVALID_AUTH_TOKEN);
                response.setResponseCode(HttpStatus.UNAUTHORIZED);
                return response;
            }

            // Check if field exists
            Optional<CustomFieldEntity> customFieldOpt = customFieldRepository.findByCustomFiledIdAndIsActiveTrue(customFieldId);
            if (customFieldOpt.isEmpty()) {
                response.getParams().setStatus(Constants.FAILED);
                response.getParams().setErrMsg("Custom field not found with ID: " + customFieldId);
                response.setResponseCode(HttpStatus.NOT_FOUND);
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
            resultMap.put("customFieldId", customFieldId);
            resultMap.put("status", "deleted");
            response.setResult(resultMap);

        } catch (Exception e) {
            log.error("Failed to delete custom field: {}", e.getMessage(), e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setErrMsg("FAILED_TO_DELETE_CUSTOM_FIELD");
            response.setMessage(Constants.FAILED);
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
            response.getResult().put("searchResults", searchResult);

        } catch (Exception e) {
            log.error("Failed to search custom fields: {}", e.getMessage(), e);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setErrMsg("FAILED_TO_SEARCH_CUSTOM_FIELDS");
            response.setMessage(Constants.FAILED);
        }

        return response;
    }

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
        if (fileName == null ||
                !(fileName.endsWith(Constants.XLSX) || fileName.endsWith(Constants.XLS)) ||
                !(contentType != null && (contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        || contentType.equals("application/vnd.ms-excel")))) {
            ProjectUtil.returnErrorMsg(Constants.ONLY_EXCEL_FILES_ALLOWED, HttpStatus.BAD_REQUEST, response, Constants.FAILED);
            return response;
        }

        Object customFieldDataObj = customFieldsData.get(Constants.CUSTOM_FIELD_DATA);
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

        ArrayNode customFieldsListData = parseHierarchy(headers, dataRows, levels);

        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String formattedCurrentTime = getFormattedCurrentTime(currentTime);
        customFieldsData.put(Constants.CREATED_BY, userId);
        customFieldsData.put(Constants.CREATED_ON, formattedCurrentTime);
        customFieldsData.put(Constants.UPDATED_ON, formattedCurrentTime);
        customFieldsData.put(Constants.CUSTOM_FIELD_DATA, customFieldsListData);
        customFieldsData.put(Constants.IS_ACTIVE, true);

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

    private ArrayNode parseHierarchy(String[] headers, List<Row> dataRows, int levels) {
        ArrayNode root = objectMapper.createArrayNode();
        for (Row row : dataRows) {
            ArrayNode currentArray = root;
            String parentFieldName = null;
            String parentFieldValue = null;
            for (int j = 0; j < levels; j++) {
                Cell valueCell = row.getCell(j);
                String value = (valueCell != null) ? valueCell.toString().trim() : null;
                if (value == null || value.isEmpty()) break;
                String fieldName = headers[j];

                ObjectNode foundNode = null;
                for (JsonNode node : currentArray) {
                    if (node.get("fieldName").asText().equals(fieldName) &&
                            node.get("fieldValue").asText().equals(value)) {
                        foundNode = (ObjectNode) node;
                        break;
                    }
                }
                if (foundNode == null) {
                    ObjectNode newNode = objectMapper.createObjectNode();
                    newNode.put("fieldName", fieldName);
                    newNode.put("fieldValue", value);
                    newNode.put("fieldAttribute", fieldName);
                    if (parentFieldName != null && parentFieldValue != null) {
                        newNode.put("parentFieldName", parentFieldName);
                        newNode.put("parentFieldValue", parentFieldValue);
                    }
                    ArrayNode childArray = objectMapper.createArrayNode();
                    newNode.set("fieldValues", childArray);
                    currentArray.add(newNode);
                    foundNode = newNode;
                }
                parentFieldName = fieldName;
                parentFieldValue = value;
                currentArray = (ArrayNode) foundNode.get("fieldValues");
            }
        }
        return root;
    }
}
