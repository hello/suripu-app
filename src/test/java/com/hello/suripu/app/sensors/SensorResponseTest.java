package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hello.suripu.core.roomstate.Condition;
import org.junit.Test;

import java.util.ArrayList;

public class SensorResponseTest {

    final private ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testSerializeEmpty() throws JsonProcessingException {

        final SensorResponse sr = new SensorResponse(SensorStatus.OK, new ArrayList<>(), Condition.UNKNOWN);
        mapper.writeValueAsString(sr);
    }

    @Test
    public void testSerializeNull() throws JsonProcessingException {

        final SensorResponse sr = new SensorResponse(SensorStatus.OK, null, Condition.UNKNOWN);
        mapper.writeValueAsString(sr);
    }
}
