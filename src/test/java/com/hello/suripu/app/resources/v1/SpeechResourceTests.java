package com.hello.suripu.app.resources.v1;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.speech.interfaces.SpeechResultReadDAO;
import com.hello.suripu.core.speech.interfaces.SpeechTimelineReadDAO;
import com.hello.suripu.core.speech.models.Result;
import com.hello.suripu.core.speech.models.SpeechResult;
import com.hello.suripu.core.speech.models.SpeechTimeline;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Created by ksg on 10/13/16
 */
public class SpeechResourceTests {
    private SpeechResource resource;
    private final AccessToken accessToken;
    private final AccessToken partnerToken;

    private SpeechResultReadDAO speechResultReadDAO;
    private SpeechTimelineReadDAO speechTimelineReadDAO;
    private DeviceDAO deviceDAO;

    private static Long PRIMARY_ACCOUNT_ID = 1L;
    private static Long PARTNER_ACCOUNT_ID = 2L;
    private static String SENSE_ID = "LIKEAROLLINGSTONE";
    private static int LOOK_BACK_MINUTES = 3;

    public SpeechResourceTests() {
        this.accessToken = new AccessToken.Builder()
                .withAppId(1L)
                .withAccountId(PRIMARY_ACCOUNT_ID)
                .withCreatedAt(DateTime.now().minusHours(3))
                .withExpiresIn(9000L)
                .withRefreshExpiresIn(9000L)
                .withRefreshToken(UUID.randomUUID())
                .withToken(UUID.randomUUID())
                .withScopes(new OAuthScope[] {OAuthScope.SENSORS_BASIC})
                .build();

        this.partnerToken = new AccessToken.Builder()
                .withAppId(1L)
                .withAccountId(PARTNER_ACCOUNT_ID)
                .withCreatedAt(DateTime.now().minusHours(2))
                .withExpiresIn(9000L)
                .withRefreshExpiresIn(9000L)
                .withRefreshToken(UUID.randomUUID())
                .withToken(UUID.randomUUID())
                .withScopes(new OAuthScope[] {OAuthScope.SENSORS_BASIC})
                .build();

    }


    @Before
    public void setUp() {
        final DateTime now = DateTime.now(DateTimeZone.UTC);
        final DeviceAccountPair primaryAccount = new DeviceAccountPair(PRIMARY_ACCOUNT_ID, 1L, SENSE_ID, now.minusHours(2));
        final DeviceAccountPair partnerAccount = new DeviceAccountPair(PARTNER_ACCOUNT_ID, 2L, SENSE_ID, now.minusHours(1));
        final List<DeviceAccountPair> accounts = Lists.newArrayList();
        accounts.add(primaryAccount);
        accounts.add(partnerAccount);

        this.deviceDAO = mock(DeviceDAO.class);
        doReturn(Optional.of(partnerAccount)).when(deviceDAO).getMostRecentSensePairByAccountId(PARTNER_ACCOUNT_ID);
        doReturn(Optional.of(primaryAccount)).when(deviceDAO).getMostRecentSensePairByAccountId(PRIMARY_ACCOUNT_ID);
        doReturn(ImmutableList.copyOf(accounts)).when(deviceDAO).getAccountIdsForDeviceId(SENSE_ID);

        final String audioUUID = "abcde";
        final SpeechTimeline primaryTimeline = new SpeechTimeline(PRIMARY_ACCOUNT_ID, now.minusMinutes(1), SENSE_ID, audioUUID);

        this.speechTimelineReadDAO = mock(SpeechTimelineReadDAO.class);
        doReturn(Optional.absent()).when(speechTimelineReadDAO).getLatest(PARTNER_ACCOUNT_ID, LOOK_BACK_MINUTES);
        doReturn(Optional.absent()).when(speechTimelineReadDAO).getLatest(PARTNER_ACCOUNT_ID, LOOK_BACK_MINUTES + 2);
        doReturn(Optional.absent()).when(speechTimelineReadDAO).getLatest(PRIMARY_ACCOUNT_ID, LOOK_BACK_MINUTES);
        doReturn(Optional.of(primaryTimeline)).when(speechTimelineReadDAO).getLatest(PRIMARY_ACCOUNT_ID, LOOK_BACK_MINUTES + 2);

        final SpeechResult speechResult = new SpeechResult.Builder().withAccountId(PRIMARY_ACCOUNT_ID)
                .withSenseId(SENSE_ID)
                .withAudioIndentifier(audioUUID)
                .withDateTimeUTC(now.minusMinutes(1))
                .withText("what is the temperature?")
                .withResponseText("the temperature is way too hot")
                .withCommand("room_condition")
                .withResult(Result.OK).build();

        this.speechResultReadDAO = mock(SpeechResultReadDAO.class);
        doReturn(Optional.of(speechResult)).when(speechResultReadDAO).getItem(audioUUID);

        this.resource = new SpeechResource(speechTimelineReadDAO, speechResultReadDAO, deviceDAO);
    }

    @After
    public void tearDown() {}

    @Test
    public void testPrimaryUserNoResults() {
        final List<SpeechResult> results = this.resource.getOnboardingResults(this.accessToken, LOOK_BACK_MINUTES);
        assertThat(results.isEmpty(), is(true));
    }

    @Test
    public void testPrimaryUserWithResults() {
        final List<SpeechResult> results = this.resource.getOnboardingResults(this.accessToken, LOOK_BACK_MINUTES + 2);
        assertThat(results.isEmpty(), is(false));
        assertThat(results.get(0).command.get(), is("room_condition"));
    }

    @Test
    public void testPartnerUserNoResults() {
        final List<SpeechResult> results = this.resource.getOnboardingResults(this.partnerToken, LOOK_BACK_MINUTES);
        assertThat(results.isEmpty(), is(true));
    }

    @Test
    public void testPartnerUserWithResults() {
        final List<SpeechResult> results = this.resource.getOnboardingResults(this.partnerToken, LOOK_BACK_MINUTES + 2);
        assertThat(results.isEmpty(), is(false));
        assertThat(results.get(0).command.get(), is("room_condition"));
    }
}
