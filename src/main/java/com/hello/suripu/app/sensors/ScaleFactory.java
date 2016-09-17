package com.hello.suripu.app.sensors;

import com.hello.suripu.app.sensors.scales.HumidityScale;
import com.hello.suripu.app.sensors.scales.LightScale;
import com.hello.suripu.app.sensors.scales.TemperatureScale;
import com.hello.suripu.core.models.Sensor;

import java.util.ArrayList;
import java.util.List;

public class ScaleFactory {

    public class EmptyScale implements Scale {

        @Override
        public List<ScaleInterval> intervals() {
            return new ArrayList<>();
        }
    }

    Scale forSensor(Sensor sensor) {
        switch (sensor) {
            case TEMPERATURE:
                return new TemperatureScale();
            case HUMIDITY:
                return new HumidityScale();
            case LIGHT:
                return new LightScale();
            case PARTICULATES:
                return new TemperatureScale();
            case SOUND:
                return new TemperatureScale();
        }

        return new EmptyScale();
    }
}
