package com.example.marketing.infrastructure.strategy;

import com.example.marketing.infrastructure.util.Provider;
import org.springframework.util.MultiValueMap;

import java.util.Map;

public interface PlatformStrategy<D> {
    Provider platform();
    String createPath(String accountId);
    String listPath(String accountId);
    String updatePath(String platformObjectId);

    Map<String,String> baseListQuery();
    MultiValueMap<String,String> toCreateForm(D dto);
    MultiValueMap<String,String> toUpdateForm(D dto, boolean isDelete);

    D mapGetRow(Map<String,Object> row, Long userId, String adAccountId);
}
