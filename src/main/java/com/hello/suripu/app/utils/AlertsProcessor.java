package com.hello.suripu.app.utils;

import com.google.common.base.Optional;
import com.hello.suripu.core.alerts.Alert;
import com.hello.suripu.core.alerts.AlertCategory;
import com.hello.suripu.core.alerts.AlertsDAO;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.models.Account;
import com.hello.suripu.core.models.device.v2.DeviceProcessor;
import com.hello.suripu.core.models.device.v2.Pill;
import com.hello.suripu.core.models.device.v2.Sense;
import com.hello.suripu.core.sense.metadata.HumanReadableHardwareVersion;
import com.hello.suripu.core.sense.voice.VoiceMetadata;
import com.hello.suripu.core.sense.voice.VoiceMetadataDAO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * Created by simonchen on 4/19/17.
 */
public class AlertsProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertsProcessor.class);

    private final AlertsDAO alertsDAO;
    private final VoiceMetadataDAO voiceMetadataDAO;
    private final DeviceProcessor deviceProcessor;
    private final AccountDAO accountDAO;

    public AlertsProcessor(final AlertsDAO alertsDAO,
                          final VoiceMetadataDAO voiceMetadataDAO,
                          final DeviceProcessor deviceProcessor,
                           final AccountDAO accountDAO) {
        this.alertsDAO = alertsDAO;
        this.voiceMetadataDAO = voiceMetadataDAO;
        this.deviceProcessor = deviceProcessor;
        this.accountDAO = accountDAO;
    }

    public Optional<Alert> getSenseAlertOptional(final Long accountId) {
        return Optional.fromNullable(getSenseAlert(accountId, DateTime.now(DateTimeZone.UTC)));
    }

    public Optional<Alert> getPillAlertOptional(final Long accountId) {
        return Optional.fromNullable(getPillAlert(accountId, DateTime.now(DateTimeZone.UTC)));
    }

    public Optional<Alert> getSystemAlertOptional(final Long accountId) {
        final Optional<Alert> alertOptional = alertsDAO.mostRecentNotSeen(accountId);
        if(alertOptional.isPresent()) {
            alertsDAO.seen(alertOptional.get().id());
        }
        return alertOptional;
    }

    @Nullable
    private Alert getSenseAlert(final Long accountId, @NotNull final DateTime createdAt) {
        final List<Sense> senses = deviceProcessor.getSenses(accountId);

        if(senses.isEmpty()) {
            return this.map(AlertCategory.SENSE_NOT_PAIRED, accountId, createdAt);
        } else {
            final Sense sense = senses.get(0);
            final VoiceMetadata voiceMetadata = voiceMetadataDAO.get(sense.externalId, accountId, accountId);
            if(voiceMetadata.muted() && HumanReadableHardwareVersion.SENSE_WITH_VOICE.equals(sense.hardwareVersion())) {
                LOGGER.debug("action=show-mute-alarm sense_id={} account_id={}", sense.externalId, accountId);
                return this.map(AlertCategory.SENSE_MUTED, accountId, createdAt);
            }
            final Optional<DateTime> lastUpdatedOptional = sense.lastUpdatedOptional;
            if (lastUpdatedOptional.isPresent() && this.shouldCreateAlert(lastUpdatedOptional.get(), 1)) {
                return this.map(AlertCategory.SENSE_NOT_SEEN, accountId, createdAt);
            }

            return null;
        }
    }

    @Nullable
    private Alert getPillAlert(final Long accountId, @NotNull final DateTime createdAt) {
        final Optional<Account> accountOptional = accountDAO.getById(accountId);
        if (!accountOptional.isPresent()) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        final List<Pill> pills = deviceProcessor.getPills(accountId, accountOptional.get());

        if(pills.isEmpty()) {
            return this.map(AlertCategory.SLEEP_PILL_NOT_PAIRED, accountId, createdAt);
        }
        final Pill pill = pills.get(0);
        final Optional<DateTime> lastUpdatedOptional = pill.lastUpdatedOptional;
        if (lastUpdatedOptional.isPresent() && this.shouldCreateAlert(lastUpdatedOptional.get(), 1)) {
            return this.map(AlertCategory.SLEEP_PILL_NOT_SEEN, accountId, createdAt);
        }

        return null;
    }

    @NotNull
    private Alert map(@NotNull final AlertCategory alertCategory,
                      @NotNull final Long accountId,
                      @NotNull final DateTime createdAt) {
        LOGGER.debug("action=map alert_category={} account_id={}", alertCategory, accountId);
        switch (alertCategory) {
            case SENSE_MUTED:
                return Alert.muted(accountId, DateTime.now());
            case SENSE_NOT_PAIRED:
                return Alert.create(
                        3L,
                        accountId,
                        "Sense Not Paired",
                        "Your account does not have a Sense paired.  Sense is required to track your sleep behavior.",
                        AlertCategory.SENSE_NOT_PAIRED,
                        createdAt);
            case SENSE_NOT_SEEN:
                return Alert.create(
                        4L,
                        accountId,
                        "Sense",
                        "Sense has not reported any data recently. Tap ‘Fix Now’ to troubleshoot.", //todo either copy needs to change or body needs to support replacement
                        AlertCategory.SLEEP_PILL_NOT_PAIRED,
                        createdAt);
            case SLEEP_PILL_NOT_PAIRED:
                return Alert.create(
                        5L,
                        accountId,
                        "Sleep Pill Not Paired",
                        "Your account does not have a Sleep Pill paired.  A Sleep Pill is required to track your sleep behavior.",
                        AlertCategory.SLEEP_PILL_NOT_PAIRED,
                        createdAt);
            case SLEEP_PILL_NOT_SEEN:
                return Alert.create(
                        6L,
                        accountId,
                        "Sleep Pill",
                        "Sleep Pill has not reported any data recently. Tap ‘Fix Now’ to troubleshoot.", //todo either copy needs to change or body needs to support replacement
                        AlertCategory.SLEEP_PILL_NOT_SEEN,
                        createdAt);
            default:
                LOGGER.error("action=map alert_category={} account_id={} unsupported alert_category", alertCategory, accountId);
                throw new WebApplicationException(Response.Status.BAD_REQUEST); // todo make InvalidAlertCategoryException that is try catched?
        }
    }

    private boolean shouldCreateAlert(@NotNull final DateTime lastSeen, final int atLeastNumDays) {
        return Days.daysBetween(lastSeen, DateTime.now(DateTimeZone.UTC)).isGreaterThan(Days.days(atLeastNumDays));
    }
}
