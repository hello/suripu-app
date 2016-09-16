package com.hello.suripu.app.sensors;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hello.suripu.app.sensors.converters.SensorQueryParameters;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.KeyStore;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.firmware.HardwareVersion;
import com.hello.suripu.core.models.AllSensorSampleList;
import com.hello.suripu.core.models.Calibration;
import com.hello.suripu.core.roomstate.CurrentRoomState;
import com.hello.suripu.core.models.Device;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.DeviceKeyStoreRecord;
import com.hello.suripu.core.models.Sensor;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SensorViewLogic {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensorViewLogic.class);

    private final DeviceDataDAODynamoDB deviceDataDAODynamoDB;
    private final KeyStore keyStore;
    private final DeviceDAO deviceDAO;
    private final SenseColorDAO senseColorDAO;
    private final CalibrationDAO calibrationDAO;
    private final SensorViewFactory sensorViewFactory;

    private static ImmutableMap<HardwareVersion, List<Sensor>> availableSensors;
    static {
        final Map<HardwareVersion, List<Sensor>> temp = new HashMap<>();
        final List<Sensor> senseOneSensors = Lists.newArrayList(
                Sensor.TEMPERATURE, Sensor.HUMIDITY, Sensor.LIGHT, Sensor.PARTICULATES, Sensor.SOUND
        );

        final List<Sensor> senseOneFiveSensors = Lists.newArrayList(senseOneSensors);
        senseOneFiveSensors.addAll(
                Lists.newArrayList(
                        Sensor.CO2, Sensor.TVOC, Sensor.UV
                ));

        temp.put(HardwareVersion.SENSE_ONE, senseOneSensors);
        temp.put(HardwareVersion.SENSE_ONE_FIVE, senseOneFiveSensors);
        availableSensors = ImmutableMap.copyOf(temp);
    }

    /**
     * Turns an Optional<T> into a Stream<T> of length zero or one depending upon
     * whether a value is present.
     */
    static <T> Stream<T> streamopt(Optional<T> opt) {
        if (opt.isPresent())
            return Stream.of(opt.get());
        else
            return Stream.empty();
    }


    public SensorViewLogic(DeviceDataDAODynamoDB deviceDataDAODynamoDB, KeyStore keyStore, DeviceDAO deviceDAO, SenseColorDAO senseColorDAO, CalibrationDAO calibrationDAO, SensorViewFactory sensorViewFactory) {
        this.deviceDataDAODynamoDB = deviceDataDAODynamoDB;
        this.keyStore = keyStore;
        this.deviceDAO = deviceDAO;
        this.senseColorDAO = senseColorDAO;
        this.calibrationDAO = calibrationDAO;
        this.sensorViewFactory = sensorViewFactory;
    }

    public SensorResponse list(final Long accountId, final DateTime asOfUTC) {
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);
        if(!deviceIdPair.isPresent()) {
            return new SensorResponse(SensorStatus.NO_SENSE, Lists.newArrayList());
        }

        final String senseId = deviceIdPair.get().externalDeviceId;

        final DateTime maxDT = asOfUTC.plusMinutes(2);
        final DateTime minDT = asOfUTC.minusMinutes(30);
        final Optional<DeviceData> data = deviceDataDAODynamoDB.getMostRecent(
                accountId, senseId, maxDT, minDT);


        final Optional<DeviceKeyStoreRecord> record = keyStore.getKeyStoreRecord(senseId);
        final HardwareVersion hardwareVersion = record.isPresent() ? record.get().hardwareVersion : HardwareVersion.SENSE_ONE;

        final Optional<Calibration> calibrationOptional = calibrationDAO.get(senseId);

        if(!data.isPresent()) {
            final CurrentRoomState roomState = CurrentRoomState.empty(calibrationOptional.isPresent());
            final List<SensorView> views = availableSensors.get(hardwareVersion)
                    .stream()
                    .flatMap(s -> streamopt(sensorViewFactory.from(s, roomState))) // remove optional responses
                    .collect(Collectors.toList());

            return SensorResponse.noData(views);
        }

        final Optional<Device.Color> colorOptional = senseColorDAO.getColorForSense(senseId);

        //default -- return the usual
        final DeviceData deviceData = data.get().withCalibratedLight(colorOptional);

        LOGGER.debug("Last device data in db = {}", deviceData);

        final CurrentRoomState roomState = CurrentRoomState.fromDeviceData(deviceData, DateTime.now(), 15, "c", calibrationOptional, (float) 35);
        final CurrentRoomState withDust = roomState.withDust(calibrationOptional.isPresent());
        final List<SensorView> views = availableSensors.get(hardwareVersion)
                .stream()
                .flatMap(s -> streamopt(sensorViewFactory.from(s, withDust, deviceData))) // remove optional responses
                .collect(Collectors.toList());

        return new SensorResponse(SensorStatus.OK, views);
    }

    public static List<X> extractTimestamps(AllSensorSampleList timeSeries) {
        if(timeSeries.isEmpty()) {
            return new ArrayList<>();
        }
        final Sensor s = timeSeries.getAvailableSensors().get(0);
        return timeSeries
                .get(s)
                .stream()
                .map(sample -> new X(sample.dateTime, sample.offsetMillis))
                .collect(Collectors.toList());
    }

    /**
     * Fetch data in batch for given account, with sensors specified in request
     */
    public SensorsDataResponse data(final Long accountId, final SensorsDataRequest request) {

        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);
        if(!deviceIdPair.isPresent()) {
            return SensorsDataResponse.noSense();
        }

        final String senseId = deviceIdPair.get().externalDeviceId;
        final Optional<Device.Color> color = senseColorDAO.getColorForSense(senseId);
        final Optional<Calibration> calibrationOptional = calibrationDAO.getStrict(senseId);
        final Map<Sensor, SensorData> map = Maps.newHashMap();

        final SensorQueryParameters queryParameters = SensorQueryParameters.from(request.queries().get(0).scope(), DateTime.now(DateTimeZone.UTC));
        final AllSensorSampleList timeSeries = deviceDataDAODynamoDB.generateTimeSeriesByUTCTimeAllSensors(
                queryParameters.start().getMillis(), queryParameters.end().getMillis(), accountId, senseId,
                queryParameters.slotDuration(), -1, color, calibrationOptional, true);

        if (timeSeries.isEmpty()) {
            return SensorsDataResponse.noData();
        }

        final Set<Sensor> sensorValues = Sets.newHashSet(timeSeries.getAvailableSensors());
        for(final SensorQuery query : request.queries()) {
            if(sensorValues.contains(query.type())) {

                final SensorData sensorData = SensorData.from(timeSeries.get(query.type()));
                map.put(query.type(), sensorData);
            } else {
                LOGGER.warn("action=get-sensor-data sensor={} result=missing", query.type());
            }
        }

        return SensorsDataResponse.ok(map, extractTimestamps(timeSeries));
    }
}
