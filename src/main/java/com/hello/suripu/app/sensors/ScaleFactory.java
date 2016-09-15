package com.hello.suripu.app.sensors;

import com.hello.suripu.app.sensors.scales.HumidityScale;
import com.hello.suripu.app.sensors.scales.LightScale;
import com.hello.suripu.app.sensors.scales.TemperatureScale;

import java.util.ArrayList;
import java.util.List;

public class ScaleFactory {

    public static class EmptyScale implements Scale {

        @Override
        public List<ScaleInterval> intervals() {
            return new ArrayList<>();
        }
    }

    Scale forSensor(SensorName name) {
        switch (name) {
            case TEMPERATURE:
                return new TemperatureScale();
            case HUMIDITY:
                return new HumidityScale();
            case LIGHT:
                return new LightScale();
            case PARTICULATES:
                return new TemperatureScale();
            case LIGHT_TEMPERATURE:
                return new TemperatureScale();
            case SOUND:
                return new TemperatureScale();
        }

        return new EmptyScale();
    }
}
