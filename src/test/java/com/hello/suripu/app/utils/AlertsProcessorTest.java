package com.hello.suripu.app.utils;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.hello.suripu.core.alerts.Alert;
import com.hello.suripu.core.alerts.AlertCategory;
import com.hello.suripu.core.alerts.AlertsDAO;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.models.Account;
import com.hello.suripu.core.models.device.v2.DeviceProcessor;
import com.hello.suripu.core.models.device.v2.Sense;
import com.hello.suripu.core.sense.metadata.HumanReadableHardwareVersion;
import com.hello.suripu.core.sense.voice.VoiceMetadata;
import com.hello.suripu.core.sense.voice.VoiceMetadataDAO;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Created by simonchen on 4/19/17.
 */
public class AlertsProcessorTest {

    private static final Long MOCK_ACCOUNT_ID = 0L;

    private VoiceMetadataDAO mockVoiceMetadataDAO;
    private DeviceProcessor mockDeviceProcessor;
    private Account mockAccount;
    private AlertsProcessor alertsProcessor;

    @Before
    public void setUp() throws Exception {
        mockAccount = mock(Account.class);

        final AlertsDAO mockAlertsDAO = mock(AlertsDAO.class);

        doReturn(Optional.<Alert>absent())
                .when(mockAlertsDAO)
                .mostRecentNotSeen(MOCK_ACCOUNT_ID);

        mockVoiceMetadataDAO = mock(VoiceMetadataDAO.class);

        mockDeviceProcessor = mock(DeviceProcessor.class);

        final AccountDAO mockAccountDAO = mock(AccountDAO.class);

        doReturn(Optional.of(mockAccount))
                .when(mockAccountDAO)
                .getById(MOCK_ACCOUNT_ID);

        alertsProcessor = new AlertsProcessor(
                mockAlertsDAO,
                mockVoiceMetadataDAO,
                mockDeviceProcessor,
                mockAccountDAO
        );
    }

    @Test
    public void getSenseNotPairedAlert() throws Exception {
        doReturn(ImmutableList.builder().build())
                .when(mockDeviceProcessor)
                .getSenses(MOCK_ACCOUNT_ID);

        final Optional<Alert> senseAlert = alertsProcessor.getSenseAlertOptional(MOCK_ACCOUNT_ID);

        assertTrue(senseAlert.isPresent());
        assertThat(senseAlert.get().category(), equalTo(AlertCategory.SENSE_NOT_PAIRED));
    }

    @Test
    public void getSenseMutedAlert() throws Exception {
        final Sense mutedSense = mock(Sense.class);
        doReturn(HumanReadableHardwareVersion.SENSE_WITH_VOICE)
                .when(mutedSense)
                .hardwareVersion();

        VoiceMetadata mockVoiceMetadata = mock(VoiceMetadata.class);
        doReturn(true)
                .when(mockVoiceMetadata)
                .muted();

        doReturn(mockVoiceMetadata)
                .when(mockVoiceMetadataDAO)
                .get(any(), anyLong(), anyLong());

        doReturn(ImmutableList.<Sense>builder().add(mutedSense).build())
                .when(mockDeviceProcessor)
                .getSenses(MOCK_ACCOUNT_ID);

        doReturn(ImmutableList.builder().build())
                .when(mockDeviceProcessor)
                .getPills(MOCK_ACCOUNT_ID, mockAccount);

        final Optional<Alert> systemAlert = alertsProcessor.getSenseAlertOptional(MOCK_ACCOUNT_ID);

        assertTrue(systemAlert.isPresent());
        assertThat(systemAlert.get().category(), equalTo(AlertCategory.SENSE_MUTED));
    }

    @Test
    public void getSleepPillNotPairedAlert() throws Exception {
        final Sense recentlySeenSense = mock(Sense.class);
        doReturn(ImmutableList.<Sense>builder().add(recentlySeenSense).build())
                .when(mockDeviceProcessor)
                .getSenses(MOCK_ACCOUNT_ID);

        doReturn(ImmutableList.builder().build())
                .when(mockDeviceProcessor)
                .getPills(MOCK_ACCOUNT_ID, mockAccount);

        final Optional<Alert> pillAlert = alertsProcessor.getPillAlertOptional(MOCK_ACCOUNT_ID);

        assertTrue(pillAlert.isPresent());
        assertThat(pillAlert.get().category(), equalTo(AlertCategory.SLEEP_PILL_NOT_PAIRED));
    }

}