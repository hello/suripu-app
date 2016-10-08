package com.hello.suripu.app.sensors;

import com.google.common.collect.Lists;
import com.hello.suripu.core.models.AllSensorSampleList;
import com.hello.suripu.core.models.Device;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.Sample;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.roomstate.CurrentRoomState;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;

public class SensorViewLogicTest {

    @Test
    public void testSerializeWhatever() {
        final List<Sensor> sensors = new ArrayList<>();
        final List<SensorView> views = SensorViewLogic.toView(
                sensors,
                SensorViewFactory.build(new ScaleFactory()),
                CurrentRoomState.empty(true),
                new DeviceData.Builder()
                        .withExternalDeviceId("yo")
                        .withAccountId(999L)
                        .withDateTimeUTC(DateTime.now(DateTimeZone.UTC))
                        .withOffsetMillis(0)
                        .withExtraSensorData(null)
                        .build(),
                DateTime.now(),
                Device.Color.BLACK
        );

        assertTrue("empty view", views.isEmpty());

    }

    @Test
    public void testExtractTimestamps() {
        final AllSensorSampleList allSensorSampleList = new AllSensorSampleList();
//        allSensorSampleList.add(Sensor.CO2, Lists.newArrayList(new Sample(0,1f,0)));

        final List<X> x = SensorViewLogic.extractTimestamps(allSensorSampleList, new ArrayList<>());
        assertEquals("timestamps", 0, x.size());
    }

    @Test
    public void testConvert() {
        final AllSensorSampleList allSensorSampleList = new AllSensorSampleList();
        final BatchQuery query = BatchQuery.create(
                QueryScope.LAST_3H_5_MINUTE,
                Lists.newArrayList(Sensor.CO2)
        );
//        allSensorSampleList.add(Sensor.CO2, Lists.newArrayList(new Sample(0,1f,0)));
        final List<Sensor> sensors = Lists.newArrayList(Sensor.CO2);
        final BatchQueryResponse response = SensorViewLogic.convert(allSensorSampleList, query, sensors);
        assertTrue("should be empty", response.sensors().isEmpty());
        assertThat("same size", response.sensors().size(), equalTo(response.timestamps().size()));
    }

    @Test
    public void testConvertNotEmpty() {
        final AllSensorSampleList allSensorSampleList = new AllSensorSampleList();
        final BatchQuery query = BatchQuery.create(
                QueryScope.LAST_3H_5_MINUTE,
                Lists.newArrayList(Sensor.CO2)
        );
        allSensorSampleList.add(Sensor.CO2, Lists.newArrayList(new Sample(0,1f,0)));
        final List<Sensor> sensors = Lists.newArrayList(Sensor.CO2);
        final BatchQueryResponse response = SensorViewLogic.convert(allSensorSampleList, query, sensors);
        assertFalse("should not be empty", response.sensors().isEmpty());
        assertThat("same size", response.sensors().size(), equalTo(response.timestamps().size()));
    }
}
