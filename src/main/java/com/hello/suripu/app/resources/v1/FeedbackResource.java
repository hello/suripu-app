package com.hello.suripu.app.resources.v1;

import com.hello.suripu.core.db.FeedbackDAO;
import com.hello.suripu.coredropwizard.db.TimelineDAODynamoDB;
import com.hello.suripu.core.models.SleepFeedback;
import com.hello.suripu.core.models.TimelineFeedback;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;

import org.joda.time.DateTime;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;

@Path("/v1/feedback")
public class FeedbackResource {

    private final FeedbackDAO feedbackDAO;
    private final TimelineDAODynamoDB timelineDAODynamoDB;

    public FeedbackResource(final FeedbackDAO feedbackDAO,
                            final TimelineDAODynamoDB timelineDAODynamoDB) {
        this.feedbackDAO = feedbackDAO;
        this.timelineDAODynamoDB = timelineDAODynamoDB;
    }

    @ScopesAllowed({OAuthScope.SLEEP_FEEDBACK})
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public void saveFeedback(@Auth final AccessToken accessToken, final SleepFeedback feedback) {
        // NOOP
    }


    @ScopesAllowed({OAuthScope.SLEEP_FEEDBACK})
    @POST
    @Path("/sleep")
    @Consumes(MediaType.APPLICATION_JSON)
    public void saveTimelineFeedback(@Auth final AccessToken accessToken, final TimelineFeedback feedback) {
        feedbackDAO.insertTimelineFeedback(accessToken.accountId, feedback);
        timelineDAODynamoDB.invalidateCache(accessToken.accountId, feedback.dateOfNight, DateTime.now());
    }
}
