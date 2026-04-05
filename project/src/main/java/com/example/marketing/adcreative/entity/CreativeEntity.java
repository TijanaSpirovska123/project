package com.example.marketing.adcreative.entity;

import com.example.marketing.infrastructure.entity.BasePlatformEntity;
import com.example.marketing.page.entity.PagePostEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "creatives")
@EqualsAndHashCode(callSuper = true)
public class CreativeEntity extends BasePlatformEntity {

    @Column(name = "link_url", columnDefinition = "TEXT")
    private String linkUrl;

    // MODE 1 (optional)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_post_id")
    private PagePostEntity pagePost;

    // MODE 2 (optional)
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "asset_id")
//    private AdAssetEntity asset;

    // Needed for MODE 2 object_story_spec
    @Column(name = "page_id", length = 100)
    private String pageId;

    @Column(name="image_hash", length=255)
    private String imageHash;
}
