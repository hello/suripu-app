package com.hello.suripu.app.utils;

import com.google.common.base.Optional;

import com.hello.suripu.core.models.Alarm;
import com.hello.suripu.core.models.AlarmExpansion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import is.hello.gaibu.core.models.Expansion;
import is.hello.gaibu.core.stores.ExpansionStore;

/**
 * Created by jnorgan on 10/18/16.
 */
public class ExpansionUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExpansionUtils.class);

  public static List<Alarm> updateAlarmServiceNames(final ExpansionStore<Expansion> expansionStore,
                                                    final List<Alarm> alarms) {
    for(final Alarm alarm:alarms){
      if(alarm.expansions.isEmpty()) {
        return alarms;
      }

      for(final AlarmExpansion alarmExpansion: alarm.expansions){
        final Optional<Expansion> expansionOptional = expansionStore.getApplicationById(alarmExpansion.id);
        if(!expansionOptional.isPresent()) {
          LOGGER.warn("warning=expansion-not-found");
          return alarms;
        }

        final Expansion expansion = expansionOptional.get();
        alarmExpansion.serviceName = expansion.serviceName.toString();
      }
    }
    return alarms;
  }
}
