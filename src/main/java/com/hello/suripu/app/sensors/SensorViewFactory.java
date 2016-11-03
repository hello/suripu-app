package com.hello.suripu.app.sensors;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hello.suripu.app.sensors.scales.PressureScale;
import com.hello.suripu.core.models.CalibratedDeviceData;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.Sensor;
import org.joda.time.DateTime;
import org.joda.time.Minutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class SensorViewFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensorViewFactory.class);
    private static Integer DEFAULT_FRESHNESS_TRESHOLD = 15;
    private final ScaleFactory scaleFactory;
    private final Integer minutesBeforeDataTooOld;


    private static final Map<Sensor, String> titles;
    static {
        final Map<Sensor, String> temp = Maps.newHashMap();
        temp.put(Sensor.TEMPERATURE, "Temperature");
        temp.put(Sensor.HUMIDITY, "Humidity");
        temp.put(Sensor.LIGHT, "Light");
        temp.put(Sensor.SOUND, "Noise");
        temp.put(Sensor.PARTICULATES, "Air Quality");
        temp.put(Sensor.CO2, "CO2");
        temp.put(Sensor.TVOC, "VOC");
        temp.put(Sensor.UV, "UV Light");
        temp.put(Sensor.PRESSURE, "Barometric pressure");
        titles = ImmutableMap.copyOf(temp);
    }

    private static final Map<Sensor, SensorUnit> units;
    static {
        final Map<Sensor, SensorUnit> temp = Maps.newHashMap();
        temp.put(Sensor.TEMPERATURE, SensorUnit.CELSIUS);
        temp.put(Sensor.HUMIDITY, SensorUnit.PERCENT);
        temp.put(Sensor.LIGHT, SensorUnit.LUX);
        temp.put(Sensor.SOUND, SensorUnit.DB);
        temp.put(Sensor.PARTICULATES, SensorUnit.MG_CM);
        temp.put(Sensor.CO2, SensorUnit.PPM);
        temp.put(Sensor.TVOC, SensorUnit.MG_CM);
        temp.put(Sensor.UV, SensorUnit.COUNT);
        temp.put(Sensor.PRESSURE, SensorUnit.MILLIBAR);
        units = ImmutableMap.copyOf(temp);
    }

    public SensorViewFactory(final ScaleFactory scaleFactory, final Integer minutesBeforeDataTooOld) {
        this.scaleFactory = scaleFactory;
        this.minutesBeforeDataTooOld = minutesBeforeDataTooOld;
    }

    public static SensorViewFactory build(final ScaleFactory scaleFactory) {
        return new SensorViewFactory(scaleFactory, DEFAULT_FRESHNESS_TRESHOLD);
    }

    public Optional<SensorView> adjustFreshness(final SensorView sensorView, final DateTime sampleTime, final DateTime now) {
        final boolean tooOld = Minutes.minutesBetween(sampleTime, now).isGreaterThan(Minutes.minutes(minutesBeforeDataTooOld));
        if(tooOld) {
            return Optional.of(SensorView.tooOld(sensorView));
        }
        return Optional.of(sensorView);
    }

    public SensorView renderSensor(final Sensor sensor, final SensorState sensorState, final Optional<Scale> customScale)  {
        final Scale scale = customScale.or(scaleFactory.forSensor(sensor));
        final String title = titles.getOrDefault(sensor, "");
        return SensorView.from(title, sensor, units.get(sensor), scale, sensorState);
    }

    public Optional<SensorView> tooOld(final Sensor sensor)  {
        return Optional.of(renderSensor(sensor, SensorState.unknown(), Optional.absent()));
    }

    public Optional<SensorView> from(final SensorViewQuery query) {
        Scale scale = scaleFactory.forSensor(query.sensor);
        final CalibratedDeviceData calibratedDeviceData = new CalibratedDeviceData(query.deviceData, query.color, Optional.absent());

        SensorState state;

        switch (query.sensor) {
            case TEMPERATURE:
                state = fromScale(calibratedDeviceData.temperature(), scale);
                break;
            case HUMIDITY:
                state = fromScale(calibratedDeviceData.humidity(), scale);
                break;
            case LIGHT:
                state = fromScale(calibratedDeviceData.lux(), scale);
                break;
            case SOUND:
                state = fromScale(calibratedDeviceData.sound(true), scale);
                break;
            case PARTICULATES:
                if(query.roomState.particulates() == null) {
                    return Optional.absent();
                }
                state = fromScale(calibratedDeviceData.particulates(), scale);
                break;
            case CO2:
                if(!query.deviceData.hasExtra()) {
                    return Optional.absent();
                }
                state = fromScale(calibratedDeviceData.co2(), scale);
                break;
            case TVOC:
                if(!query.deviceData.hasExtra()) {
                    return Optional.absent();
                }
                state = fromScale(calibratedDeviceData.tvoc(), scale);
                break;
            case UV:
                if(!query.deviceData.hasExtra()) {
                    return Optional.absent();
                }
                state = fromScale(new Float(query.deviceData.extra().uvCount()), scale);
                break;
            case PRESSURE:
                if(!query.deviceData.hasExtra()) {
                    return Optional.absent();
                }
                // Pressure sensor is basically one big exception
                final PressureScale pressureScale = scaleFactory.pressure(calibratedDeviceData.pressure());
                final DeviceData old = query.oldData.or(query.deviceData);
                final CalibratedDeviceData oldCalibrated = new CalibratedDeviceData(old, query.color, Optional.absent());
                final float pressureChange = calibratedDeviceData.pressure() - oldCalibrated.pressure();
                state = pressureState(calibratedDeviceData.pressure(), pressureChange, pressureScale);
                scale = pressureScale;
                break;
            default:
                LOGGER.warn("msg=missing-sensor-data sensor={} account_id={}", query.sensor, query.deviceData.accountId);
                return Optional.absent();
        }
        final SensorView view = renderSensor(query.sensor, state, Optional.of(scale));
        return adjustFreshness(view, query.deviceData.dateTimeUTC, query.now);
    }

    public static SensorState fromScale(final Float value, final Scale scale) {
        if(value == null) {
            return SensorState.unknown();
        }

        for(final ScaleInterval interval : scale.intervals()) {
            if(inRange(value, interval)) {
                return new SensorState(value, interval.message(), interval.condition());
            }
        }
        LOGGER.warn("msg=not-in-range value={} scale={}", value, scale);
        return SensorState.unknown();
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

    public static SensorState pressureState(float currentValue, float diffInPressure, final PressureScale pressureScale) {
        final SensorState state = fromScale(diffInPressure, pressureScale.changeScale());
        return new SensorState(currentValue, state.message, state.condition);

    }
}
