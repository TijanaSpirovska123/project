package com.example.marketing.infrastructure.entity;



import java.time.LocalDateTime;

public interface SchedulableEntity {
    Long getId();
    String getPlatformId(); // e.g., facebookCampaignId, facebookAdSetId
    LocalDateTime getScheduleTime();
    String getPlatform();


}