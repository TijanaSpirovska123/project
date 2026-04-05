package com.example.marketing.infrastructure;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Endpoints {
    public static final String BASE = "/api/";
    public static final String CAMPAIGN = "campaigns";
    public static final String AD_SET = "ad-sets";
    public static final String PAGE = "page";
    public static final String BASE_CAMPAIGN = BASE + CAMPAIGN;
    public static final String BASE_AD_SET = BASE + AD_SET;
    public static final String BASE_PAGE = BASE + PAGE;
    public static final String BASE_AD = BASE + "ads";


    public static final String GRAPH_API_BASE = "https://graph.facebook.com/v23.0/";
    public static final String BASE_ASSET = BASE + "assets";
}
