package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hello.suripu.core.models.Sensor;
import org.junit.Test;

public class ScaleFactoryTest {

    final private ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testSerializeEmptyScale() throws JsonProcessingException {
        final ScaleFactory scaleFactory = new ScaleFactory();
        final Scale scale = scaleFactory.forSensor(Sensor.CO2);
        mapper.writeValueAsString(scale);
    }
}
