package com.hello.suripu.app.models;

import com.google.common.base.Optional;
import com.hello.suripu.core.models.timeline.v2.Timeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopTimelineCache implements TimelineCache {
    private final static Logger LOGGER = LoggerFactory.getLogger(NoopTimelineCache.class);

    @Override
    public Optional<Timeline> get(Long accountId, String dateNight) {
        LOGGER.info("action=get-timeline-cache account_id={} date_night={}", accountId, dateNight);
        return Optional.of(Timeline.create());
    }

    @Override
    public void save(Timeline timeline, Long accountId, String dateNight) {
        LOGGER.info("action=save-timeline-cache account_id={} date_night={}", accountId, dateNight);
    }

    @Override
    public void invalidate(Long accountId, String dateNight) {
        LOGGER.info("action=invalidate-timeline-cache account_id={} date_night={}", accountId, dateNight);
    }
}
