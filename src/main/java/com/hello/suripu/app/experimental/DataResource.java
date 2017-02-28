package com.hello.suripu.app.experimental;

import com.google.api.client.util.Lists;

import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.core.db.PillDataDAODynamoDB;
import com.hello.suripu.core.models.TrackerMotion;
import com.hello.suripu.core.models.device.v2.DeviceProcessor;
import com.hello.suripu.core.models.device.v2.DeviceQueryInfo;
import com.hello.suripu.core.models.device.v2.Devices;
import com.hello.suripu.core.models.device.v2.Pill;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import com.hello.suripu.coredropwizard.resources.BaseResource;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Created by jnorgan on 2/27/17.
 */
@Path("/experimental/data")
public class DataResource extends BaseResource {
  private static final Logger LOGGER = LoggerFactory.getLogger(DataResource.class);

  private final DeviceProcessor deviceProcessor;
  private final PillDataDAODynamoDB pillDataDAODynamoDB;

  public DataResource(final DeviceProcessor deviceProcessor,
                      final PillDataDAODynamoDB pillDataDAODynamoDB) {
    this.pillDataDAODynamoDB = pillDataDAODynamoDB;
    this.deviceProcessor = deviceProcessor;
  }

  @ScopesAllowed({OAuthScope.USER_BASIC})
  @GET
  @Timed
  @Path("/pill/latest")
  @Produces(MediaType.APPLICATION_JSON)
  public List<SanitizedPillData> getPillData(@Auth final AccessToken accessToken, @QueryParam("limit") Integer recordLimit) {
    if(recordLimit == null || recordLimit < 1) {
      recordLimit = 1;
    }
    if(recordLimit > 100) {
      recordLimit = 100;
    }

    final DeviceQueryInfo deviceQueryInfo = DeviceQueryInfo.create(
        accessToken.accountId,
        this.isSenseLastSeenDynamoDBReadEnabled(accessToken.accountId),
        this.isSensorsDBUnavailable(accessToken.accountId)
    );
    final Devices devices = deviceProcessor.getAllDevices(deviceQueryInfo);

    if(devices.pills.isEmpty()) {
      LOGGER.error("error=pill-not-found account_id={}", accessToken.accountId);
      throw new WebApplicationException(Response.Status.NOT_FOUND);
    }

    final Pill pill = devices.pills.get(0);
    final List<TrackerMotion> pillData = pillDataDAODynamoDB.getNumMostRecent(pill.externalId, accessToken.accountId, DateTime.now(), recordLimit);
    if(pillData.isEmpty()) {
      LOGGER.error("error=no-pill-data account_id={}", accessToken.accountId);
      throw new WebApplicationException(Response.Status.NOT_FOUND);
    }
    final List<SanitizedPillData> sanitizedData = Lists.newArrayList();
    for(final TrackerMotion motionData : pillData) {
      final SanitizedPillData data = new SanitizedPillData.Builder()
          .withTimestamp(motionData.timestamp)
          .withOffsetMillis(motionData.offsetMillis)
          .withOnDurationInSeconds(motionData.onDurationInSeconds)
          .build();
      sanitizedData.add(data);
    }

    return sanitizedData;
  }
}
