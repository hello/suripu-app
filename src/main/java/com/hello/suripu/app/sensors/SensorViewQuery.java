package com.hello.suripu.app.sensors;

import com.google.common.base.Optional;
import com.hello.suripu.core.models.Calibration;
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
    public final Optional<Calibration> calibration;
    public final DateTime pairedAt;

    public SensorViewQuery(final Sensor sensor, final CurrentRoomState roomState, final DeviceData deviceData, final Optional<DeviceData> oldData, final DateTime now, final Device.Color color, final Optional<Calibration> calibration, final DateTime pairedAt) {
        this.sensor = sensor;
        this.roomState = roomState;
        this.deviceData = deviceData;
        this.oldData = oldData;
        this.now = now;
        this.color = color;
        this.calibration = calibration;
        this.pairedAt = pairedAt;
    }

    public static SensorViewQuery create(final Sensor sensor, final CurrentRoomState roomState, final DeviceData deviceData, final DateTime now, final Device.Color color, final DateTime pairedAt) {
        return new SensorViewQuery(sensor, roomState, deviceData, Optional.absent(), now, color, Optional.absent(), pairedAt);
    }

    public static SensorViewQuery createWithOldData(final Sensor sensor, final CurrentRoomState roomState, final DeviceData deviceData, final DeviceData oldData, final DateTime now, final Device.Color color, final DateTime pairedAt) {
        return new SensorViewQuery(sensor, roomState, deviceData, Optional.fromNullable(oldData), now, color, Optional.absent(), pairedAt);
    }


}
