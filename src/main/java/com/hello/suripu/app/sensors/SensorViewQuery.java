package com.hello.suripu.app.sensors;

import com.hello.suripu.core.models.Device;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.roomstate.CurrentRoomState;
import org.joda.time.DateTime;

public class SensorViewQuery {

    public final Sensor sensor;
    public final CurrentRoomState roomState;
    public final DeviceData deviceData;
    public final DateTime now;
    public final Device.Color color;

    public SensorViewQuery(final Sensor sensor, final CurrentRoomState roomState, final DeviceData deviceData, final DateTime now, final Device.Color color) {
        this.sensor = sensor;
        this.roomState = roomState;
        this.deviceData = deviceData;
        this.now = now;
        this.color = color;
    }
}
