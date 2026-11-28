package com.campus.contest.recommendation.stream.config;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public final class StreamSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String kafkaBootstrapServers;
    private final String kafkaTopic;
    private final String kafkaGroupId;
    private final RedisConnectionSettings redis;
    private final int windowMinutes;
    private final int watermarkMinutes;
    private final int retentionDays;
    private final long checkpointIntervalMs;
    private final int parallelism;

    public StreamSettings(
            String kafkaBootstrapServers,
            String kafkaTopic,
            String kafkaGroupId,
            RedisConnectionSettings redis,
            int windowMinutes,
            int watermarkMinutes,
            int retentionDays,
            long checkpointIntervalMs,
            int parallelism
    ) {
        this.kafkaBootstrapServers = requireText(
                kafkaBootstrapServers,
                "kafka bootstrap servers"
        );
        this.kafkaTopic = requireText(kafkaTopic, "kafka topic");
        this.kafkaGroupId = requireText(kafkaGroupId, "kafka group id");
        this.redis = redis;
        this.windowMinutes = requirePositive(windowMinutes, "window minutes");
        this.watermarkMinutes = requirePositive(watermarkMinutes, "watermark minutes");
        this.retentionDays = requirePositive(retentionDays, "retention days");
        if (checkpointIntervalMs <= 0) {
            throw new IllegalArgumentException("checkpoint interval must be > 0");
        }
        this.checkpointIntervalMs = checkpointIntervalMs;
        this.parallelism = requirePositive(parallelism, "parallelism");
    }

    public static StreamSettings fromSystemEnvironment(String[] args) {
        return fromArgs(args, System.getenv());
    }

    static StreamSettings fromArgs(String[] args, Map<String, String> environment) {
        Map<String, String> cli = parseArguments(args);
        Map<String, String> env = environment == null ? Map.of() : environment;

        String redisHost = value(cli, env, "redis-host", "REDIS_HOST", "localhost");
        int redisPort = parseInt(
                value(cli, env, "redis-port", "REDIS_PORT", "6379"),
                "redis port"
        );
        String redisPassword = value(
                cli,
                env,
                "redis-password",
                "REDIS_PASSWORD",
                ""
        );
        int redisDatabase = parseInt(
                value(cli, env, "redis-database", "REDIS_DATABASE", "0"),
                "redis database"
        );

        return new StreamSettings(
                value(
                        cli,
                        env,
                        "kafka-bootstrap-servers",
                        "KAFKA_BOOTSTRAP_SERVERS",
                        "localhost:9092"
                ),
                value(
                        cli,
                        env,
                        "kafka-topic",
                        "RECOMMENDATION_KAFKA_TOPIC",
                        "contest-behavior"
                ),
                value(
                        cli,
                        env,
                        "kafka-group-id",
                        "RECOMMENDATION_KAFKA_GROUP_ID",
                        "recommendation-stream"
                ),
                new RedisConnectionSettings(redisHost, redisPort, redisPassword, redisDatabase),
                parseInt(
                        value(
                                cli,
                                env,
                                "window-minutes",
                                "RECOMMENDATION_WINDOW_MINUTES",
                                "5"
                        ),
                        "window minutes"
                ),
                parseInt(
                        value(
                                cli,
                                env,
                                "watermark-minutes",
                                "RECOMMENDATION_WATERMARK_MINUTES",
                                "1"
                        ),
                        "watermark minutes"
                ),
                parseInt(
                        value(
                                cli,
                                env,
                                "retention-days",
                                "RECOMMENDATION_RETENTION_DAYS",
                                "7"
                        ),
                        "retention days"
                ),
                parseLong(
                        value(
                                cli,
                                env,
                                "checkpoint-interval-ms",
                                "RECOMMENDATION_CHECKPOINT_INTERVAL_MS",
                                "30000"
                        ),
                        "checkpoint interval"
                ),
                parseInt(
                        value(
                                cli,
                                env,
                                "parallelism",
                                "RECOMMENDATION_PARALLELISM",
                                "1"
                        ),
                        "parallelism"
                )
        );
    }

    private static Map<String, String> parseArguments(String[] args) {
        Map<String, String> result = new HashMap<>();
        if (args == null) {
            return result;
        }
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (argument == null || !argument.startsWith("--")) {
                throw new IllegalArgumentException("unsupported argument: " + argument);
            }
            String body = argument.substring(2);
            int separator = body.indexOf('=');
            if (separator >= 0) {
                result.put(body.substring(0, separator), body.substring(separator + 1));
                continue;
            }
            if (index + 1 >= args.length) {
                throw new IllegalArgumentException("missing value for argument: " + argument);
            }
            result.put(body, args[++index]);
        }
        return result;
    }

    private static String value(
            Map<String, String> cli,
            Map<String, String> environment,
            String cliKey,
            String environmentKey,
            String defaultValue
    ) {
        String cliValue = cli.get(cliKey);
        if (cliValue != null) {
            return cliValue;
        }
        String environmentValue = environment.get(environmentKey);
        return environmentValue == null ? defaultValue : environmentValue;
    }

    private static int parseInt(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    private static long parseLong(String value, String name) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a long integer", exception);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    public String getKafkaBootstrapServers() {
        return kafkaBootstrapServers;
    }

    public String getKafkaTopic() {
        return kafkaTopic;
    }

    public String getKafkaGroupId() {
        return kafkaGroupId;
    }

    public RedisConnectionSettings getRedis() {
        return redis;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public int getWatermarkMinutes() {
        return watermarkMinutes;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public long getCheckpointIntervalMs() {
        return checkpointIntervalMs;
    }

    public int getParallelism() {
        return parallelism;
    }
}
