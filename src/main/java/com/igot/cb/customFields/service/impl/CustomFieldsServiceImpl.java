package com.igot.cb.customFields.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.customFields.entity.CustomFieldEntity;
import com.igot.cb.customFields.repository.CustomFieldRepository;
import com.igot.cb.customFields.service.CustomFieldsService;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.pores.util.PayloadValidation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
        ApiResponse response = new ApiResponse("customField.create");

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

            // Create a mutable copy of the JSON data as ObjectNode
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            String formattedCurrentTime = getFormattedCurrentTime(currentTime);
            ObjectNode customFieldsDataObjectNode = (ObjectNode) customFieldsData;
            customFieldsDataObjectNode.put(Constants.CREATED_BY, userId);
            customFieldsDataObjectNode.put(Constants.CREATED_ON, formattedCurrentTime);
            customFieldsDataObjectNode.put(Constants.UPDATED_ON, formattedCurrentTime);
            customFieldsDataObjectNode.put(Constants.IS_ACTIVE, true);
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
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.getParams().setErrMsg("FAILED_TO_CREATE_CUSTOM_FIELD");
            response.setMessage(Constants.FAILED);
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
}
