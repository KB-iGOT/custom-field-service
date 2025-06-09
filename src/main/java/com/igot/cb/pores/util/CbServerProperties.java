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

}
