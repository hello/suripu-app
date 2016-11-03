package com.hello.suripu.app.sensors;

import com.google.common.base.Optional;
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
    public final Optional<DeviceData> oldData;

    public SensorViewQuery(final Sensor sensor, final CurrentRoomState roomState, final DeviceData deviceData, final Optional<DeviceData> oldData, final DateTime now, final Device.Color color) {
        this.sensor = sensor;
        this.roomState = roomState;
        this.deviceData = deviceData;
        this.oldData = oldData;
        this.now = now;
        this.color = color;
    }

    public static SensorViewQuery create(final Sensor sensor, final CurrentRoomState roomState, final DeviceData deviceData, final DateTime now, final Device.Color color) {
        return new SensorViewQuery(sensor, roomState, deviceData, Optional.absent(), now, color);
    }

    public static SensorViewQuery createWithOldData(final Sensor sensor, final CurrentRoomState roomState, final DeviceData deviceData, final DeviceData oldData, final DateTime now, final Device.Color color) {
        return new SensorViewQuery(sensor, roomState, deviceData, Optional.fromNullable(oldData), now, color);
    }
}
