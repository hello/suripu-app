package com.hello.suripu.app.sensors;

import com.hello.suripu.app.sensors.scales.Co2Scale;
import com.hello.suripu.app.sensors.scales.HumidityScale;
import com.hello.suripu.app.sensors.scales.LightScale;
import com.hello.suripu.app.sensors.scales.TemperatureScale;
import com.hello.suripu.app.sensors.scales.UvScale;
import com.hello.suripu.app.sensors.scales.VocScale;
import com.hello.suripu.core.models.Sensor;

import java.util.ArrayList;
import java.util.List;

public class ScaleFactory {

    public class EmptyScale extends Scale {

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
            case CO2:
                return new Co2Scale();
            case TVOC:
                return new VocScale();
            case UV:
                return new UvScale();
        }

        return new EmptyScale();
    }
}
