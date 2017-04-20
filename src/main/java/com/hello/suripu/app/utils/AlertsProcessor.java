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
        return Optional.fromNullable(getSenseAlert(accountId));
    }

    public Optional<Alert> getPillAlertOptional(final Long accountId) {
        return Optional.fromNullable(getPillAlert(accountId));
    }

    public Optional<Alert> getSystemAlertOptional(final Long accountId) {
        final Optional<Alert> alertOptional = alertsDAO.mostRecentNotSeen(accountId);
        if(alertOptional.isPresent()) {
            alertsDAO.seen(alertOptional.get().id());
        }
        return alertOptional;
    }

    @Nullable
    private Alert getSenseAlert(final Long accountId) {
        final List<Sense> senses = deviceProcessor.getSenses(accountId);

        if(senses.isEmpty()) {
            return this.map(AlertCategory.SENSE_NOT_PAIRED, accountId);
        } else {
            final Sense sense = senses.get(0);
            final VoiceMetadata voiceMetadata = voiceMetadataDAO.get(sense.externalId, accountId, accountId);
            if(voiceMetadata.muted() && HumanReadableHardwareVersion.SENSE_WITH_VOICE.equals(sense.hardwareVersion())) {
                LOGGER.debug("action=show-mute-alarm sense_id={} account_id={}", sense.externalId, accountId);
                return this.map(AlertCategory.SENSE_MUTED, accountId);
            } else {
                final Optional<DateTime> lastUpdatedOptional = sense.lastUpdatedOptional;
                if (lastUpdatedOptional.isPresent() && this.shouldCreateAlert(lastUpdatedOptional.get(), 1)) {
                    return this.map(AlertCategory.SENSE_NOT_SEEN, accountId);
                }
            }
            return null;
        }
    }

    @Nullable
    private Alert getPillAlert(final Long accountId) {
        final Optional<Account> accountOptional = accountDAO.getById(accountId);
        if (!accountOptional.isPresent()) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        final List<Pill> pills = deviceProcessor.getPills(accountId, accountOptional.get());

        if(pills.isEmpty()) {
            return this.map(AlertCategory.SLEEP_PILL_NOT_PAIRED, accountId);
        } else {
            final Pill pill = pills.get(0);
            final Optional<DateTime> lastUpdatedOptional = pill.lastUpdatedOptional;
            if (lastUpdatedOptional.isPresent() && this.shouldCreateAlert(lastUpdatedOptional.get(), 1)) {
                return this.map(AlertCategory.SLEEP_PILL_NOT_SEEN, accountId);
            }
            return null;
        }
    }

    @NotNull
    private Alert map(@NotNull final AlertCategory alertCategory,
                      final Long accountId) {
        final Alert alert;
        LOGGER.debug("action=map alert_category={} account_id={}", alertCategory, accountId);
        switch (alertCategory) {
            case SENSE_MUTED:
                alert = Alert.muted(accountId, DateTime.now());
                break;
            case SENSE_NOT_PAIRED:
                alert = Alert.create(
                        3L,
                        accountId,
                        "Sense Not Paired",
                        "Your account does not have a Sense paired.  Sense is required to track your sleep behavior.",
                        AlertCategory.SENSE_NOT_PAIRED,
                        DateTime.now());
                break;
            case SENSE_NOT_SEEN:
                alert = Alert.create(
                        4L,
                        accountId,
                        "Sense",
                        "Sense has not reported any data recently. Tap ‘Fix Now’ to troubleshoot.", //todo either copy needs to change or body needs to support replacement
                        AlertCategory.SLEEP_PILL_NOT_PAIRED,
                        DateTime.now());
                break;
            case SLEEP_PILL_NOT_PAIRED:
                alert = Alert.create(
                        5L,
                        accountId,
                        "Sleep Pill Not Paired",
                        "Your account does not have a Sleep Pill paired.  A Sleep Pill is required to track your sleep behavior.",
                        AlertCategory.SLEEP_PILL_NOT_PAIRED,
                        DateTime.now());
                break;
            case SLEEP_PILL_NOT_SEEN:
                alert = Alert.create(
                        6L,
                        accountId,
                        "Sleep Pill",
                        "Sleep Pill has not reported any data recently. Tap ‘Fix Now’ to troubleshoot.", //todo either copy needs to change or body needs to support replacement
                        AlertCategory.SLEEP_PILL_NOT_SEEN,
                        DateTime.now());
                break;
            default:
                LOGGER.error("action=map alert_category={} account_id={} unsupported alert_category", alertCategory, accountId);
                throw new WebApplicationException(Response.Status.BAD_REQUEST); // todo make InvalidAlertCategoryException that is try catched?
        }

        return alert;
    }

    private boolean shouldCreateAlert(@NotNull final DateTime lastSeen, final int atLeastNumDays) {
        return Days.daysBetween(lastSeen, DateTime.now()).isGreaterThan(Days.days(atLeastNumDays));
    }
}
