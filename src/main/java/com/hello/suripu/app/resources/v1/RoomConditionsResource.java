package com.hello.suripu.app.resources.v1;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.colors.SenseColorDAO;
import com.hello.suripu.core.models.AllSensorSampleList;
import com.hello.suripu.core.models.Calibration;
import com.hello.suripu.core.roomstate.CurrentRoomState;
import com.hello.suripu.core.models.Device;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.DeviceData;
import com.hello.suripu.core.models.Sample;
import com.hello.suripu.core.models.Sensor;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.util.SmoothSample;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import com.hello.suripu.coredropwizard.resources.BaseResource;
import com.librato.rollout.RolloutClient;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Path("/v1/room")
public class RoomConditionsResource extends BaseResource {

    @Inject
    RolloutClient feature;

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomConditionsResource.class);
    private final static ImmutableSet<String> hiddenSensors = ImmutableSet.copyOf(Sets.newHashSet("light_variance", "light_peakiness", "dust_min", "dust_max", "dust_variance"));

    private static final Float NO_SOUND_CAPTURED_DB = (float) 0;    // The Sound value when Sense didn't capture audio
    private static final Float NO_SOUND_FILL_VALUE_DB = (float) 35; // Replace with this value when Sense isn't capturing audio

    private final DeviceDataDAODynamoDB deviceDataDAODynamoDB;
    private final DeviceDAO deviceDAO;
    private final long allowedRangeInSeconds;
    private final SenseColorDAO senseColorDAO;
    private final CalibrationDAO calibrationDAO;


    public RoomConditionsResource(
            final DeviceDataDAODynamoDB deviceDataDAODynamoDB, final DeviceDAO deviceDAO,
            final long allowedRangeInSeconds,final SenseColorDAO senseColorDAO,
            final CalibrationDAO calibrationDAO) {
        this.deviceDataDAODynamoDB = deviceDataDAODynamoDB;
        this.deviceDAO = deviceDAO;
        this.allowedRangeInSeconds = allowedRangeInSeconds;
        this.senseColorDAO = senseColorDAO;
        this.calibrationDAO = calibrationDAO;
    }


    @ScopesAllowed({OAuthScope.SENSORS_BASIC})
    @Timed
    @GET
    @Path("/current")
    @Produces(MediaType.APPLICATION_JSON)
    public CurrentRoomState current(@Auth final AccessToken token,
                                    @DefaultValue("c") @QueryParam("temp_unit") final String unit) {


        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(token.accountId);

        if(!deviceIdPair.isPresent()) {
            LOGGER.warn("Did not find any device_id for account_id = {}", token.accountId);
            return CurrentRoomState.empty(false); // at this stage we don't have a Sense id, so we can't use FF.
        }

        final Optional<Calibration> calibrationOptional = getCalibrationStrict(deviceIdPair.get().externalDeviceId);
        final Boolean hasDust = calibrationOptional.isPresent();

        if(isSensorsViewUnavailable(token.accountId)) {
            LOGGER.warn("SENSORS VIEW UNAVAILABLE FOR USER {}", token.accountId);
            return CurrentRoomState.empty(hasDust);
        }

        Integer thresholdInMinutes = 15;
        Integer mostRecentLookBackMinutes = 30;
        if (this.hasDelayCurrentRoomStateThreshold(token.accountId)) {
            thresholdInMinutes = 120;
            mostRecentLookBackMinutes = 120;
        }

        final DateTime maxDT = DateTime.now(DateTimeZone.UTC).plusMinutes(2);
        final DateTime minDT = DateTime.now(DateTimeZone.UTC).minusMinutes(mostRecentLookBackMinutes);
        final Optional<DeviceData> data = deviceDataDAODynamoDB.getMostRecent(
                token.accountId, deviceIdPair.get().externalDeviceId, maxDT, minDT);


        if(!data.isPresent()) {
            return CurrentRoomState.empty(hasDust);
        }

        //default -- return the usual
        DeviceData deviceData = data.get();

        if (this.hasColorCompensationEnabled(token.accountId)) {
            //color compensation?  get the color
            final Optional<Device.Color> color = senseColorDAO.getColorForSense(deviceIdPair.get().externalDeviceId);
            deviceData = data.get().withCalibratedLight(color); //and compensate 
        }

        LOGGER.debug("Last device data in db = {}", deviceData);

        final CurrentRoomState roomState = CurrentRoomState.fromDeviceData(deviceData, DateTime.now(), thresholdInMinutes, unit, calibrationOptional, NO_SOUND_FILL_VALUE_DB);

        return roomState.withDust(hasDust);
    }


    @ScopesAllowed({OAuthScope.SENSORS_BASIC})
    @Timed
    @GET
    @Path("/{sensor}/week")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Sample> getLastWeek(
            @Auth final AccessToken accessToken,
            @PathParam("sensor") final String sensorName,
            @QueryParam("from") Long queryEndTimestampUTC) { // utc or local???

        if (hiddenSensors.contains(sensorName)) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }
        final Sensor sensor = nameToSensor(sensorName);
        return retrieveWeekData(accessToken.accountId, sensor, queryEndTimestampUTC);
    }


    public static Sensor nameToSensor(String sensorName) {
        for(Sensor sensor: Sensor.values()) {
            if(sensor.toString().equalsIgnoreCase(sensorName)) {
                return sensor;
            }
        }
        throw new IllegalArgumentException("invalid sensor name");
    }

    // TODO this should be deprecated
    @ScopesAllowed({OAuthScope.SENSORS_BASIC})
    @Timed
    @GET
    @Path("/all_sensors/week")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<Sensor, List<Sample>> getAllSensorsLastWeek(
            @Auth final AccessToken accessToken,
            @QueryParam("from_utc") Long queryEndTimestampUTC) {
        return retrieveAllSensorsWeekData(accessToken.accountId, queryEndTimestampUTC);
    }

    /*
    * This is the correct implementation of get the last 24 hours' data
    * from the timestamp provided by the client.
     */
    @ScopesAllowed({OAuthScope.SENSORS_BASIC})
    @Timed
    @GET
    @Path("/{sensor}/24hours")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Sample> getLast24hours(
            @Auth final AccessToken accessToken,
            @PathParam("sensor") String sensorName,
            @QueryParam("from_utc") Long queryEndTimestampUTC) {

        if (hiddenSensors.contains(sensorName)) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }
        validateQueryRange(queryEndTimestampUTC, DateTime.now(), accessToken.accountId, allowedRangeInSeconds);

        final int slotDurationInMinutes = 5;
        final long queryStartTimeUTC = new DateTime(queryEndTimestampUTC, DateTimeZone.UTC).minusHours(24).getMillis();


        // get latest device_id connected to this account
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accessToken.accountId);
        if(!deviceIdPair.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        Optional<Device.Color> color = Optional.absent();
        if (this.hasColorCompensationEnabled(accessToken.accountId)) {
            color = senseColorDAO.getColorForSense(deviceIdPair.get().externalDeviceId);
        }

        final Optional<Calibration> calibrationOptional = getCalibrationStrict(deviceIdPair.get().externalDeviceId);
        final Sensor sensor = nameToSensor(sensorName);
        final List<Sample> timeSeries = deviceDataDAODynamoDB.generateTimeSeriesByUTCTime(
                queryStartTimeUTC, queryEndTimestampUTC, accessToken.accountId, deviceIdPair.get().externalDeviceId,
                slotDurationInMinutes, sensor, missingDataDefaultValue(accessToken.accountId), color, calibrationOptional,
                useAudioPeakEnergy(accessToken.accountId));

        return adjustTimeSeries(timeSeries, sensor, deviceIdPair.get().externalDeviceId);
    }

    @ScopesAllowed({OAuthScope.SENSORS_BASIC})
    @Timed
    @GET
    @Path("/all_sensors/24hours")
    @Produces(MediaType.APPLICATION_JSON)
    public  Map<Sensor, List<Sample>> getAllSensorsLast24hours(
            @Auth final AccessToken accessToken,
            @PathParam("sensor") String sensor,
            @QueryParam("from_utc") Long queryEndTimestampUTC) {

        if (hiddenSensors.contains(sensor)) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        if (isSensorsViewUnavailable(accessToken.accountId)) {
            LOGGER.warn("SENSORS VIEW UNAVAILABLE FOR USER {}", accessToken.accountId);
            return AllSensorSampleList.getEmptyData();
        }
        validateQueryRange(queryEndTimestampUTC, DateTime.now(), accessToken.accountId, allowedRangeInSeconds);

        final int slotDurationInMinutes = 5;
        final long queryStartTimeUTC = new DateTime(queryEndTimestampUTC, DateTimeZone.UTC).minusHours(24).getMillis();


        // get latest device_id connected to this account
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accessToken.accountId);
        if(!deviceIdPair.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        Optional<Device.Color> color = Optional.absent();
        if (this.hasColorCompensationEnabled(accessToken.accountId)) {
            color = senseColorDAO.getColorForSense(deviceIdPair.get().externalDeviceId);
        }

        final Optional<Calibration> calibrationOptional = getCalibrationStrict(deviceIdPair.get().externalDeviceId);

        final AllSensorSampleList sensorData = deviceDataDAODynamoDB.generateTimeSeriesByUTCTimeAllSensors(
                queryStartTimeUTC, queryEndTimestampUTC, accessToken.accountId, deviceIdPair.get().externalDeviceId, slotDurationInMinutes,
                missingDataDefaultValue(accessToken.accountId), color, calibrationOptional, useAudioPeakEnergy(accessToken.accountId));

        if (sensorData.isEmpty()) {
            return AllSensorSampleList.getEmptyData();
        }

        final AllSensorSampleList adjustedSensorData = adjustTimeSeriesAllSensors(sensorData, deviceIdPair.get().externalDeviceId);

        return getDisplayData(adjustedSensorData.getAllData(), hasCalibrationEnabled(deviceIdPair.get().externalDeviceId));
    }

    @ScopesAllowed({OAuthScope.SENSORS_BASIC})
    @Timed
    @GET
    @Path("/all_sensors/hours")
    @Produces(MediaType.APPLICATION_JSON)
    public  Map<Sensor, List<Sample>> getAllSensorsLastHours(
            @Auth final AccessToken accessToken,
            @QueryParam("quantity") Integer quantity,
            @QueryParam("from_utc") Long queryEndTimestampUTC) {

        validateQueryRange(queryEndTimestampUTC, DateTime.now(), accessToken.accountId, allowedRangeInSeconds);


        if(isSensorsViewUnavailable(accessToken.accountId)) {
            LOGGER.warn("SENSORS VIEW UNAVAILABLE FOR USER {}", accessToken.accountId);
            return AllSensorSampleList.getEmptyData();
        }


        final int slotDurationInMinutes = 5;
        final long queryStartTimeUTC = new DateTime(queryEndTimestampUTC, DateTimeZone.UTC).minusHours(quantity).getMillis();


        // get latest device_id connected to this account
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accessToken.accountId);
        if(!deviceIdPair.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        Optional<Device.Color> color = Optional.absent();
        if (this.hasColorCompensationEnabled(accessToken.accountId)) {
            color = senseColorDAO.getColorForSense(deviceIdPair.get().externalDeviceId);
        }

        final Optional<Calibration> calibrationOptional = getCalibrationStrict(deviceIdPair.get().externalDeviceId);

        final AllSensorSampleList sensorData = deviceDataDAODynamoDB.generateTimeSeriesByUTCTimeAllSensors(
                queryStartTimeUTC, queryEndTimestampUTC, accessToken.accountId, deviceIdPair.get().externalDeviceId,
                slotDurationInMinutes, missingDataDefaultValue(accessToken.accountId), color, calibrationOptional,
                useAudioPeakEnergy(accessToken.accountId));

        if (sensorData.isEmpty()) {
            return AllSensorSampleList.getEmptyData();
        }

        final AllSensorSampleList adjustedSensorData = adjustTimeSeriesAllSensors(sensorData, deviceIdPair.get().externalDeviceId);

        return getDisplayData(adjustedSensorData.getAllData(), hasCalibrationEnabled(deviceIdPair.get().externalDeviceId));
    }

    /*
    * WARNING: This implementation will not giving out the data of last 24 hours.
    * It gives the data of last DAY, which is from a certain local timestamp
    * to that timestamp plus one DAY, keep in mind that one day can be more/less than 24 hours
     */
    @ScopesAllowed({OAuthScope.SENSORS_BASIC})
    @Timed
    @GET
    @Path("/{sensor}/day")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Sample> getLastDay(
            @Auth final AccessToken accessToken,
            @PathParam("sensor") String sensorName,

            // The @QueryParam("from") should be named as @QueryParam("from_local_utc")
            // to make it explicit that the API is expecting a local time and not confuse
            // the user.
            @QueryParam("from") Long queryEndTimestampInUTC) {
        if (hiddenSensors.contains(sensorName)) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }
        final Sensor sensor = nameToSensor(sensorName);
        return retrieveDayData(accessToken.accountId, sensor, queryEndTimestampInUTC);
    }

    /*
    * WARNING: This implementation will not giving out the data of last 24 hours.
    * It gives the data of last DAY, which is from a certain local timestamp
    * to that timestamp plus one DAY, keep in mind that one day can be more/less than 24 hours
     */
    @ScopesAllowed({OAuthScope.SENSORS_BASIC})
    @Timed
    @GET
    @Path("/{sensor}/{device_name}/day")   // One DAY is not 24 hours, be careful on the naming.
    @Produces(MediaType.APPLICATION_JSON)
    public List<Sample> getLastDayDeviceName(
            @Auth final AccessToken accessToken,
            @PathParam("sensor") String sensorName,
            @PathParam("device_name") String deviceName,

            // The @QueryParam("from") should be named as @QueryParam("from_local_utc")
            // to make it explicit that the API is expecting a local time and not confuse
            // the user.
            @QueryParam("from") Long queryEndTimestampInUTC) {

        if (hiddenSensors.contains(sensorName)) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        final int slotDurationInMinutes = 5;

        /*
        * We have to minutes one day instead of 24 hours, for the same reason that we want one DAY's
        * data, instead of 24 hours.
         */
        final long queryStartTimeInUTC = new DateTime(queryEndTimestampInUTC, DateTimeZone.UTC).minusDays(1).getMillis();

        validateQueryRange(queryEndTimestampInUTC, DateTime.now(), accessToken.accountId, allowedRangeInSeconds);

        // check that accountId, deviceName pair exists
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accessToken.accountId);

        if (!deviceIdPair.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        Optional<Device.Color> color = Optional.absent();

        if (this.hasColorCompensationEnabled(accessToken.accountId)) {
            color = senseColorDAO.getColorForSense(deviceName);
        }

        final Optional<Calibration> calibrationOptional = getCalibrationStrict(deviceName);
        final Sensor sensor = nameToSensor(sensorName);
        final List<Sample> timeSeries = deviceDataDAODynamoDB.generateTimeSeriesByUTCTime(
                queryStartTimeInUTC, queryEndTimestampInUTC, accessToken.accountId, deviceIdPair.get().externalDeviceId,
                slotDurationInMinutes, sensor, missingDataDefaultValue(accessToken.accountId), color, calibrationOptional,
                useAudioPeakEnergy(accessToken.accountId));

        return adjustTimeSeries(timeSeries, sensor, deviceName);
    }

    /*
    * This is the correct implementation of get the last 24 hours' data
    * from the timestamp provided by the client.
     */
    @ScopesAllowed({OAuthScope.SENSORS_BASIC})
    @Timed
    @GET
    @Path("/{sensor}/{device_name}/24hours")   // One DAY is not 24 hours, be careful on the naming.
    @Produces(MediaType.APPLICATION_JSON)
    public List<Sample> getLast24hoursDeviceName(
            @Auth final AccessToken accessToken,
            @PathParam("sensor") String sensorName,
            @PathParam("device_name") String deviceName,
            @QueryParam("from_utc") Long queryEndTimestampUTC) {

        if (hiddenSensors.contains(sensorName)) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        validateQueryRange(queryEndTimestampUTC, DateTime.now(), accessToken.accountId, allowedRangeInSeconds);

        final int slotDurationInMinutes = 5;
        final long queryStartTimeUTC = new DateTime(queryEndTimestampUTC, DateTimeZone.UTC).minusHours(24).getMillis();

        // check that accountId, deviceName pair exists
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accessToken.accountId);

        if (!deviceIdPair.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        Optional<Device.Color> color = Optional.absent();

        if (this.hasColorCompensationEnabled(accessToken.accountId)) {
            color = senseColorDAO.getColorForSense(deviceName);
        }

        final Optional<Calibration> calibrationOptional = getCalibrationStrict(deviceName);
        final Sensor sensor = nameToSensor(sensorName);
        final List<Sample> timeSeries = deviceDataDAODynamoDB.generateTimeSeriesByUTCTime(
                queryStartTimeUTC, queryEndTimestampUTC, accessToken.accountId, deviceIdPair.get().externalDeviceId,
                slotDurationInMinutes, sensor, missingDataDefaultValue(accessToken.accountId), color, calibrationOptional,
                useAudioPeakEnergy(accessToken.accountId));


        return adjustTimeSeries(timeSeries, sensor, deviceName);
    }


    /**
     * Validates that the current request start range is within reasonable bounds
     * @param clientUtcTimestamp
     * @param nowForServer
     * @param accountId
     */
    private void validateQueryRange(final Long clientUtcTimestamp, final DateTime nowForServer, final Long accountId, final long allowedRangeInSeconds) {
        if (clientUtcTimestamp == null) {
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }

        if(Math.abs(clientUtcTimestamp - nowForServer.getMillis()) > allowedRangeInSeconds * 1000) {
            LOGGER.warn("Invalid request, {} is too far off for account_id = {}", clientUtcTimestamp, accountId);
            throw new WebApplicationException(Response.Status.BAD_REQUEST);  // This should be FORBIDDEN
        }
    }

    private List<Sample> retrieveDayData(final Long accountId, final Sensor sensor, final Long queryEndTimestampInUTC) {

        if(isSensorsViewUnavailable(accountId)) {
            LOGGER.warn("SENSORS VIEW UNAVAILABLE FOR USER {}", accountId);
            return Collections.EMPTY_LIST;
        }

        final int slotDurationInMinutes = 5;
        /*
        * We have to minutes one day instead of 24 hours, for the same reason that we want one DAY's
        * data, instead of 24 hours.
         */
        final long queryStartTimeInUTC = new DateTime(queryEndTimestampInUTC, DateTimeZone.UTC).minusDays(1).getMillis();

        validateQueryRange(queryEndTimestampInUTC,
                DateTime.now(),
                accountId,
                allowedRangeInSeconds
        );

        // get latest device_id connected to this account
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);

        if(!deviceIdPair.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        Optional<Device.Color> color = Optional.absent();

        if (this.hasColorCompensationEnabled(accountId)) {
            color = senseColorDAO.getColorForSense(deviceIdPair.get().externalDeviceId);
        }

        final Optional<Calibration> calibrationOptional = getCalibrationStrict(deviceIdPair.get().externalDeviceId);

        final List<Sample> timeSeries = deviceDataDAODynamoDB.generateTimeSeriesByUTCTime(
                queryStartTimeInUTC, queryEndTimestampInUTC, accountId, deviceIdPair.get().externalDeviceId,
                slotDurationInMinutes, sensor, missingDataDefaultValue(accountId), color, calibrationOptional,
                useAudioPeakEnergy(accountId));

        return adjustTimeSeries(timeSeries, sensor, deviceIdPair.get().externalDeviceId);

    }

    private List<Sample> retrieveWeekData(final Long accountId, final Sensor sensor, final Long queryEndTimestampInUTC) {

        if(isSensorsViewUnavailable(accountId)) {
            LOGGER.warn("SENSORS VIEW UNAVAILABLE FOR USER {}", accountId);
            return Collections.EMPTY_LIST;
        }

        final int slotDurationInMinutes = 60;
        //final int  queryDurationInHours = 24 * 7; // 7 days

        /*
        * Again, the same problem:
        * We have to minutes one week instead of 7*24 hours, for the same reason that one week can be more/less than 7 * 24 hours
         */
        final long queryStartTimeInUTC = new DateTime(queryEndTimestampInUTC, DateTimeZone.UTC).minusWeeks(1).getMillis();
        validateQueryRange(queryEndTimestampInUTC,
                DateTime.now(),
                accountId,
                allowedRangeInSeconds);

        // get latest device_id connected to this account
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);
        if(!deviceIdPair.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        Optional<Device.Color> color = Optional.absent();

        if (this.hasColorCompensationEnabled(accountId)) {
            color = senseColorDAO.getColorForSense(deviceIdPair.get().externalDeviceId);
        }

        final Optional<Calibration> calibrationOptional = getCalibrationStrict(deviceIdPair.get().externalDeviceId);

        final List<Sample> timeSeries = deviceDataDAODynamoDB.generateTimeSeriesByUTCTime(
                queryStartTimeInUTC, queryEndTimestampInUTC, accountId, deviceIdPair.get().externalDeviceId,
                slotDurationInMinutes, sensor, missingDataDefaultValue(accountId), color, calibrationOptional,
                useAudioPeakEnergy(accountId));

        return adjustTimeSeries(timeSeries, sensor, deviceIdPair.get().externalDeviceId);
    }

    private Map<Sensor, List<Sample>> retrieveAllSensorsWeekData(final Long accountId, final Long queryEndTimestampInUTC) {

        if(isSensorsViewUnavailable(accountId)) {
            LOGGER.warn("SENSORS VIEW UNAVAILABLE FOR USER {}", accountId);
            return AllSensorSampleList.getEmptyData();
        }

        final int slotDurationInMinutes = 60;
        //final int  queryDurationInHours = 24 * 7; // 7 days

        /*
        * Again, the same problem:
        * We have to minutes one week instead of 7*24 hours, for the same reason that one week can be more/less than 7 * 24 hours
         */
        final long queryStartTimeInUTC = new DateTime(queryEndTimestampInUTC, DateTimeZone.UTC).minusWeeks(1).getMillis();

        validateQueryRange(queryEndTimestampInUTC, DateTime.now(), accountId, allowedRangeInSeconds);

        // get latest device_id connected to this account
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);
        if(!deviceIdPair.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        Optional<Device.Color> color = Optional.absent();

        if (this.hasColorCompensationEnabled(accountId)) {
            color = senseColorDAO.getColorForSense(deviceIdPair.get().externalDeviceId);
        }

        final Optional<Calibration> calibrationOptional = getCalibrationStrict(deviceIdPair.get().externalDeviceId);

        final AllSensorSampleList sensorData = deviceDataDAODynamoDB.generateTimeSeriesByUTCTimeAllSensors(
                queryStartTimeInUTC, queryEndTimestampInUTC, accountId, deviceIdPair.get().externalDeviceId,
                slotDurationInMinutes, missingDataDefaultValue(accountId), color, calibrationOptional,
                useAudioPeakEnergy(accountId));

        if (sensorData.isEmpty()) {
            return AllSensorSampleList.getEmptyData();
        }

        final AllSensorSampleList adjustedSensorData = adjustTimeSeriesAllSensors(sensorData, deviceIdPair.get().externalDeviceId);

        return getDisplayData(adjustedSensorData.getAllData(), hasCalibrationEnabled(deviceIdPair.get().externalDeviceId));
    }

    private static Map<Sensor, List<Sample>> getDisplayData(final Map<Sensor, List<Sample>> allSensorData, Boolean hasDust){
        final Map<Sensor, List<Sample>> displayData = Maps.newHashMap();
        displayData.put(Sensor.LIGHT, allSensorData.get(Sensor.LIGHT));
        displayData.put(Sensor.HUMIDITY, allSensorData.get(Sensor.HUMIDITY));
        displayData.put(Sensor.SOUND, allSensorData.get(Sensor.SOUND));
        displayData.put(Sensor.TEMPERATURE, allSensorData.get(Sensor.TEMPERATURE));
        if(hasDust) {
            displayData.put(Sensor.PARTICULATES, allSensorData.get(Sensor.PARTICULATES));
        }
        return displayData;
    }

    private Calibration getCalibration(final String senseId) {
        final Optional<Calibration> optionalCalibration = this.hasCalibrationEnabled(senseId) ? calibrationDAO.getStrict(senseId) : Optional.<Calibration>absent();
        return optionalCalibration.isPresent() ? optionalCalibration.get() : Calibration.createDefault(senseId);
    }

    private Optional<Calibration> getCalibrationStrict(final String senseId) {
        return calibrationDAO.getStrict(senseId);
    }

    private List<Sample> adjustTimeSeries (final List<Sample> samples, final Sensor sensor, final String senseId) {
        if (Sensor.PARTICULATES.equals(sensor) && this.hasDustSmoothEnabled(senseId)) {
            return SmoothSample.convert(samples);
        } else if (Sensor.SOUND.equals(sensor)) {
            return SmoothSample.replaceAll(samples, NO_SOUND_CAPTURED_DB, NO_SOUND_FILL_VALUE_DB);
        }
        return samples;
    }

    private AllSensorSampleList adjustTimeSeriesAllSensors (final AllSensorSampleList allSensorSampleList, final String senseId) {
        if (hasDustSmoothEnabled(senseId)) {
            allSensorSampleList.update(Sensor.PARTICULATES, SmoothSample.convert(allSensorSampleList.get(Sensor.PARTICULATES)));
        }
        allSensorSampleList.update(Sensor.SOUND, SmoothSample.replaceAll(allSensorSampleList.get(Sensor.SOUND),
                NO_SOUND_CAPTURED_DB, NO_SOUND_FILL_VALUE_DB));
        return allSensorSampleList;
    }
}