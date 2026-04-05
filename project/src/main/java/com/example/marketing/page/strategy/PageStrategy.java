package com.example.marketing.page.strategy;

import com.example.marketing.infrastructure.util.Provider;

import java.util.Map;

public interface PageStrategy {
    Provider platform();

    // /me/accounts
    String listPagesPath();                      // "me/accounts"
    Map<String,String> listPagesQuery();         // fields=id,name,access_token

    // /{pageId}/posts
    String listPostsPath(String pageId);         // pageId + "/posts"
    Map<String,String> listPostsQuery();         // fields=id,permalink_url
}
