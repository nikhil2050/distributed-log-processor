package com.nikhil.logprocessor.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {
    @Value("${LOG_PRODUCER_URL:http://localhost:8081}")
    public static final String LOGPRODUCER_URL = "http://localhost:8081";

    @Value("${LOG_CONSUMER_URL:http://localhost:8082}")
    public static final String LOGCONSUMER_URL = "http://localhost:8082";

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // IN:  http://localhost:8080/apigateway/producer/api/logs
                // OUT: http://localhost:8081/logproducer/api/logs
                .route("log-producer", r -> r.path("/apigateway/producer/**")
                        .filters(f -> f.rewritePath("/apigateway/producer/(?<segment>)", "/logproducer/${segment}"))
                        .uri(LOGPRODUCER_URL))

                // IN:  http://localhost:8080/apigateway/consumer/health
                // OUT: http://localhost:8082/logconsumer/health
                .route("log-consumer", r -> r.path("/apigateway/consumer/**")
                        .filters(f -> f.rewritePath("/apigateway/consumer/(?<segment>)", "/logconsumer/${segment}"))
                        .uri(LOGCONSUMER_URL))
                .build();
    }

}
