package com.example.marketing.infrastructure.service;

import com.example.marketing.infrastructure.service.platformserviceimpl.AbstractPlatformService.SyncResult;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.user.entity.UserEntity;

import java.util.List;

public interface PlatformService<E, D> {
    void createOnPlatform(E entity);
    void updateOnPlatform(E entity);
    void deleteOnPlatform(E entity);

    List<D> listFromPlatform(UserEntity user, Provider platform, String adAccountId);
    SyncResult syncFromPlatform(UserEntity user, Provider platform, String adAccountId);
}
