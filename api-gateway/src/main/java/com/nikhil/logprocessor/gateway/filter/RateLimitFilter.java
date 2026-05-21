package com.nikhil.logprocessor.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;

/**
 * Spring Cloud Gateway filter that uses Redis to implement rate limiting
 * Controls how many requests a client can make within a time window (e.g., 100 requests per 60 seconds).
 * Uses Redis to track counts across multiple server instances
 */
@Component
@Slf4j
public class RateLimitFilter extends AbstractGatewayFilterFactory<RateLimitFilter.Config> {

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    public RateLimitFilter() {
        super(Config.class);
    }

    /**
     * Main Logic - Called when filter is applied to a route
     *
     * FLOW DIAGRAM:
     * Request arrives
     *     ↓
     * Get client IP → "192.168.1.1"
     *     ↓
     * Redis key: "rate_limit:192.168.1.1"
     *     ↓
     * Get current count from Redis
     *     ↓
     * count >= 100?
     *     ├─ YES → Return 429 (Too Many Requests) ❌
     *     └─ NO → Increment count by 1
     *         ↓
     *         Is it first request (count == 1)?
     *         ├─ YES → Set Redis expiry to 60 sec
     *         └─ NO → Continue
     *         ↓
     *         Pass to next filter ✅
     *
     * Example Scenario:
     * Config: limit=100, window=60 seconds
     * Request #    Time    Count   Redis Key           Action
     * 1            0s      1       Set + expire 60s    ✅ Pass
     * 2            1s      2       Get from Redis      ✅ Pass
     * 50           10s     50      Increment           ✅ Pass
     * 100          30s     100     Increment           ✅ Pass
     * 101          31s     -       count >= 100        ❌ 429 Blocked
     * -            60s     -       Redis key expires   🔄 Counter resets
     * 102          61s     1       New key created     ✅ Pass
     *
     */
    @Override
    public GatewayFilter apply(Config config) {
        log.info("RateLimitFilter.apply() called with config - limit: {}, window: {}", config.getLimit(), config.getWindow());

        /**
         * Lambda that processes each request
         * @param exchange: HTTP request/response object
         * @param chain: Remaining filters to execute
         */
        return (exchange, chain) -> {
            String clientId = getClientId(exchange);
            String key = "rate_limit:" + clientId;      // e.g. "rate_limit:192.168.1.1"

            log.debug("RateLimitFilter: Processing request from clientId: {}, key: {}", clientId, key);

            // Fetch request count from Redis (non-blocking)
            ReactiveValueOperations<String, String> ops = redisTemplate.opsForValue();
            return ops.get(key)
                    .doOnNext(value -> log.debug("RateLimitFilter: Retrieved current count from Redis: {}", value))
                    .cast(String.class)
                    .defaultIfEmpty("0")      // If no count exists, start at 0
                    .doOnNext(count -> log.debug("RateLimitFilter: Current count (or 0): {}", count))
                    .flatMap(currentCount -> {  // Check if Limit Exceeded
                        int count = Integer.parseInt(currentCount);

                        log.debug("RateLimitFilter: Parsed count: {}", count);

                        // If count ≥ limit (100): Return HTTP 429 (Too Many Requests)
                        if (count >= config.getLimit()) {
                            log.warn("RateLimitFilter: Rate limit exceeded for clientId: {}. Count: {}, Limit: {}", 
                                    clientId, count, config.getLimit());
                            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);

                            // End the request, don't proceed to next filter
                            return exchange.getResponse().setComplete();
                        }

                        log.debug("RateLimitFilter: Incrementing counter for clientId: {}", clientId);

                        // Increase count by 1 in Redis
                        return ops.increment(key)
                                .doOnNext(newCount -> log.debug("RateLimitFilter: New count after increment: {}", newCount))
                                .flatMap(newCount -> {  // The new count value after increment

                                    // Set Expiry on First Request
                                    if (newCount == 1) {
                                        log.info("RateLimitFilter: First request from clientId: {}. Setting expiry to {} seconds", 
                                                clientId, config.getWindow());

                                        // If 1st request, set Redis key to expire after window seconds (60 sec)
                                        // This resets the counter automatically
                                        return redisTemplate.expire(key, Duration.ofSeconds(config.getWindow()))
                                                .doOnNext(result -> log.debug("RateLimitFilter: Expire set result: {}", result))
                                                .then(chain.filter(exchange));  // Pass request to next filter
                                    }
                                    log.debug("RateLimitFilter: Allowing request. Count: {}/{}", newCount, config.getLimit());
                                    return chain.filter(exchange);
                                });
                    })
                    .doOnError(error -> log.error("RateLimitFilter: Error processing rate limit", error));
        };
    }

    /**
     * Identifies the client (using IP address) to uniquely identify them
     * @param exchange
     * @return
     */
    private String getClientId(ServerWebExchange exchange) {
        // Simple client identification - in production use proper authentication
        String clientIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        log.debug("RateLimitFilter: Client IP extracted: {}", clientIp);
        return clientIp;
    }

    public static class Config {
        private int limit = 100;    // Max requests allowed
        private int window = 60;    // Time window in seconds

        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }

        public int getWindow() { return window; }
        public void setWindow(int window) { this.window = window; }
    }
}
