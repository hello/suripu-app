package com.hello.suripu.app.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.hello.suripu.core.models.timeline.v2.Timeline;
import net.spy.memcached.MemcachedClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TimelineMemCache implements TimelineCache {

    private final static Logger LOGGER = LoggerFactory.getLogger(TimelineMemCache.class);

    private final ObjectMapper mapper;
    private final MemcachedClient memcachedClient;
    private final static Integer EXPIRES_IN_SECONDS = 60;

    private String computeKey(final Long accountId, final String dateNight) {
        return String.format("timeline:v2:%s-%s", accountId, dateNight);
    }

    public TimelineMemCache(final ObjectMapper mapper, final MemcachedClient memcachedClient) {
        this.mapper = mapper;
        this.memcachedClient = memcachedClient;
    }

    @Override
    public Optional<Timeline> get(final Long accountId, String dateNight) {
        final String cacheKey = computeKey(accountId, dateNight);
        try {

            final byte[] serialized = (byte[]) memcachedClient.get(cacheKey);
            if(serialized == null) {
                return Optional.absent();
            }

            final Timeline timeline = mapper.readValue(serialized, Timeline.class);
            return Optional.of(timeline);

        } catch (IOException e) {
            LOGGER.error("action=save-timeline-cache key={} error={}", "", e.getMessage());
        }
        return Optional.absent();
    }

    @Override
    public void save(Timeline timeline, Long accountId, String dateNight) {
        final String cacheKey = computeKey(accountId, dateNight);
        try {
            final byte[] serialized = mapper.writeValueAsBytes(timeline);
            memcachedClient.set(cacheKey, EXPIRES_IN_SECONDS, serialized);
        } catch (JsonProcessingException e) {
            LOGGER.error("action=save-timeline-cache key={} error={}", cacheKey, e.getMessage());
        }
    }

    @Override
    public void invalidate(Long accountId, String dateNight) {
        final String cacheKey = computeKey(accountId, dateNight);
        try {
            memcachedClient.delete(cacheKey);
        } catch (Exception e) {
            LOGGER.error("action=invalidate-cache key={} error={}", cacheKey, e.getMessage());
        }
    }
}
