package com.example.marketing.campaign.entity;

import com.example.marketing.adset.entity.AdSetEntity;
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
@Table(name = "campaign",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "platform", "ad_account_id", "external_id"}))
@Data
@EqualsAndHashCode(callSuper = true)
public class CampaignEntity extends BasePlatformEntity {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private Map<String, Object> rawData;

    @OneToMany(mappedBy = "campaign", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<AdSetEntity> adSets = new ArrayList<>();
}
