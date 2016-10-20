package com.hello.suripu.app.utils;

import com.google.common.base.Optional;

import com.hello.suripu.core.models.Alarm;
import com.hello.suripu.core.models.AlarmExpansion;
import com.hello.suripu.core.models.ValueRange;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import is.hello.gaibu.core.models.Expansion;
import is.hello.gaibu.core.stores.ExpansionStore;
import is.hello.gaibu.homeauto.factories.HomeAutomationExpansionFactory;

/**
 * Created by jnorgan on 10/18/16.
 */
public class ExpansionUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExpansionUtils.class);

  public static List<Alarm> updateExpansionAlarms(final ExpansionStore<Expansion> expansionStore,
                                                  final List<Alarm> alarms,
                                                  final Long accountId) {
    for(final Alarm alarm:alarms){
      if(alarm.expansions.isEmpty()) {
        continue;
      }

      for(final AlarmExpansion alarmExpansion: alarm.expansions){
        final Optional<Expansion> expansionOptional = expansionStore.getApplicationById(alarmExpansion.id);
        if(!expansionOptional.isPresent()) {
          LOGGER.warn("warning=expansion-not-found");
          continue;
        }
        final Expansion expansion = expansionOptional.get();
        final ValueRange expansionValueRange = HomeAutomationExpansionFactory.getValueRangeByServiceName(expansion.serviceName);

        //Update the service name
        alarmExpansion.serviceName = expansion.serviceName.toString();

        //Sanity check the target values
        if(!expansionValueRange.isEmpty() && alarmExpansion.targetValue.isEmpty()) {
          LOGGER.error("error=expansion-alarm-no-target service_name={} account_id={}", alarmExpansion.serviceName, accountId);
          continue;
        }

        if(!alarmExpansion.targetValue.hasRangeWithinRange(expansionValueRange)) {
          LOGGER.error("error=expansion-alarm-outside-range service_name={} account_id={}", alarmExpansion.serviceName, accountId);
        }

      }
    }
    return alarms;
  }
}
