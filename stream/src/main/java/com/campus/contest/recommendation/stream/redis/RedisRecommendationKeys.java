package com.campus.contest.recommendation.stream.redis;

import java.time.Instant;

public final class RedisRecommendationKeys {

    public static final int DEDUP_TTL_DAYS = 8;
    public static final int PREFERENCE_TTL_DAYS = 8;

    private RedisRecommendationKeys() {
    }

    public static String dedup(String eventId) {
        return "rec:flink:dedup:" + eventId;
    }

    public static String userCategory(String userId, String category) {
        return "rec:user:" + userId + ":cat:" + category;
    }

    /**
     * The Redis hash field is the UTC epoch second at the start of the 5-minute
     * window. Backend recommendation readers use the same encoding.
     */
    public static String bucketField(long windowStartEpochSecond) {
        return Long.toString(windowStartEpochSecond);
    }

    public static String bucketField(Instant windowStart) {
        return bucketField(windowStart.getEpochSecond());
    }
}
