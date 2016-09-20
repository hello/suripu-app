package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.roomstate.CurrentRoomState;
import com.hello.suripu.core.sense.data.SenseOneFiveExtraData;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SensorViewFactoryTest {

    final private ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testNoDeviceData() {
        final SensorViewFactory factory = new SensorViewFactory(new ScaleFactory());
        Optional<SensorView> view = factory.from(Sensor.CO2, CurrentRoomState.empty(true));
        assertFalse("view is present", view.isPresent());
    }

    @Test
    public void testNullExtraData() {
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
    public void testWithExtra() throws JsonProcessingException {
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
        final List<Sensor> sensors = Lists.newArrayList(Sensor.CO2, Sensor.TVOC, Sensor.UV);
        for(final Sensor sensor : sensors) {
            final Optional<SensorView> view = factory.from(Sensor.CO2, CurrentRoomState.empty(true), data);
            assertTrue(String.format("%s view is present", sensor.name()), view.isPresent());
            mapper.writeValueAsString(view.get());
        }
    }

    @Test
    public void testMissingSensors() {

        final DeviceData dataNoExtra = new DeviceData.Builder()
                .withExternalDeviceId("yo")
                .withAccountId(999L)
                .withDateTimeUTC(DateTime.now(DateTimeZone.UTC))
                .withOffsetMillis(0)
                .build();

        final SensorViewFactory factory = new SensorViewFactory(new ScaleFactory());
        final Optional<SensorView> view = factory.from(Sensor.CO2, CurrentRoomState.empty(true), dataNoExtra);
        assertFalse("view is present", view.isPresent());
    }
}
