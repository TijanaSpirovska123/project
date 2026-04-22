package com.example.marketing.page.entity;


import com.example.marketing.user.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pages")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class PageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_id", nullable = false)
    private String pageId;

    @Column(nullable = false)
    private String name;

    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "picture_url")
    private String pictureUrl;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
}
