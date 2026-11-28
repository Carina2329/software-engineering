package com.campus.contest.recommendation.stream.config;

import java.io.Serializable;
import java.util.Objects;

public final class RedisConnectionSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String host;
    private final int port;
    private final String password;
    private final int database;

    public RedisConnectionSettings(String host, int port, String password, int database) {
        this.host = requireText(host, "redis host");
        this.port = requireValidPort(port);
        this.password = password == null || password.isBlank() ? null : password;
        if (database < 0) {
            throw new IllegalArgumentException("redis database must be >= 0");
        }
        this.database = database;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPassword() {
        return password;
    }

    public int getDatabase() {
        return database;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static int requireValidPort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("redis port must be between 1 and 65535");
        }
        return port;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RedisConnectionSettings)) {
            return false;
        }
        RedisConnectionSettings that = (RedisConnectionSettings) other;
        return port == that.port
                && database == that.database
                && host.equals(that.host)
                && Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, password, database);
    }
}
