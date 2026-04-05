package com.example.marketing.page.service;

import com.example.marketing.exception.BusinessException;
import com.example.marketing.exception.StatusErrorResponse;
import com.example.marketing.page.util.CursorPager;
import com.example.marketing.infrastructure.util.Provider;
import com.example.marketing.oauth.entity.OAuthAccountEntity;
import com.example.marketing.oauth.repository.OAuthAccountRepository;
import com.example.marketing.page.dto.PageDto;
import com.example.marketing.page.dto.PagePostDto;
import com.example.marketing.page.entity.PageEntity;
import com.example.marketing.page.entity.PagePostEntity;
import com.example.marketing.page.mapper.PageMapper;
import com.example.marketing.page.mapper.PagePostMapper;
import com.example.marketing.page.repository.PagePostRepository;
import com.example.marketing.page.repository.PageRepository;
import com.example.marketing.page.strategy.PageStrategy;
import com.example.marketing.page.strategy.PageStrategyRegistry;
import com.example.marketing.infrastructure.strategy.PlatformClientRegistry;
import com.example.marketing.user.entity.UserEntity;
import com.example.marketing.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PageService {

    private static final Provider PLATFORM = Provider.META;

    private final PageRepository pageRepository;
    private final PagePostRepository pagePostRepository;
    private final PageMapper pageMapper;
    private final PagePostMapper pagePostMapper;

    private final UserRepository userRepository;
    private final OAuthAccountRepository oAuthAccountRepository;

    private final PlatformClientRegistry clients;
    private final PageStrategyRegistry pageStrategies;

    public List<PageDto> getAllPages() {
        return pageRepository.findAll().stream().map(pageMapper::convertToBaseDto).toList();
    }

    @Transactional
    public List<PageDto> getAllPagesFromMeta(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        String userToken = getAccessToken(user, PLATFORM.name());

        var client = clients.of(PLATFORM);
        PageStrategy strategy = pageStrategies.of(PLATFORM);

        List<Map<String,Object>> rows = CursorPager.fetchAll(
                client,
                strategy.listPagesPath(),
                strategy.listPagesQuery(),
                userToken
        );

        Map<String, PageEntity> existing = pageRepository.findByUser(user).stream()
                .collect(Collectors.toMap(PageEntity::getPageId, Function.identity()));

        List<PageEntity> toSave = new ArrayList<>(rows.size());

        for (var row : rows) {
            String pageId = Objects.toString(row.get("id"), null);
            if (pageId == null) continue;

            PageEntity e = existing.getOrDefault(pageId, new PageEntity());
            e.setPageId(pageId);
            e.setName(Objects.toString(row.get("name"), null));
            e.setAccessToken(Objects.toString(row.get("access_token"), null)); // page token
            e.setUser(user);

            toSave.add(e);
        }

        pageRepository.saveAll(toSave);
        return toSave.stream().map(pageMapper::convertToBaseDto).toList();
    }

    @Transactional
    public List<PagePostDto> getAllPagePostsFromMeta(Long userId, String pageName) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found with id: " + userId));

        PageEntity page = pageRepository.findByNameAndUser(pageName, user)
                .orElseThrow(() -> BusinessException.notFound("Page '" + pageName + "' not found for current user"));

        String pageToken = page.getAccessToken();
        if (pageToken == null || pageToken.isBlank()) {
            throw BusinessException.badRequest("Page '" + pageName
                    + "' has no access token stored. Please re-sync pages from Meta first.");
        }

        var client = clients.of(PLATFORM);
        PageStrategy strategy = pageStrategies.of(PLATFORM);

        List<Map<String,Object>> rows = CursorPager.fetchAll(
                client,
                strategy.listPostsPath(page.getPageId()),
                strategy.listPostsQuery(),
                pageToken
        );

        Map<String, PagePostEntity> existing = pagePostRepository.findByPage(page).stream()
                .collect(Collectors.toMap(PagePostEntity::getPostId, Function.identity()));

        List<PagePostEntity> toSave = new ArrayList<>(rows.size());

        for (var row : rows) {
            String postId = Objects.toString(row.get("id"), null);
            if (postId == null) continue;

            PagePostEntity e = existing.getOrDefault(postId, new PagePostEntity());
            e.setPostId(postId);
            e.setPermalinkUrl(Objects.toString(row.get("permalink_url"), null));
            e.setPage(page);
            toSave.add(e);
        }

        pagePostRepository.saveAll(toSave);
        return toSave.stream().map(pagePostMapper::convertToBaseDto).toList();
    }

    private String getAccessToken(UserEntity user, String provider) {
        return oAuthAccountRepository.findByUserAndProvider(user, provider)
                .map(OAuthAccountEntity::getAccessToken)
                .orElseThrow(() -> BusinessException.of(StatusErrorResponse.SERVICE_UNAVAILABLE,
                        "No " + provider + " OAuth access token found for user " + user.getId()
                                + ". Please connect your " + provider + " account first."));
    }
}
