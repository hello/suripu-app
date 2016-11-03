package com.hello.suripu.app.sensors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.hello.suripu.app.sensors.scales.PressureScale;
import com.hello.suripu.app.sensors.scales.TemperatureScale;
import com.hello.suripu.core.models.CalibratedDeviceData;
import com.hello.suripu.core.models.Device;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.roomstate.Condition;
import com.hello.suripu.core.roomstate.CurrentRoomState;
import com.hello.suripu.core.sense.data.SenseOneFiveExtraData;
import com.hello.suripu.core.util.calibration.SenseOneFiveDataConversion;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

public class SensorViewFactoryTest {

    final private ObjectMapper mapper = new ObjectMapper();
    final private ScaleFactory scaleFactory = new ScaleFactory();
    final SensorViewFactory factory = SensorViewFactory.build(scaleFactory);

    @Test
    public void testNullExtraData() {
        final DeviceData data = new DeviceData.Builder()
                .withExternalDeviceId("yo")
                .withAccountId(999L)
                .withDateTimeUTC(DateTime.now(DateTimeZone.UTC))
                .withOffsetMillis(0)
                .withExtraSensorData(null)
                .build();
        final SensorViewQuery query = new SensorViewQuery(Sensor.CO2, CurrentRoomState.empty(true), data, Optional.absent(), DateTime.now(DateTimeZone.UTC), Device.Color.BLACK);
        final Optional<SensorView> view = factory.from(query);
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

        final SensorViewQuery query = new SensorViewQuery(Sensor.CO2, CurrentRoomState.empty(true), dataNoExtra, Optional.absent(),DateTime.now(DateTimeZone.UTC), Device.Color.BLACK);
        final Optional<SensorView> view = factory.from(query);
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
            final SensorViewQuery query = new SensorViewQuery(Sensor.CO2, CurrentRoomState.empty(true), data, Optional.absent(), DateTime.now(DateTimeZone.UTC), Device.Color.BLACK);
            final Optional<SensorView> view = factory.from(query);
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

    @Test
    public void testPressure() {
        final int pressure = 25916808;

        final SenseOneFiveExtraData extra = SenseOneFiveExtraData.create(
                pressure,0,0,"",0,0,0,0
        );
        final DeviceData data = new DeviceData.Builder()
                .withExternalDeviceId("yo")
                .withAccountId(999L)
                .withDateTimeUTC(DateTime.now(DateTimeZone.UTC))
                .withOffsetMillis(0)
                .withExtraSensorData(extra)
                .build();

        final SensorViewQuery query = new SensorViewQuery(Sensor.PRESSURE, CurrentRoomState.empty(true), data, Optional.absent(), DateTime.now(DateTimeZone.UTC), Device.Color.BLACK);
        final Optional<SensorView> view = factory.from(query);

        final CalibratedDeviceData calibratedDeviceData = new CalibratedDeviceData(data, Device.Color.BLACK, Optional.absent());

        assertTrue("Pressure is present", view.isPresent());
        final SensorView pressureView = view.get();
        assertEquals("Condition is IDEAL", pressureView.condition(), Condition.IDEAL);
        assertThat("Condition is IDEAL", calibratedDeviceData.pressure(), equalTo(pressureView.value()));
    }

    public static class PressureCase {
        public final float diff;
        public final Condition expected;
        public PressureCase(float diff, Condition expected) {
            this.diff = diff;
            this.expected = expected;
        }
    }
    @Test
    public void betterPressureTest() {


        final float currentPressure = SenseOneFiveDataConversion.convertRawToMilliBar(25916808);
        final PressureScale pressureScale = new PressureScale(currentPressure);

        final List<PressureCase> cases = Lists.newArrayList(
                new PressureCase(0f, Condition.IDEAL),
                new PressureCase(19.9f, Condition.IDEAL),
                new PressureCase(20.1f, Condition.WARNING),
                new PressureCase(-20.1f, Condition.WARNING),
                new PressureCase(-50f, Condition.ALERT),
                new PressureCase(100, Condition.ALERT)
        );

        for(PressureCase pressureCase : cases) {
            final SensorState state = SensorViewFactory.pressureState(25916808, pressureCase.diff, pressureScale);
            assertEquals(state.condition, pressureCase.expected);
        }
    }
}
