package com.hello.suripu.app.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.hello.suripu.app.sharing.InsightShare;
import com.hello.suripu.app.sharing.Share;
import com.hello.suripu.app.sharing.ShareDAO;
import com.hello.suripu.app.sharing.ShareRequest;
import com.hello.suripu.app.sharing.ShareResponse;
import com.hello.suripu.core.db.InsightsDAODynamoDB;
import com.hello.suripu.core.db.TrendsInsightsDAO;
import com.hello.suripu.core.models.Insights.InsightCard;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.processors.InsightProcessor;
import com.hello.suripu.coredw8.oauth.AccessToken;
import com.hello.suripu.coredw8.oauth.Auth;
import com.hello.suripu.coredw8.oauth.ScopesAllowed;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/v2/sharing")
public class SharingResource {

    private final ShareDAO shareDAO;
    private final InsightsDAODynamoDB insightsDAODynamoDB;
    private final ObjectMapper mapper;
    private final TrendsInsightsDAO trendsInsightsDAO;

    public SharingResource(final ShareDAO shareDAO, final InsightsDAODynamoDB insightsDAODynamoDB,
                           final TrendsInsightsDAO trendsInsightsDAO, final ObjectMapper mapper) {
        this.shareDAO = shareDAO;
        this.insightsDAODynamoDB = insightsDAODynamoDB;
        this.trendsInsightsDAO = trendsInsightsDAO;
        this.mapper = mapper;
    }


    @ScopesAllowed({OAuthScope.USER_EXTENDED})
    @POST
    @Path("/insight")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ShareResponse shareInsight(@Auth final AccessToken accessToken,
                                      @Valid final ShareRequest shareRequest) {

        final List<InsightCard> insights = insightsDAODynamoDB.getInsightsByDate(
                1001L,
                DateTime.now(DateTimeZone.UTC),
                false,
                100);

        InsightCard insightCard = null;
        for(final InsightCard card : insights) {
            if(card.id.isPresent() && card.id.get().equals(UUID.fromString(shareRequest.id))) {
                insightCard = card;
                break;
            }
        }

        if(insightCard == null) {
            throw new WebApplicationException(404);
        }

        final List<InsightCard> cardsWithOutImages = Lists.newArrayList(insightCard);
        final Map<InsightCard.Category, String> categoryNames = InsightProcessor.categoryNames(trendsInsightsDAO);
        final List<InsightCard> cardsWithImmages = InsightsDAODynamoDB.backfillImagesBasedOnCategory(cardsWithOutImages , categoryNames);
        final InsightCard card = cardsWithImmages.get(0);

        final InsightShare insightShare = InsightShare.create(card, 1001L, mapper);
        final String id = shareDAO.put(insightShare);
        return ShareResponse.create(id);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{uuid}")
    public Share getShare(final @PathParam("uuid") String uuid) {
        return null;
    }
}
