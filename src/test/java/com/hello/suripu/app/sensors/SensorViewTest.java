package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.roomstate.Condition;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SensorViewTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private class StupidScale implements Scale {

        @Override
        public List<ScaleInterval> intervals() {
            return new ArrayList<>();
        }
    }
    @Test
    public void testSerialize() throws JsonProcessingException {
        final ScaleFactory scaleFactory = new ScaleFactory();
         SensorView view = new SensorView(
                 "name",
                 Sensor.CO2,
                 SensorUnit.RATIO,
                 0f,
                 "hi",
                 Condition.WARNING,
                 scaleFactory.forSensor(Sensor.CO2)
         );

        String json = mapper.writeValueAsString(view);
    }
}
