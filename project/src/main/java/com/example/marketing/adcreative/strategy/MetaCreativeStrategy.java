package com.example.marketing.adcreative.strategy;

import com.example.marketing.adcreative.dto.CreativeDto;
import com.example.marketing.infrastructure.util.Provider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.marketing.infrastructure.util.MetaForm.*;
import static com.example.marketing.infrastructure.util.StrategyMappers.s;

@Component
public class MetaCreativeStrategy implements CreativeStrategy {

    private static final String FIELDS =
            "id,name,object_story_id,object_story_spec,object_type,image_hash,image_url,video_id,body,title,status,thumbnail_url,url_tags";

    // Keep ONE ObjectMapper instance (cheap + correct)
    private static final ObjectMapper OM = new ObjectMapper();

    @Override public Provider platform(){ return Provider.META; }

    @Override
    public String createPath(String accountId) {
        return normalizeAct(accountId) + "/adcreatives";
    }

    @Override
    public String listPath(String accountId) {
        return normalizeAct(accountId) + "/adcreatives";
    }

    @Override
    public String updatePath(String platformObjectId) {
        return platformObjectId;
    }

    @Override
    public Map<String, String> baseListQuery() {
        return Map.of("fields", FIELDS);
    }

    @Override
    public MultiValueMap<String, String> toCreateForm(CreativeDto dto) {
        var p = newForm();
        add(p, "name", required(dto.getName(), "name"));

        // MODE 1: existing post
        if (isNotBlank(dto.getObjectStoryId())) {
            add(p, "object_story_id", dto.getObjectStoryId());
            if (isNotBlank(dto.getUrlTags())) add(p, "url_tags", dto.getUrlTags());
            return p;
        }

        // MODE 2: uploaded image hash + link creative
        if (isNotBlank(dto.getPageId()) && isNotBlank(dto.getObjectUrl()) && isNotBlank(dto.getImageHash())) {
            String specJson = buildLinkDataSpecJson(
                    dto.getPageId(),
                    dto.getObjectUrl(),
                    dto.getMessage(),
                    dto.getHeadline(),
                    dto.getImageHash()
            );
            add(p, "object_story_spec", specJson);
            if (isNotBlank(dto.getUrlTags())) add(p, "url_tags", dto.getUrlTags());
            return p;
        }

        // MODE 3: video creative — video_id based
        if (isNotBlank(dto.getPageId()) && isNotBlank(dto.getVideoId())) {
            String specJson = buildVideoDataSpecJson(
                    dto.getPageId(),
                    dto.getVideoId(),
                    dto.getObjectUrl(),
                    dto.getName()
            );
            add(p, "object_story_spec", specJson);
            if (isNotBlank(dto.getUrlTags())) add(p, "url_tags", dto.getUrlTags());
            return p;
        }

        throw new IllegalArgumentException(
                "Invalid creative input. Provide either objectStoryId OR (pageId + objectUrl + imageHash) OR (pageId + videoId)."
        );
    }

    @Override
    public MultiValueMap<String, String> toUpdateForm(CreativeDto dto, boolean isDelete) {
        var p = newForm();

        if (isDelete) {
            add(p, "status", "DELETED");
            return p;
        }

        if (isNotBlank(dto.getName())) add(p, "name", dto.getName());
        return p;
    }

    @Override
    public CreativeDto mapGetRow(Map<String, Object> row, Long userId, String adAccountId) {
        var dto = new CreativeDto();

        dto.setExternalId(s(row, "id"));          // ✅ FIXED (was creativeId)
        dto.setName(s(row, "name"));
        dto.setObjectStoryId(s(row, "object_story_id"));
        dto.setObjectType(s(row, "object_type"));
        dto.setImageHash(s(row, "image_hash"));

        // Meta returns "object_url" sometimes; your fields list includes image_url etc.
        // Keep your mapping as you had it.
        dto.setObjectUrl(s(row, "object_url"));

        dto.setAdAccountId(adAccountId);          // ✅ only if BasePlatformDto has it (it does)
        dto.setUserId(userId);
        dto.setPlatform(Provider.META.name());
        return dto;
    }


    private static String buildLinkDataSpecJson(
            String pageId,
            String linkUrl,
            String message,
            String headline,
            String imageHash
    ) {
        try {
            Map<String, Object> linkData = new LinkedHashMap<>();
            linkData.put("link", linkUrl);
            if (isNotBlank(message)) linkData.put("message", message);
            if (isNotBlank(headline)) linkData.put("name", headline);
            if (isNotBlank(imageHash)) linkData.put("image_hash", imageHash);

            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("page_id", pageId);
            spec.put("link_data", linkData);

            return OM.writeValueAsString(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to build object_story_spec JSON", e);
        }
    }

    private static String buildVideoDataSpecJson(
            String pageId,
            String videoId,
            String destinationUrl,
            String creativeName
    ) {
        try {
            Map<String, Object> videoData = new LinkedHashMap<>();
            videoData.put("video_id", videoId);
            videoData.put("call_to_action", Map.of(
                    "type", "LEARN_MORE",
                    "value", Map.of("link", destinationUrl != null ? destinationUrl : "")
            ));
            if (isNotBlank(creativeName)) videoData.put("title", creativeName);

            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("page_id", pageId);
            spec.put("video_data", videoData);

            return OM.writeValueAsString(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to build video object_story_spec JSON", e);
        }
    }

    private static boolean isNotBlank(String v) {
        return v != null && !v.isBlank();
    }

    private static String required(String v, String field) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing required field: " + field);
        return v;
    }
}
