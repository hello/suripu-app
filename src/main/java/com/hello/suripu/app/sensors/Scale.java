package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.List;

@JsonSerialize(as=Scale.class)
public interface Scale {
    List<ScaleInterval> intervals();
}
