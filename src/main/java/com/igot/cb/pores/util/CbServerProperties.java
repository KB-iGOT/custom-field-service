package com.igot.cb.pores.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class CbServerProperties {

  @Value("${search.result.redis.ttl}")
  private long searchResultRedisTtl;

  @Value("${customField.entity}")
  private String customFieldEntity;

  @Value("${customField.elastic.mapping.json.path}")
  private String customFieldElasticMappingJsonPath;

  @Value("${customField.Validation.File.path}")
  private String customFieldValidationFilePath;

  @Value("${custom.field.max.level}")
  private int customFieldMaxLevel;

  @Value("${customField.list.validation.file.path}")
  private String customFieldListValidationFilePath;

  @Value("${customField.upload.allowedExtensions}")
  private String allowedExtensions;

  @Value("${customField.upload.allowedContentTypes}")
  private String allowedContentTypes;

  @Value("${customField.list.update.validation.file.path}")
  private String customFieldListUpdateValidationFilePath;

  @Value("${customField.status.update.validation.file.path}")
  private String customFieldStatusUpdateValidationFilePath;

}
