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
    private static String UNKNOWN_MESSAGE = "";
    private final ScaleFactory scaleFactory;

    public SensorViewFactory(ScaleFactory scaleFactory) {
        this.scaleFactory = scaleFactory;
    }

    public Optional<SensorView> from(Sensor sensor, CurrentRoomState roomState) {
        final Scale scale = scaleFactory.forSensor(sensor);

        switch (sensor) {
            case TEMPERATURE:
                final SensorState tempState = fromScale(roomState.temperature().value, scale);
                final SensorView temperature = SensorView.from("Temperature", Sensor.TEMPERATURE, SensorUnit.CELSIUS, scale, tempState);
                return Optional.of(temperature);
            case HUMIDITY:
                final SensorState humidityState = fromScale(roomState.humidity().value, scale);
                final SensorView humidity = SensorView.from("Humidity", Sensor.HUMIDITY, SensorUnit.PERCENT, scale, humidityState);
                return Optional.of(humidity);
            case LIGHT:
                final SensorState lightState = fromScale(roomState.light().value, scale);
                final SensorView light = SensorView.from("Light", Sensor.LIGHT, SensorUnit.LUX, scale, lightState);
                return Optional.of(light);
            case SOUND:
                final SensorState soundState = fromScale(roomState.sound().value, scale);
                final SensorView sound = SensorView.from("Noise", Sensor.SOUND, SensorUnit.DB, scale, soundState);
                return Optional.of(sound);
            case PARTICULATES:
                if(roomState.particulates() != null) {
                    final SensorState particulatesState = fromScale(roomState.particulates().value, scale);
                    final SensorView airQuality = SensorView.from("Particulates", Sensor.PARTICULATES, SensorUnit.MG_CM, scale, particulatesState);
                    return Optional.of(airQuality);
                }
        }
        return Optional.absent();
    }

    public Optional<SensorView> from(final Sensor sensor, final CurrentRoomState roomState, final DeviceData deviceData) {
        if (deviceData.hasExtra()) {
            final Scale scale = scaleFactory.forSensor(sensor);
            switch (sensor) {
                case CO2:
                    final SensorState co2State = fromScale(new Float(deviceData.extra().co2()), scale);
                    final SensorView co2 = SensorView.from("CO2", Sensor.CO2, SensorUnit.PPM, scale, co2State);
                    return Optional.of(co2);
                case TVOC:
                    final SensorState tvocState = fromScale(new Float(deviceData.extra().tvoc()), scale);
                    final SensorView tvoc = SensorView.from("VOC", Sensor.TVOC, SensorUnit.MG_CM, scale, tvocState);
                    return Optional.of(tvoc);
                case UV:
                    final SensorState uvState = fromScale(new Float(deviceData.extra().uvCount()), scale);
                    final SensorView uv = SensorView.from("UV Light", Sensor.UV, SensorUnit.COUNT, scale, uvState);
                    return Optional.of(uv);
                case PRESSURE:
                    final SensorState pressureState = fromScale(new Float(deviceData.extra().pressure()), scale);
                    final SensorView pressure = SensorView.from("Barometric Pressure", Sensor.PRESSURE, SensorUnit.MILLIBAR, scale, pressureState);
                    return Optional.of(pressure);
            }
        }
        final Optional<SensorView> view = from(sensor, roomState);
        if(!view.isPresent()) {
            LOGGER.warn("msg=missing-sensor-data sensor={} account_id={}", sensor, deviceData.accountId);
        }
        return view;
    }

    public static SensorState fromScale(Float value, Scale scale) {
        if(value != null) {
            for(final ScaleInterval interval : scale.intervals()) {
                if(inRange(value, interval)) {
                    return new SensorState(value, interval.message(), interval.condition());
                }
            }
        }

        return new SensorState(value, UNKNOWN_MESSAGE, Condition.UNKNOWN);
    }

    public static boolean inRange(Float value, ScaleInterval interval) {
        if(value == null) {
            return false;
        }
        if(interval.min() != null & interval.max()!= null) {
            return value >= interval.min() && value <= interval.max();
        } else if(interval.min() == null) {
            return value <= interval.max();
        } else if(interval.max() == null) {
            return value >= interval.min();
        }
        return false;
    }
}
