package com.example.marketing.ad.entity;

import com.example.marketing.adset.entity.AdSetEntity;
import com.example.marketing.infrastructure.entity.BasePlatformEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "ad",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "platform", "ad_account_id", "external_id"}))
@Data
@EqualsAndHashCode(callSuper = true)
public class AdEntity extends BasePlatformEntity {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private Map<String, Object> rawData;

    @Column(name = "ad_set_external_id", length = 64)
    private String adSetExternalId;

    @Column(name = "ad_set_name")
    private String adSetName;

    @Column(name = "creative_id", length = 64)
    private String creativeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ad_set_id")
    private AdSetEntity adSet;
}
