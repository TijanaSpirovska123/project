package com.example.marketing.adset.entity;

import com.example.marketing.ad.entity.AdEntity;
import com.example.marketing.campaign.entity.CampaignEntity;
import com.example.marketing.infrastructure.entity.BasePlatformEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "ad_set",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "platform", "ad_account_id", "external_id"}))
@Data
@EqualsAndHashCode(callSuper = true)
public class AdSetEntity extends BasePlatformEntity {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private Map<String, Object> rawData;

    @Column(name = "campaign_external_id", length = 64)
    private String campaignExternalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private CampaignEntity campaign;

    @OneToMany(mappedBy = "adSet", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<AdEntity> ads = new ArrayList<>();
}
