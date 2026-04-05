package com.example.marketing.infrastructure.strategy.meta;

import com.example.marketing.infrastructure.strategy.PlatformClient;
import com.example.marketing.infrastructure.util.Provider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class MetaPlatformClient implements PlatformClient {

    private final RestTemplate restTemplate;

    @Value("${meta.graph.base:https://graph.facebook.com}")
    private String base;

    @Value("${meta.graph.version:v21.0}")
    private String version;

    public MetaPlatformClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public Provider provider() {
        return Provider.META;
    }

    private String baseUrl() {
        return base + "/" + version;
    }

    private String buildUrl(String path) {
        return baseUrl() + "/" + path;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();

        if (token == null || token.isBlank()) {
            return headers;
        }

        String t = token.trim();
        if (t.toLowerCase().startsWith("bearer ")) {
            t = t.substring("bearer ".length()).trim();
        }

        headers.setBearerAuth(t);
        return headers;
    }

    @Override
    public ResponseEntity<Map> get(String path, Map<String, String> query, String token) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(buildUrl(path));
        if (query != null) query.forEach(builder::queryParam);

        HttpEntity<Void> req = new HttpEntity<>(authHeaders(token));
        // Use build(false) to avoid double-encoding pre-encoded query parameters (e.g., time_range JSON)
        return restTemplate.exchange(builder.build(false).toUri(), HttpMethod.GET, req, Map.class);
    }

    @Override
    public ResponseEntity<Map> postForm(String path, MultiValueMap<String, String> form, String token) {
        if (form == null) form = new LinkedMultiValueMap<>();

        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(form, headers);
        return restTemplate.exchange(buildUrl(path), HttpMethod.POST, req, Map.class);
    }

    @Override
    public ResponseEntity<Map> postMultipart(String path, MultiValueMap<String, Object> formData, String token) {
        if (formData == null) formData = new LinkedMultiValueMap<>();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        boolean hasAccessTokenField = formData.containsKey("access_token");
        if (!hasAccessTokenField) {
            headers.setBearerAuth(token);
        }

        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(formData, headers);
        return restTemplate.exchange(buildUrl(path), HttpMethod.POST, req, Map.class);
    }




    @Override
    public ResponseEntity<Map> delete(String objectId, Map<String, String> payload, String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("status", "DELETED");
        if (payload != null) payload.forEach(form::add);
        return postForm(objectId, form, token);
    }
}
