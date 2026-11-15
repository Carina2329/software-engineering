package com.campus.contest.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证邮件 OTP 专用 RabbitMQ 拓扑。
 *
 * <p>本配置只用于认证邮件 OTP，不允许被其他业务复用。</p>
 */
@Configuration
@EnableRabbit
public class RabbitMqConfig {

    @Bean
    public DirectExchange emailOtpExchange(
            @Value("${app.mail.queue.exchange:auth.otp.exchange}") String exchange
    ) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    public DirectExchange emailOtpDeadLetterExchange(
            @Value("${app.mail.queue.dead-letter-exchange:auth.otp.dlx}") String exchange
    ) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    public Queue emailOtpSendQueue(
            @Value("${app.mail.queue.send-queue:auth.otp.send.queue}") String queue,
            @Value("${app.mail.queue.dead-letter-exchange:auth.otp.dlx}") String deadLetterExchange,
            @Value("${app.mail.queue.dead-letter-queue:auth.otp.dlq}") String deadLetterQueue
    ) {
        return QueueBuilder.durable(queue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(deadLetterQueue)
                .build();
    }

    @Bean
    public Queue emailOtpRetryQueue(
            @Value("${app.mail.queue.retry-queue:auth.otp.retry.queue}") String queue,
            @Value("${app.mail.queue.exchange:auth.otp.exchange}") String exchange,
            @Value("${app.mail.queue.routing-key}") String sendRoutingKey,
            @Value("${app.mail.queue.retry-delay-ms:30000}") long retryDelayMs
    ) {
        return QueueBuilder.durable(queue)
                .ttl(Math.toIntExact(retryDelayMs))
                .deadLetterExchange(exchange)
                .deadLetterRoutingKey(sendRoutingKey)
                .build();
    }

    @Bean
    public Queue emailOtpDeadLetterQueue(
            @Value("${app.mail.queue.dead-letter-queue:auth.otp.dlq}") String queue
    ) {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    public Binding emailOtpSendBinding(
            @Qualifier("emailOtpExchange") DirectExchange emailOtpExchange,
            @Qualifier("emailOtpSendQueue") Queue emailOtpSendQueue,
            @Value("${app.mail.queue.routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(emailOtpSendQueue)
                .to(emailOtpExchange)
                .with(routingKey);
    }

    @Bean
    public Binding emailOtpRetryBinding(
            @Qualifier("emailOtpExchange") DirectExchange emailOtpExchange,
            @Qualifier("emailOtpRetryQueue") Queue emailOtpRetryQueue,
            @Value("${app.mail.queue.retry-routing-key}") String retryRoutingKey
    ) {
        return BindingBuilder.bind(emailOtpRetryQueue)
                .to(emailOtpExchange)
                .with(retryRoutingKey);
    }

    @Bean
    public Binding emailOtpDeadLetterBinding(
            @Qualifier("emailOtpDeadLetterExchange") DirectExchange emailOtpDeadLetterExchange,
            @Qualifier("emailOtpDeadLetterQueue") Queue emailOtpDeadLetterQueue,
            @Value("${app.mail.queue.dead-letter-queue:auth.otp.dlq}") String routingKey
    ) {
        return BindingBuilder.bind(emailOtpDeadLetterQueue)
                .to(emailOtpDeadLetterExchange)
                .with(routingKey);
    }

    @Bean
    public MessageConverter emailOtpMessageConverter(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
