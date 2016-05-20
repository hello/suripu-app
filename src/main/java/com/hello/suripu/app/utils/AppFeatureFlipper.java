package com.hello.suripu.app.utils;

/**
 * Created by jakepiccolo on 5/20/16.
 */
public enum AppFeatureFlipper {
    DO_NOT_THROTTLE_IP("do_not_throttle_ip");

    public final String featureName;
    AppFeatureFlipper(String featureName) {
        this.featureName = featureName;
    }
}
