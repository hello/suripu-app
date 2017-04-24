package com.hello.suripu.app.alerts;

import com.google.common.annotations.VisibleForTesting;
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
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by simonchen on 4/19/17.
 */
public class AlertsProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertsProcessor.class);
    private static final int MIN_NUM_DAYS_SINCE_LAST_SEEN_DEVICE = 1;

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

    /**
     * Only returns alert optionals for the following alert categories if applicable:
     * {@link com.hello.suripu.core.alerts.AlertCategory#EXPANSION_UNREACHABLE}
     * {@link com.hello.suripu.core.alerts.AlertCategory#SENSE_MUTED}
     */
    public Optional<Alert> getExistingAlertOptional(final Long accountId) throws UnsupportedAlertCategoryException {
        final List<Sense> senses = deviceProcessor.getSenses(accountId);
        if (senses.isEmpty()) {
            return getSystemAlertOptional(accountId);
        }
        final Sense sense = senses.get(0);
        return getSenseMutedAlert(accountId, DateTime.now(DateTimeZone.UTC), sense);
    }

    public Optional<Alert> getSenseAlertOptional(final Long accountId) throws UnsupportedAlertCategoryException {
        return getSenseAlert(accountId, DateTime.now(DateTimeZone.UTC));
    }

    public Optional<Alert> getPillAlertOptional(final Long accountId) throws UnsupportedAlertCategoryException, BadAlertRequestException {
        return getPillAlert(accountId, DateTime.now(DateTimeZone.UTC));
    }

    public Optional<Alert> getSystemAlertOptional(final Long accountId) {
        final Optional<Alert> alertOptional = alertsDAO.mostRecentNotSeen(accountId);
        if(alertOptional.isPresent()) {
            alertsDAO.seen(alertOptional.get().id());
        }
        return alertOptional;
    }

    @NotNull
    private Optional<Alert> getSenseAlert(final Long accountId, @NotNull final DateTime createdAt) {
        final List<Sense> senses = deviceProcessor.getSenses(accountId);

        if(senses.isEmpty()) {
            return Optional.of(this.map(AlertCategory.SENSE_NOT_PAIRED, accountId, createdAt));
        }
        final Sense sense = senses.get(0);
        final Optional<Alert> senseMutedAlertOptional = getSenseMutedAlert(accountId, createdAt, sense);
        if (senseMutedAlertOptional.isPresent()) {
            return senseMutedAlertOptional;
        }
        final Optional<DateTime> lastUpdatedOptional = sense.lastUpdatedOptional;
        if (lastUpdatedOptional.isPresent() && this.shouldCreateAlert(lastUpdatedOptional.get(), MIN_NUM_DAYS_SINCE_LAST_SEEN_DEVICE)) {
            return Optional.of(this.map(AlertCategory.SENSE_NOT_SEEN, accountId, createdAt));
        }

        return Optional.absent();

    }

    @NotNull
    private Optional<Alert> getSenseMutedAlert(final Long accountId,
                                               @NotNull final DateTime createdAt,
                                               @NotNull final Sense sense) {
        final VoiceMetadata voiceMetadata = voiceMetadataDAO.get(sense.externalId, accountId, accountId);
        //final VoiceMetadata voiceMetadata = deviceProcessor.voiceMetadata(sense.externalId, accountId);
        if(voiceMetadata.muted() && HumanReadableHardwareVersion.SENSE_WITH_VOICE.equals(sense.hardwareVersion())) {
            LOGGER.debug("action=show-mute-alarm sense_id={} account_id={}", sense.externalId, accountId);
            return Optional.of(this.map(AlertCategory.SENSE_MUTED, accountId, createdAt));
        }
        return Optional.absent();
    }

    @NotNull
    private Optional<Alert> getPillAlert(final Long accountId, @NotNull final DateTime createdAt) {
        final Optional<Account> accountOptional = accountDAO.getById(accountId);
        if (!accountOptional.isPresent()) {
            throw new com.hello.suripu.app.alerts.AlertsProcessor.BadAlertRequestException(String.format("no account associated with accountId=%s", accountId));
        }
        final List<Pill> pills = deviceProcessor.getPills(accountId, accountOptional.get());

        if(pills.isEmpty()) {
            return Optional.of(this.map(AlertCategory.SLEEP_PILL_NOT_PAIRED, accountId, createdAt));
        }
        final Pill pill = pills.get(0);
        final Optional<DateTime> lastUpdatedOptional = pill.lastUpdatedOptional;
        if (lastUpdatedOptional.isPresent() && this.shouldCreateAlert(lastUpdatedOptional.get(), MIN_NUM_DAYS_SINCE_LAST_SEEN_DEVICE)) {
            return Optional.of(this.map(AlertCategory.SLEEP_PILL_NOT_SEEN, accountId, createdAt));
        }

        return Optional.absent();
    }

    @VisibleForTesting
    @NotNull
    protected Alert map(@NotNull final AlertCategory alertCategory,
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
                        "Sense has not reported any data recently.",
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
                        "Sleep Pill has not reported any data recently.",
                        AlertCategory.SLEEP_PILL_NOT_SEEN,
                        createdAt);
            default:
                LOGGER.error("action=map alert_category={} account_id={} unsupported alert_category", alertCategory, accountId);
                throw new UnsupportedAlertCategoryException(alertCategory);
        }
    }

    private boolean shouldCreateAlert(@NotNull final DateTime lastSeen, final int atLeastNumDays) {
        return Days.daysBetween(lastSeen, DateTime.now(DateTimeZone.UTC)).isGreaterThan(Days.days(atLeastNumDays));
    }

    public static class BadAlertRequestException extends RuntimeException {

        BadAlertRequestException(@NotNull final String message) {
            super(message);
        }
    }

    public static class UnsupportedAlertCategoryException extends RuntimeException {
        private final AlertCategory alertCategory;

        UnsupportedAlertCategoryException(@NotNull final AlertCategory category) {
            super(String.format("cannot create alert for category: %s", category));
            alertCategory = category;
        }

        public AlertCategory getAlertCategory() {
            return alertCategory;
        }
    }
}
