package com.campus.contest.recommendation.stream.window;

import java.io.Serializable;
import java.util.Objects;

public class UserCategoryKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private String userId;
    private String category;

    public UserCategoryKey() {
    }

    public UserCategoryKey(String userId, String category) {
        this.userId = userId;
        this.category = category;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserCategoryKey)) {
            return false;
        }
        UserCategoryKey that = (UserCategoryKey) other;
        return Objects.equals(userId, that.userId)
                && Objects.equals(category, that.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, category);
    }
}
