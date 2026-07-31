package com.sni.bilanga.mailService.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class GraphApiRateLimiter {

    @Value("${bokati.graph-api.rate-limit.limit-for-period:800}")
    private int limitForPeriod;

    @Value("${bokati.graph-api.rate-limit.limit-refresh-period-ms:60000}")
    private long refreshPeriodMs;

    @Value("${bokati.graph-api.rate-limit.timeout-ms:30000}")
    private long timeoutMs;

    private Semaphore semaphore;
    private final AtomicLong lastRefresh = new AtomicLong(0);

    @PostConstruct
    public void init() {
        semaphore = new Semaphore(limitForPeriod);
        lastRefresh.set(System.currentTimeMillis());
        log.info("Microsoft Graph rate limiter initialized: {}/{}ms", limitForPeriod, refreshPeriodMs);
    }

    public void acquirePermission() {
        refreshIfNeeded();
        try {
            if (!semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                        "Microsoft Graph rate limit exceeded · could not acquire permit within " + timeoutMs + "ms");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for rate limit permit", e);
        }
    }

    private void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        long last = lastRefresh.get();
        if (now - last >= refreshPeriodMs && lastRefresh.compareAndSet(last, now)) {
            int permitsToRelease = limitForPeriod - semaphore.availablePermits();
            if (permitsToRelease > 0) {
                semaphore.release(permitsToRelease);
            }
        }
    }
}
