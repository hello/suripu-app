package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.hello.suripu.app.sensors.scales.TemperatureScale;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.roomstate.Condition;
import com.hello.suripu.core.roomstate.CurrentRoomState;
import com.hello.suripu.core.sense.data.SenseOneFiveExtraData;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SensorViewFactoryTest {

    final private ObjectMapper mapper = new ObjectMapper();
    final private ScaleFactory scaleFactory = new ScaleFactory();
    final SensorViewFactory factory = SensorViewFactory.build(scaleFactory);

    @Test
    public void testNoDeviceData() {
        Optional<SensorView> view = factory.from(Sensor.CO2, CurrentRoomState.empty(true));
        assertFalse("view is present", view.isPresent());
    }

    @Test
    public void testNullExtraData() {
        final DeviceData data = new DeviceData.Builder()
                .withExternalDeviceId("yo")
                .withAccountId(999L)
                .withDateTimeUTC(DateTime.now(DateTimeZone.UTC))
                .withOffsetMillis(0)
                .withExtraSensorData(null)
                .build();
        Optional<SensorView> view = factory.from(Sensor.CO2, CurrentRoomState.empty(true), data, DateTime.now(DateTimeZone.UTC));
        assertFalse("view is present", view.isPresent());
    }

    @Test
    public void testNoExtraData() {
        final DeviceData dataNoExtra = new DeviceData.Builder()
                .withExternalDeviceId("yo")
                .withAccountId(999L)
                .withDateTimeUTC(DateTime.now(DateTimeZone.UTC))
                .withOffsetMillis(0)
                .build();

        final Optional<SensorView> view = factory.from(Sensor.CO2, CurrentRoomState.empty(true), dataNoExtra, DateTime.now(DateTimeZone.UTC));
        assertFalse("view is present", view.isPresent());
    }

    @Test
    public void testWithExtra() throws JsonProcessingException {
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
        final List<Sensor> sensors = Lists.newArrayList(Sensor.CO2, Sensor.TVOC, Sensor.UV);
        for(final Sensor sensor : sensors) {
            final Optional<SensorView> view = factory.from(Sensor.CO2, CurrentRoomState.empty(true), data, DateTime.now(DateTimeZone.UTC));
            assertTrue(String.format("%s view is present", sensor.name()), view.isPresent());
            mapper.writeValueAsString(view.get());
        }
    }

    @Test
    public void testNullValues() {
        final SensorState state = SensorViewFactory.fromScale(null, new TemperatureScale());
        assertEquals("conditions should be equal", Condition.UNKNOWN, state.condition);
    }

    @Test
    public void testRange() {
        final ScaleInterval noLowerBound = new ScaleInterval("name", "message", null, 10f, Condition.UNKNOWN);
        assertTrue("no lower bound", SensorViewFactory.inRange(-10f, noLowerBound));

        final ScaleInterval noUpperBound = new ScaleInterval("name", "message", 0f, null, Condition.UNKNOWN);
        assertTrue("no upper bound", SensorViewFactory.inRange(10f,  noUpperBound));

        final ScaleInterval inRange = new ScaleInterval("name", "message", 0f, 5f, Condition.UNKNOWN);
        assertTrue("in range", SensorViewFactory.inRange(2f,  inRange));
    }
}
