package com.hello.suripu.app.v2;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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
import com.hello.suripu.core.models.AllSensorSampleList;
import com.hello.suripu.core.models.Calibration;
import com.hello.suripu.core.models.Device;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.models.Sensor;
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
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
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

    public SensorsResource(DeviceDataDAODynamoDB deviceDataDAODynamoDB, DeviceDAO deviceDAO, SenseColorDAO senseColorDAO, CalibrationDAO calibrationDAO) {
        this.deviceDataDAODynamoDB = deviceDataDAODynamoDB;
        this.deviceDAO = deviceDAO;
        this.senseColorDAO = senseColorDAO;
        this.calibrationDAO = calibrationDAO;
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public SensorResponse list() {

        final SensorView sensorView = new SensorView("Temperature", Sensor.TEMPERATURE, SensorUnit.CELCIUS, "some clever message", new TemperatureScale());

        final SensorResponse response = new SensorResponse(SensorStatus.OK, Lists.newArrayList(sensorView));
        return response;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public SensorsDataResponse data(@Valid final SensorsDataRequest request) {

        final Long accountId = 2350L;
        // get latest device_id connected to this account
        final Optional<DeviceAccountPair> deviceIdPair = deviceDAO.getMostRecentSensePairByAccountId(accountId);
        if(!deviceIdPair.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        final Optional<Device.Color> color = senseColorDAO.getColorForSense(deviceIdPair.get().externalDeviceId);


        final Optional<Calibration> calibrationOptional = calibrationDAO.getStrict(deviceIdPair.get().externalDeviceId);
        final Map<Sensor, SensorData> map = Maps.newHashMap();


        final SensorQueryParameters queryParameters = SensorQueryParameters.from(request.queries().get(0).scope(), DateTime.now(DateTimeZone.UTC));
        final AllSensorSampleList timeSeries = deviceDataDAODynamoDB.generateTimeSeriesByUTCTimeAllSensors(
                queryParameters.start().getMillis(), queryParameters.end().getMillis(), accountId, deviceIdPair.get().externalDeviceId,
                queryParameters.slotDuration(), -1, color, calibrationOptional, true);

        final Set<Sensor> sensorValues = Sets.newHashSet(timeSeries.getAvailableSensors());
        for(final SensorQuery query : request.queries()) {
            if(sensorValues.contains(query.type())) {
                final SensorData sensorData = SensorData.from(timeSeries.get(query.type()));
                map.put(query.type(), sensorData);
            } else {
                LOGGER.warn("action=get-sensor-data sensor={} result=missing", query.type());
            }
        }

        return SensorsDataResponse.create(map, extractTimestamps(timeSeries));
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
