package com.hello.suripu.app.v2;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hello.suripu.app.sensors.Scale;
import com.hello.suripu.app.sensors.SensorData;
import com.hello.suripu.app.sensors.SensorQuery;
import com.hello.suripu.app.sensors.SensorResponse;
import com.hello.suripu.app.sensors.SensorStatus;
import com.hello.suripu.app.sensors.SensorUnit;
import com.hello.suripu.app.sensors.SensorView;
import com.hello.suripu.app.sensors.SensorsDataRequest;
import com.hello.suripu.app.sensors.SensorsDataResponse;
import com.hello.suripu.app.sensors.X;
import com.hello.suripu.app.sensors.converters.SensorQueryParameters;
import com.hello.suripu.app.sensors.scales.TemperatureScale;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.firmware.HardwareVersion;
import com.hello.suripu.core.models.AllSensorSampleList;
import com.hello.suripu.core.models.Calibration;
import com.hello.suripu.core.models.CurrentRoomState;
import com.hello.suripu.core.models.Device;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/v2/sensors")
public class SensorsResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensorsResource.class);

    private final DeviceDataDAODynamoDB deviceDataDAODynamoDB;
    private final DeviceDAO deviceDAO;
    private final SenseColorDAO senseColorDAO;
    private final CalibrationDAO calibrationDAO;

    private static ImmutableMap<HardwareVersion, Set<Sensor>> availableSensors;
    static {
        final Map<HardwareVersion, Set<Sensor>> temp = new HashMap<>();
        final Set<Sensor> senseOneSensors = Sets.newHashSet(
                Sensor.TEMPERATURE, Sensor.HUMIDITY, Sensor.LIGHT, Sensor.PARTICULATES, Sensor.SOUND
        );
        final Set<Sensor> senseOneFiveSensors = Sets.newHashSet(senseOneSensors);
//        senseOneFiveSensors.addAll(Sets.newHashSet());
        temp.put(HardwareVersion.SENSE_ONE, senseOneSensors);
        temp.put(HardwareVersion.SENSE_ONE_FIVE, senseOneFiveSensors);
    }
    public SensorsResource(DeviceDataDAODynamoDB deviceDataDAODynamoDB, DeviceDAO deviceDAO, SenseColorDAO senseColorDAO, CalibrationDAO calibrationDAO) {
        this.deviceDataDAODynamoDB = deviceDataDAODynamoDB;
        this.deviceDAO = deviceDAO;
        this.senseColorDAO = senseColorDAO;
        this.calibrationDAO = calibrationDAO;
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ScopesAllowed({OAuthScope.SENSORS_BASIC})
    @Timed
    public SensorResponse list(@Auth final AccessToken token) {

        final Long accountId = token.accountId;
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);
        if(!deviceIdPair.isPresent()) {
            return new SensorResponse(SensorStatus.NO_SENSE, Lists.newArrayList());
        }


        final DateTime maxDT = DateTime.now(DateTimeZone.UTC).plusMinutes(2);
        final DateTime minDT = DateTime.now(DateTimeZone.UTC).minusMinutes(30);
        final Optional<DeviceData> data = deviceDataDAODynamoDB.getMostRecent(
                token.accountId, deviceIdPair.get().externalDeviceId, maxDT, minDT);

        if(!data.isPresent()) {
            return new SensorResponse(SensorStatus.WAITING_FOR_DATA, Lists.newArrayList());
        }

        final Optional<Device.Color> colorOptional = senseColorDAO.getColorForSense(deviceIdPair.get().externalDeviceId);

        //default -- return the usual
        final DeviceData deviceData = data.get().withCalibratedLight(colorOptional);

        LOGGER.debug("Last device data in db = {}", deviceData);

        final CurrentRoomState roomState = CurrentRoomState.fromDeviceData(deviceData, DateTime.now(), 15, "c", Optional.absent(), (float) 35);

        final Scale tempScale = new TemperatureScale();
        final SensorView temperature = new SensorView(
                "Temperature", Sensor.TEMPERATURE, SensorUnit.CELCIUS,
                "some clever message", roomState.temperature.condition, tempScale
        );
        final SensorView humidity = new SensorView(
                "Humidity", Sensor.HUMIDITY, SensorUnit.PERCENT, roomState.humidity.message, roomState.humidity.condition, tempScale);

        final SensorView sound = new SensorView(
                "Sound", Sensor.SOUND, SensorUnit.DB, roomState.sound.message, roomState.sound.condition, tempScale);

        final SensorResponse response = new SensorResponse(
                SensorStatus.OK, Lists.newArrayList(temperature, humidity, sound)
        );
        return response;
    }


    @POST
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public SensorsDataResponse data(@Auth final AccessToken token, @Valid final SensorsDataRequest request) {

        final Long accountId = token.accountId;
        // get latest device_id connected to this account
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);
        if(!deviceIdPair.isPresent()) {
            return SensorsDataResponse.noSense();
        }

        final Optional<Device.Color> color = senseColorDAO.getColorForSense(deviceIdPair.get().externalDeviceId);


        final Optional<Calibration> calibrationOptional = calibrationDAO.getStrict(deviceIdPair.get().externalDeviceId);
        final Map<Sensor, SensorData> map = Maps.newHashMap();

        final SensorQueryParameters queryParameters = SensorQueryParameters.from(request.queries().get(0).scope(), DateTime.now(DateTimeZone.UTC));
        final AllSensorSampleList timeSeries = deviceDataDAODynamoDB.generateTimeSeriesByUTCTimeAllSensors(
                queryParameters.start().getMillis(), queryParameters.end().getMillis(), accountId, deviceIdPair.get().externalDeviceId,
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
}
