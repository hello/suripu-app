package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.hello.suripu.core.models.Sensor;

import java.util.List;

public class BatchQuery {

    private final QueryScope scope;
    private final List<Sensor> sensors;

    private BatchQuery(QueryScope scope, List<Sensor> sensors) {
        this.scope = scope;
        this.sensors = sensors;
    }

    @JsonCreator
    public static BatchQuery create(
            @JsonProperty("scope") final QueryScope scope,
            @JsonProperty("sensors") final List<Sensor> sensors) {

        return new BatchQuery(scope, sensors);
    }

    public QueryScope scope() {
        return scope;
    }

    public List<Sensor> sensors() {
        return sensors;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(BatchQuery.class)
                .add("scope", scope)
                .add("sensors", sensors)
                .toString();
    }
}
