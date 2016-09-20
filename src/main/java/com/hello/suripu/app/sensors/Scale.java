package com.hello.suripu.app.sensors;

import com.google.common.base.MoreObjects;

import java.util.List;

public abstract class Scale {

    abstract public List<ScaleInterval> intervals();

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(Scale.class)
                .add("intervals", intervals())
                .toString();
    }
}
