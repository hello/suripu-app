package com.hello.suripu.app.models;

import com.google.common.base.Optional;
import com.hello.suripu.core.models.timeline.v2.Timeline;

public interface TimelineCache {
    Optional<Timeline> get(Long accountId, String dateNight);

    void save(Timeline timeline, Long accountId, String dateNight);
    void invalidate(Long accountId, String dateNight);
}
