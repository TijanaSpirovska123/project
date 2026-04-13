package com.example.marketing.asset.entity;

public enum VideoVariantType {
    ORIGINAL(0, 0),
    META_VIDEO_SQUARE(1080, 1080),
    META_VIDEO_VERTICAL(1080, 1350),
    META_VIDEO_LANDSCAPE(1200, 628),
    META_VIDEO_STORY(1080, 1920);

    private final int width;
    private final int height;

    VideoVariantType(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public String getDisplayName() {
        return switch (this) {
            case ORIGINAL -> "Original";
            case META_VIDEO_SQUARE -> "1:1 Square (1080×1080)";
            case META_VIDEO_VERTICAL -> "4:5 Vertical (1080×1350)";
            case META_VIDEO_LANDSCAPE -> "1.91:1 Landscape (1200×628)";
            case META_VIDEO_STORY -> "9:16 Story/Reels (1080×1920)";
        };
    }
}
