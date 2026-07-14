package com.example.marketing.page.util;

import com.example.marketing.infrastructure.strategy.PlatformClient;
import org.springframework.http.ResponseEntity;

import java.util.*;

public final class CursorPager {

    private CursorPager() {}

    public static List<Map<String,Object>> fetchAll(
            PlatformClient client,
            String path,
            Map<String,String> baseQuery,
            String token
    ) {
        List<Map<String,Object>> out = new ArrayList<>();
        String after = null;

        do {
            Map<String,String> q = new HashMap<>();
            if (baseQuery != null) q.putAll(baseQuery);
            if (after != null) q.put("after", after);

            ResponseEntity<Map> resp = client.get(path, q, token);
            Map body = resp.getBody();
            if (body == null) break;

            @SuppressWarnings("unchecked")
            List<Map<String,Object>> data = (List<Map<String,Object>>) body.get("data");
            if (data != null) out.addAll(data);

            @SuppressWarnings("unchecked")
            Map<String,Object> paging = (Map<String,Object>) body.get("paging");
            @SuppressWarnings("unchecked")
            Map<String,Object> cursors = paging != null ? (Map<String,Object>) paging.get("cursors") : null;

            after = cursors != null ? Objects.toString(cursors.get("after"), null) : null;

        } while (after != null);

        return out;
    }
}
