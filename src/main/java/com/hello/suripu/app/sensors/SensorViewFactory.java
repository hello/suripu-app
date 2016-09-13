package com.hello.suripu.app.sensors;

import com.google.common.base.Optional;
import com.hello.suripu.core.models.CurrentRoomState;

public class SensorViewFactory {

    private final ScaleFactory scaleFactory;

    public SensorViewFactory(ScaleFactory scaleFactory) {
        this.scaleFactory = scaleFactory;
    }

    public Optional<SensorView> from(SensorName sensorName, CurrentRoomState roomState) {
        final Scale scale = scaleFactory.forSensor(sensorName);

        switch (sensorName) {
            case TEMPERATURE:
                final SensorView temperature = new SensorView(
                        "Temperature", SensorName.TEMPERATURE, SensorUnit.CELSIUS, roomState.temperature.value,
                        "some clever message", roomState.temperature.condition, scale
                );
                return Optional.of(temperature);
            case HUMIDITY:
                final SensorView humidity = new SensorView(
                        "Humidity", SensorName.HUMIDITY, SensorUnit.PERCENT, roomState.humidity.value, roomState.humidity.message,
                        roomState.humidity.condition, scale);
                return Optional.of(humidity);
            case LIGHT:
                final SensorView light = new SensorView(
                        "Light", SensorName.LIGHT, SensorUnit.LUX, roomState.light.value, roomState.light.message, roomState.light.condition, scale);
                return Optional.of(light);
            case SOUND:
                final SensorView sound = new SensorView(
                        "Sound", SensorName.SOUND, SensorUnit.DB, roomState.sound.value, roomState.sound.message, roomState.sound.condition, scale);
                return Optional.of(sound);
            case PARTICULATES:
                if(roomState.hasDust) {
                    final SensorView airQuality = new SensorView(
                            "Air quality", SensorName.PARTICULATES, SensorUnit.MG_CM, roomState.particulates.value,
                            roomState.particulates.message, roomState.particulates.condition, scale);
                    return Optional.of(airQuality);
                }
        }
        return Optional.absent();
    }
}
