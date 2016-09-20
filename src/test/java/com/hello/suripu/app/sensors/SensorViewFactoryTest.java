package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.roomstate.CurrentRoomState;
import com.hello.suripu.core.sense.data.SenseOneFiveExtraData;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SensorViewFactoryTest {

    final private ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testSomething() {
        final SensorViewFactory factory = new SensorViewFactory(new ScaleFactory());
        Optional<SensorView> view = factory.from(Sensor.CO2, CurrentRoomState.empty(true));
        assertFalse("view is present", view.isPresent());
    }

    @Test
    public void testSomethingElse() {
        final SensorViewFactory factory = new SensorViewFactory(new ScaleFactory());
        final DeviceData data = new DeviceData.Builder()
                .withExternalDeviceId("yo")
                .withAccountId(999L)
                .withDateTimeUTC(DateTime.now(DateTimeZone.UTC))
                .withOffsetMillis(0)
                .withExtraSensorData(null)
                .build();
        Optional<SensorView> view = factory.from(Sensor.CO2, CurrentRoomState.empty(true), data);
        assertFalse("view is present", view.isPresent());
    }

    @Test
    public void testSomethingElseAgain() throws JsonProcessingException {
        final SensorViewFactory factory = new SensorViewFactory(new ScaleFactory());
        SenseOneFiveExtraData extra = SenseOneFiveExtraData.create(
                0,0,0,"",0,0,0,0
        );
        final DeviceData data = new DeviceData.Builder()
                .withExternalDeviceId("yo")
                .withAccountId(999L)
                .withDateTimeUTC(DateTime.now(DateTimeZone.UTC))
                .withOffsetMillis(0)
                .withExtraSensorData(extra)
                .build();
        Optional<SensorView> view = factory.from(Sensor.CO2, CurrentRoomState.empty(true), data);
        assertTrue("view is present", view.isPresent());
        mapper.writeValueAsString(view.get());
    }
}
