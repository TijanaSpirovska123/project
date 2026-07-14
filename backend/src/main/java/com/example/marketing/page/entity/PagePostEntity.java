package com.example.marketing.page.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "page_posts")
public class PagePostEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private String postId;

    @Column(name = "permalink_url", nullable = false)
    private String permalinkUrl;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private PageEntity page;
}
