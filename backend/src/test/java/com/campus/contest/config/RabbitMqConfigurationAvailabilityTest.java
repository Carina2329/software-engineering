package com.campus.contest.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 防止 RabbitMQ 拓扑启动所需属性只存在于开发者本机环境变量中。
 */
class RabbitMqConfigurationAvailabilityTest {

    @Test
    void applicationYamlDefinesRetryRoutingKey() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        Object retryRoutingKey = sources.stream()
                .map(source -> source.getProperty("app.mail.queue.retry-routing-key"))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        assertNotNull(
                retryRoutingKey,
                "application.yml 必须配置 app.mail.queue.retry-routing-key"
        );
        assertFalse(retryRoutingKey.toString().isBlank());
    }
}
