package com.hello.suripu.app.resources.v1;

import com.amazon.speech.speechlet.servlet.SpeechletServlet;
import com.hello.suripu.app.service.SenseSpeechlet;
import com.hello.suripu.app.service.TestVoiceResponsesDAO;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.db.AlarmDAODynamoDB;
import com.hello.suripu.core.db.CalibrationDAO;
import com.hello.suripu.core.db.DeviceDataDAODynamoDB;
import com.hello.suripu.core.db.DeviceReadDAO;
import com.hello.suripu.core.db.MergedUserInfoDynamoDB;
import com.hello.suripu.core.db.sleep_sounds.DurationDAO;
import com.hello.suripu.core.preferences.AccountPreferencesDAO;
import com.hello.suripu.core.processors.SleepSoundsProcessor;
import com.hello.suripu.core.processors.TimelineProcessor;
import com.hello.suripu.coredw8.clients.MessejiClient;
import com.hello.suripu.coredw8.db.AccessTokenDAO;
import com.hello.suripu.coredw8.db.TimelineDAODynamoDB;


import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * Created by jnorgan on 6/16/16.
 */
@Path("/skill")
@Produces(MediaType.APPLICATION_JSON)
public class SkillResource extends SpeechletServlet {
  @Context
  private HttpServletRequest servletRequest;

  @Context
  HttpServletResponse servletResponse;

  @POST
  public void postWrapper() throws ServletException, IOException {
    super.doPost(servletRequest, servletResponse);
  }

  public SkillResource(final AccountDAO accountDAO,
                       final AccessTokenDAO accessTokenDAO,
                       final DeviceReadDAO deviceReadDAO,
                       final DeviceDataDAODynamoDB deviceDataDAO,
                       final TimelineDAODynamoDB timelineDAODynamoDB,
                       final MessejiClient messejiClient,
                       final SleepSoundsProcessor sleepSoundsProcessor,
                       final DurationDAO durationDAO,
                       final TimelineProcessor timelineProcessor,
                       final AccountPreferencesDAO preferencesDAO,
                       final CalibrationDAO calibrationDAO,
                       final MergedUserInfoDynamoDB mergedUserInfoDynamoDB,
                       final AlarmDAODynamoDB alarmDAODynamoDB,
                       final TestVoiceResponsesDAO voiceResponsesDAO) {
    setSpeechlet(new SenseSpeechlet(accountDAO,
        accessTokenDAO,
        deviceReadDAO,
        deviceDataDAO,
        timelineDAODynamoDB,
        messejiClient,
        sleepSoundsProcessor,
        durationDAO,
        timelineProcessor,
        preferencesDAO,
        calibrationDAO,
        mergedUserInfoDynamoDB,
        alarmDAODynamoDB,
        voiceResponsesDAO
    ));
  }

}
