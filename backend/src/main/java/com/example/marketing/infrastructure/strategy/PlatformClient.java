package com.example.marketing.infrastructure.strategy;

import com.example.marketing.infrastructure.util.Provider;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import java.util.Map;


public interface PlatformClient {
    Provider provider();

    ResponseEntity<Map> postForm(String path, MultiValueMap<String,String> form, String token);

    ResponseEntity<Map> postMultipart(String path, MultiValueMap<String,Object> formData, String token);

    ResponseEntity<Map> get(String path, Map<String,String> queryParams, String token);

    ResponseEntity<Map> delete(String objectId, Map<String,String> payload, String token);

}
