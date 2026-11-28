package com.campus.contest.recommendation.stream.window;

import java.io.Serializable;

public class PreferenceWindowResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String category;
    private final long windowStartEpochSecond;
    private final long windowEndEpochSecond;
    private final long viewCount;

    public PreferenceWindowResult(
            String userId,
            String category,
            long windowStartEpochSecond,
            long windowEndEpochSecond,
            long viewCount
    ) {
        this.userId = userId;
        this.category = category;
        this.windowStartEpochSecond = windowStartEpochSecond;
        this.windowEndEpochSecond = windowEndEpochSecond;
        this.viewCount = viewCount;
    }

    public String getUserId() {
        return userId;
    }

    public String getCategory() {
        return category;
    }

    public long getWindowStartEpochSecond() {
        return windowStartEpochSecond;
    }

    public long getWindowEndEpochSecond() {
        return windowEndEpochSecond;
    }

    public long getViewCount() {
        return viewCount;
    }
}
