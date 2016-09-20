package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.hello.suripu.core.models.Sensor;

public class SensorQuery {
    final private Sensor type;
    final private QueryScope scope;
    final private SensorUnit unit;
    final private AggregationMethod aggregationMethod;

    private SensorQuery(Sensor type, QueryScope scope, SensorUnit unit, AggregationMethod aggregationMethod) {
        this.type = type;
        this.scope = scope;
        this.unit = unit;
        this.aggregationMethod = aggregationMethod;
    }

    @JsonCreator
    public static SensorQuery create(
            @JsonProperty("type") Sensor type,
            @JsonProperty("scope")QueryScope scope,
            @JsonProperty("unit") SensorUnit unit,
            @JsonProperty("aggregation_method") AggregationMethod aggregationMethod) {
        return new SensorQuery(type, scope, unit, aggregationMethod);
    }

    public Sensor type() {
        return type;
    }

    public QueryScope scope() {
        return scope;
    }

    public SensorUnit unit() {
        return unit;
    }

    public AggregationMethod aggregationMethod() {
        return aggregationMethod;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(SensorQuery.class)
                .add("type", type)
                .add("scope", scope)
                .add("unit", unit)
                .add("aggregation_method", aggregationMethod)
                .toString();

    }
}
