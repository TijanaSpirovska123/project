package com.example.marketing.page.api;


import com.example.marketing.infrastructure.api.BaseController;
import com.example.marketing.page.dto.PageDto;
import com.example.marketing.page.dto.PagePostDto;
import com.example.marketing.page.service.PageService;
import com.example.marketing.infrastructure.Endpoints;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Positive;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(Endpoints.BASE_PAGE)
public class PageController extends BaseController {

    private final PageService pageService;

    @GetMapping
    public List<PageDto> getAllPages() {
        return pageService.getAllPages();
    }

    @GetMapping("/meta")
    public ResponseEntity<List<PageDto>> getAllPagesFromMeta(Authentication auth) {
        Long userId = extractUserId(auth);
        List<PageDto> pages = pageService.getAllPagesFromMeta(userId);
        return ResponseEntity.ok(pages);
    }

    @GetMapping("/meta/{pageName}/posts")
    public ResponseEntity<List<PagePostDto>> getAllPagePostsFromMeta(Authentication auth,
            @PathVariable String pageName) {
        Long userId = extractUserId(auth);
        List<PagePostDto> posts = pageService.getAllPagePostsFromMeta(userId, pageName);
        return ResponseEntity.ok(posts);
    }

}
