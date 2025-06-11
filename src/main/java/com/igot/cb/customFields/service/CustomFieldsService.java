package com.igot.cb.customFields.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public interface CustomFieldsService {

    ApiResponse createCustomFields(JsonNode customFieldsData, String token);

    ApiResponse readCustomField(String customFieldId, String token);

    ApiResponse updateCustomField(String customFieldId, JsonNode customFieldData, String token);

    ApiResponse deleteCustomField(String customFieldId, String token);

    ApiResponse searchCustomFields(SearchCriteria searchCriteria);

    ApiResponse uploadMasterListCustomField(MultipartFile multipartFile, String customFieldsMasterDataJson, String token);
}
