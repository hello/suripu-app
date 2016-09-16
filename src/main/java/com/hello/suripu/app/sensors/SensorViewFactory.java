package com.hello.suripu.app.sensors;

import com.google.common.base.Optional;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.roomstate.Condition;
import com.hello.suripu.core.roomstate.CurrentRoomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SensorViewFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensorViewFactory.class);
    private final ScaleFactory scaleFactory;

    public SensorViewFactory(ScaleFactory scaleFactory) {
        this.scaleFactory = scaleFactory;
    }

    public Optional<SensorView> from(Sensor sensor, CurrentRoomState roomState) {
        final Scale scale = scaleFactory.forSensor(sensor);

        switch (sensor) {
            case TEMPERATURE:
                final SensorView temperature = new SensorView(
                        "Temperature", Sensor.TEMPERATURE, SensorUnit.CELSIUS, roomState.temperature().value,
                        "some clever message", roomState.temperature().condition(), scale
                );
                return Optional.of(temperature);
            case HUMIDITY:
                final SensorView humidity = new SensorView(
                        "Humidity", Sensor.HUMIDITY, SensorUnit.PERCENT, roomState.humidity().value, roomState.humidity().message,
                        roomState.humidity().condition(), scale);
                return Optional.of(humidity);
            case LIGHT:
                final SensorView light = new SensorView(
                        "Light", Sensor.LIGHT, SensorUnit.LUX, roomState.light().value, roomState.light().message, roomState.light().condition(), scale);
                return Optional.of(light);
            case SOUND:
                final SensorView sound = new SensorView(
                        "Noise", Sensor.SOUND, SensorUnit.DB, roomState.sound().value, roomState.sound().message, roomState.sound().condition(), scale);
                return Optional.of(sound);
            case PARTICULATES:
                if(roomState.particulates() != null) {
                    final SensorView airQuality = new SensorView(
                            "Air quality", Sensor.PARTICULATES, SensorUnit.MG_CM, roomState.particulates().value,
                            roomState.particulates().message, roomState.particulates().condition(), scale);
                    return Optional.of(airQuality);
                }


        }
        LOGGER.warn("msg=missing-sensor-data sensor={}", sensor);
        return Optional.absent();
    }

    public Optional<SensorView> from(Sensor sensor, CurrentRoomState roomState, DeviceData deviceData) {
        final Scale scale = scaleFactory.forSensor(sensor);
        switch (sensor) {
            case CO2:
                if(deviceData.hasExtra()) {
                    final SensorView co2 = new SensorView(
                            "Co2", Sensor.CO2, SensorUnit.RATIO, (float) deviceData.extra().co2(), "Co2 message", Condition.WARNING, scale);
                    return Optional.of(co2);
                }

        }
        return from(sensor, roomState);
    }
}
