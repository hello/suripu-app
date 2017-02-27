package com.hello.suripu.app.v2;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import com.codahale.metrics.annotation.Timed;
import com.hello.suripu.core.db.InsightsDAODynamoDB;
import com.hello.suripu.core.db.TrendsInsightsDAO;
import com.hello.suripu.core.models.Insights.InfoInsightCards;
import com.hello.suripu.core.models.Insights.InsightCard;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.processors.InsightProcessor;
import com.hello.suripu.core.processors.insights.IntroductionInsights;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/v2/insights")
public class InsightsResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(InsightsResource.class);
    private static final int MAX_INSIGHTS_NUM = 20;

    private final InsightsDAODynamoDB insightsDAODynamoDB;
    private final TrendsInsightsDAO trendsInsightsDAO;

    public InsightsResource(final InsightsDAODynamoDB insightsDAODynamoDB,
                            final TrendsInsightsDAO trendsInsightsDAO) {
        this.insightsDAODynamoDB = insightsDAODynamoDB;
        this.trendsInsightsDAO = trendsInsightsDAO;
    }

    @ScopesAllowed({OAuthScope.INSIGHTS_READ})
    @Timed
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<InsightCard> getInsights(@Auth final AccessToken accessToken) {

        final Boolean chronological = false; // reverse chronological
        final DateTime queryDate = DateTime.now(DateTimeZone.UTC);
        LOGGER.debug("action=get_insight account_id={} querydate={}", accessToken.accountId, queryDate);
        final ImmutableList<InsightCard> cards = insightsDAODynamoDB.getInsightsByDate(accessToken.accountId,
                queryDate, chronological, MAX_INSIGHTS_NUM);
        LOGGER.debug("action=insight_results account_id={} size={}", accessToken.accountId, cards.size());

        if (cards.isEmpty()) {
            // no insights generated yet, probably a new user, send introduction cards
            final List<InsightCard> introCards = Lists.newArrayList(IntroductionInsights.getIntroductionCard(accessToken.accountId));
            this.insightsDAODynamoDB.insertListOfInsights(introCards);
            return insightCardsWithInfoPreviewAndMissingImages(introCards);
        }

        return insightCardsWithInfoPreviewAndMissingImages(cards);
    }

    @ScopesAllowed({OAuthScope.INSIGHTS_READ})
    @Timed
    @GET
    @Path("/info/{category}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<InfoInsightCards> getGenericInsightCards(
            @Auth final AccessToken accessToken,
            @PathParam("category") final String value) {
        try {
            final InsightCard.Category category = InsightCard.Category.fromString(value);

            final List<InfoInsightCards> cards = trendsInsightsDAO.getGenericInsightCardsByCategory(category.toString().toLowerCase());
            final DateTime readDate = DateTime.now(DateTimeZone.UTC);
            LOGGER.debug("action=insight_detail account_id={} category={} readdate={}", accessToken.accountId, category, readDate);
            return cards;
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }
    }

    /**
     * Convenience method to construct a new list of InsightCards that include info preview titles
     * @param insightCards: an array of insight card without info preview titles
     * @return List of InsightCard objects that contain info preview titles
     */
    private List<InsightCard> insightCardsWithInfoPreviewAndMissingImages(final List<InsightCard> insightCards) {
        final Map<InsightCard.Category, String> categoryNames = InsightProcessor.categoryNames(trendsInsightsDAO);
        return InsightsDAODynamoDB.backfillImagesBasedOnCategory(insightCards, categoryNames);
    }

}
