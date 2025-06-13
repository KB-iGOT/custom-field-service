package com.igot.cb.customFields.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.customFields.service.CustomFieldsService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/customFields/v1")
public class CustomFieldsController {
    @Autowired
    private CustomFieldsService customFieldsService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse> createCustomFields(@RequestBody JsonNode customFieldsData,
                                                          @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.createCustomFields(customFieldsData, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/read/{customFieldId}")
    public ResponseEntity<ApiResponse> readCustomField(
            @PathVariable String customFieldId,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.readCustomField(customFieldId, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PutMapping("/update/{customFieldId}")
    public ResponseEntity<ApiResponse> updateCustomField(
            @PathVariable String customFieldId,
            @RequestBody JsonNode customFieldData,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.updateCustomField(customFieldId, customFieldData, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @DeleteMapping("/delete/{customFieldId}")
    public ResponseEntity<ApiResponse> deleteCustomField(
            @PathVariable String customFieldId,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.deleteCustomField(customFieldId, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse> searchCustomFields(
            @RequestBody SearchCriteria searchCriteria) {
        ApiResponse response = customFieldsService.searchCustomFields(searchCriteria);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/masterList/create")
    public ResponseEntity<ApiResponse> uploadCustomFieldHierarchy(
            @RequestParam("file") MultipartFile multipartFile,
            @RequestParam("metadata") String customFieldsMasterDataJson,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.uploadMasterListCustomField(multipartFile, customFieldsMasterDataJson, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PutMapping(value = "/masterList/update")
    public ResponseEntity<ApiResponse> updateMasterListCustomField(
            @RequestParam("file") MultipartFile file,
            @RequestParam("metadata") String customFieldsMasterDataJson,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.updateMasterListCustomField(file, customFieldsMasterDataJson, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/status/update")
    public ResponseEntity<ApiResponse> updateCustomFieldStatus(
            @RequestBody JsonNode updateCustomFieldStatusData,
            @RequestHeader(Constants.X_AUTH_TOKEN) String token) {
        ApiResponse response = customFieldsService.updateCustomFieldStatus(updateCustomFieldStatusData, token);
        return new ResponseEntity<>(response, response.getResponseCode());
    }
}
