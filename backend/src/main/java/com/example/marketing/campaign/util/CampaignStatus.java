package com.example.marketing.campaign.util;

public enum CampaignStatus {
    PENDING,      // waiting to be sent (scheduler)
    SENDING,      // in-flight
    SENT,         // created on FB (has externalCampaignId)
    FAILED,       // our send failed
    CANCELED      // user canceled before sending
}