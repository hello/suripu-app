package com.hello.suripu.app.v2;

import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.app.sensors.SensorResponse;
import com.hello.suripu.app.sensors.SensorViewLogic;
import com.hello.suripu.app.sensors.SensorsDataRequest;
import com.hello.suripu.app.sensors.SensorsDataResponse;
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

@Path("/v2/sensors")
public class SensorsResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensorsResource.class);

    private final SensorViewLogic viewLogic;

    public SensorsResource(final SensorViewLogic viewLogic) {
        this.viewLogic = viewLogic;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ScopesAllowed({OAuthScope.SENSORS_BASIC})
    @Timed
    public SensorResponse list(@Auth final AccessToken token) {
<<<<<<< 2150c4628d4344ac74062912aa617e2b66d22ce0
        LOGGER.debug("action=list-sensors account_id={}", token.accountId);
        final SensorResponse response = viewLogic.list(token.accountId, DateTime.now(DateTimeZone.UTC));
        LOGGER.debug("action=list-sensors account_id={} sensors={}", token.accountId, response.availableSensors());
=======

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
                "Temperature", Sensor.TEMPERATURE, SensorUnit.CELCIUS, roomState.temperature.value,
                "some clever message", roomState.temperature.condition, tempScale
        );
        final SensorView humidity = new SensorView(
                "Humidity", Sensor.HUMIDITY, SensorUnit.PERCENT, roomState.humidity.value, roomState.humidity.message, roomState.humidity.condition, tempScale);

        final SensorView sound = new SensorView(
                "Sound", Sensor.SOUND, SensorUnit.DB, roomState.sound.value, roomState.sound.message, roomState.sound.condition, tempScale);

        final SensorResponse response = new SensorResponse(
                SensorStatus.OK, Lists.newArrayList(temperature, humidity, sound)
        );
>>>>>>> Add value to sensor view
        return response;
    }

    @POST
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public SensorsDataResponse data(@Auth final AccessToken token, @Valid final SensorsDataRequest request) {
        LOGGER.debug("action=get-sensors-data account_id={}", token.accountId);
        final SensorsDataResponse response = viewLogic.data(token.accountId, request);
        LOGGER.debug("action=get-sensors-data account_id={} sensors={}", token.accountId, response.sensors().keySet());
        return response;
    }
}