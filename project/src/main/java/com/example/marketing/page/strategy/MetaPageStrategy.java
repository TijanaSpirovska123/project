package com.example.marketing.page.strategy;

import com.example.marketing.infrastructure.util.Provider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MetaPageStrategy implements PageStrategy {

    @Override public Provider platform() { return Provider.META; }

    @Override public String listPagesPath() { return "me/accounts"; }

    @Override public Map<String, String> listPagesQuery() {
        // id, name, access_token – same fields you requested
        return Map.of("fields", "id,name,access_token,picture{url}");
    }

    @Override public String listPostsPath(String pageId) { return pageId + "/posts"; }

    @Override public Map<String, String> listPostsQuery() {
        return Map.of("fields", "id,permalink_url");
    }
}
