package com.hello.suripu.app.sensors;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hello.suripu.app.sensors.converters.SensorQueryParameters;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.KeyStore;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.firmware.HardwareVersion;
import com.hello.suripu.core.models.AllSensorSampleList;
import com.hello.suripu.core.models.Calibration;
import com.hello.suripu.core.models.Device;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.DeviceKeyStoreRecord;
import com.hello.suripu.core.models.Sample;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.roomstate.Condition;
import com.hello.suripu.core.roomstate.CurrentRoomState;
import com.hello.suripu.core.util.RoomConditionUtil;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                        Sensor.CO2, Sensor.TVOC, Sensor.UV, Sensor.PRESSURE
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
            return new SensorResponse(SensorStatus.NO_SENSE, Lists.newArrayList(), Condition.UNKNOWN);
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
            final List<SensorView> views = availableSensors.get(hardwareVersion)
                    .stream()
                    .flatMap(s -> streamopt(sensorViewFactory.tooOld(s))) // remove optional responses
                    .collect(Collectors.toList());
            LOGGER.warn("status=no-data account_id={} sense_id={}", accountId, senseId);
            return SensorResponse.noData(views);
        }

        final Optional<Device.Color> colorOptional = senseColorDAO.getColorForSense(senseId);
        final Device.Color color = colorOptional.or(Device.Color.BLACK);
        //default -- return the usual
        final DeviceData deviceData = data.get();

        LOGGER.debug("Last device data in db = {}", deviceData);

        final CurrentRoomState roomState = CurrentRoomState.fromDeviceData(deviceData, DateTime.now(), 15, "c", calibrationOptional, (float) 35);
        final CurrentRoomState roomStateWithDust = roomState.withDust(calibrationOptional.isPresent());
        final List<SensorView> views = toView(
                availableSensors.get(hardwareVersion),
                sensorViewFactory,
                roomStateWithDust,
                deviceData,
                new DateTime(DateTimeZone.UTC),
                color
        );
        final Condition condition = RoomConditionUtil.getGeneralRoomConditionV2(roomStateWithDust, calibrationOptional.isPresent());
        return new SensorResponse(SensorStatus.OK, views, condition);
    }

    public static List<SensorView> toView(
            final List<Sensor> sensors,
            final SensorViewFactory sensorViewFactory,
            final CurrentRoomState roomState,
            final DeviceData deviceData,
            final DateTime now,
            final Device.Color color) {

        return sensors.stream()
                .flatMap(s -> streamopt( // remove optional responses
                        sensorViewFactory.from(new SensorViewQuery(s, roomState, deviceData, now,color))))
                .collect(Collectors.toList());
    }

    public static List<X> extractTimestamps(final AllSensorSampleList timeSeries, List<Sensor> sensors) {
        if(timeSeries.isEmpty()) {
            return new ArrayList<>();
        }

        // Bug right here.
        final Sensor s = sensors.get(0);
        return timeSeries
                .get(s)
                .stream()
                .map(sample -> new X(sample.dateTime, sample.offsetMillis))
                .collect(Collectors.toList());
    }

    /**
     * Fetch data in batch for given account, with sensors specified in request
     */
    public BatchQueryResponse data(final Long accountId, final BatchQuery query) {

        LOGGER.debug("account_id={} query={}", accountId, query);

        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);
        if(!deviceIdPair.isPresent()) {
            return BatchQueryResponse.noSense();
        }

        final String senseId = deviceIdPair.get().externalDeviceId;
        final Optional<Device.Color> color = senseColorDAO.getColorForSense(senseId);
        final Optional<Calibration> calibrationOptional = calibrationDAO.getStrict(senseId);
        final Optional<DeviceKeyStoreRecord> record = keyStore.getKeyStoreRecord(senseId);
        if(!record.isPresent()) {
            return BatchQueryResponse.noSense();
        }

        final List<Sensor> sensors = availableSensors.get(record.get().hardwareVersion);
        final SensorQueryParameters queryParameters = SensorQueryParameters.from(query.scope(), DateTime.now(DateTimeZone.UTC));
        final AllSensorSampleList timeSeries = deviceDataDAODynamoDB.generateTimeSeriesByUTCTimeAllSensors(
                queryParameters.start().getMillis(), queryParameters.end().getMillis(), accountId, senseId,
                queryParameters.slotDuration(), -1, color, calibrationOptional, true);

        if (timeSeries.isEmpty()) {
            return BatchQueryResponse.noData();
        }

        return convert(timeSeries, query, sensors);
    }

    public static BatchQueryResponse convert(final AllSensorSampleList timeSeries, final BatchQuery query, final List<Sensor> availableSensors) {
        final Map<Sensor, SensorData> map = Maps.newHashMap();
        for(final Sensor sensor: query.sensors()) {
            if(availableSensors.contains(sensor)) {
                final List<Sample> samples = timeSeries.get(sensor);
                if(samples != null && !samples.isEmpty()) {
                    final SensorData sensorData = SensorData.from(samples);
                    map.put(sensor, sensorData);
                } else {
                    LOGGER.warn("action=get-sensor-data sensor={} result=null_or_empty", sensor);
                    LOGGER.warn("samples={}", samples);
                }

            } else {
                LOGGER.warn("action=get-sensor-data sensor={} result=missing", sensor);
            }
        }
        final List<X> timestamps = extractTimestamps(timeSeries, availableSensors);
        return BatchQueryResponse.ok(map, timestamps);
    }


}
