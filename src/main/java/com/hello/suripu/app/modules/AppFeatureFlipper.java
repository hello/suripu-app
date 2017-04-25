package com.hello.suripu.app.modules;

public class AppFeatureFlipper {

    public static String LOW_BATTERY_ALERT_OVERRIDE = "low_battery_alert_override";
    public static String SENSORS_V2_ENABLED = "sensors_v2_enabled";
    public static String NEST_ENABLED = "nest_enabled";
    /**
     * If false only allow returning alerts with categories:
     * {@link com.hello.suripu.core.alerts.AlertCategory#EXPANSION_UNREACHABLE}
     * {@link com.hello.suripu.core.alerts.AlertCategory#SENSE_MUTED}
     */
    public static String NEW_ALERTS_ENABLED = "new_alerts_enabled";

}
