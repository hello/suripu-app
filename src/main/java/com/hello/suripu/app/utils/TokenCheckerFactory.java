package com.hello.suripu.app.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.base.Optional;
import com.hello.suripu.app.configuration.ExpansionConfiguration;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import is.hello.gaibu.core.models.Expansion;
import is.hello.gaibu.core.models.ExpansionData;
import is.hello.gaibu.core.models.ExpansionDeviceData;
import is.hello.gaibu.core.models.ExternalToken;
import is.hello.gaibu.core.stores.ExpansionStore;
import is.hello.gaibu.core.stores.ExternalOAuthTokenStore;
import is.hello.gaibu.core.stores.PersistentExpansionDataStore;
import is.hello.gaibu.homeauto.factories.HomeAutomationExpansionDataFactory;
import is.hello.gaibu.homeauto.factories.HomeAutomationExpansionFactory;
import is.hello.gaibu.homeauto.interfaces.HomeAutomationExpansion;
import is.hello.gaibu.homeauto.models.ConfigurationResponse;
import is.hello.gaibu.homeauto.models.ResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by jnorgan on 12/9/16.
 */
public class TokenCheckerFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(TokenChecker.class);

  private final DeviceDAO deviceDAO;
  private final ExpansionConfiguration expansionConfig;
  private final ExpansionStore<Expansion> expansionStore;
  private final ExternalOAuthTokenStore<ExternalToken> externalTokenStore;
  private final PersistentExpansionDataStore expansionDataStore;
  private final ObjectMapper mapper;

  public TokenCheckerFactory(final DeviceDAO deviceDAO,
                             final ExpansionConfiguration expansionConfig,
                             final ExpansionStore<Expansion> expansionStore,
                             final ExternalOAuthTokenStore<ExternalToken> externalTokenStore,
                             final PersistentExpansionDataStore expansionDataStore,
                             final ObjectMapper mapper) {
    this.deviceDAO = deviceDAO;
    this.expansionConfig = expansionConfig;
    this.expansionStore = expansionStore;
    this.externalTokenStore = externalTokenStore;
    this.expansionDataStore = expansionDataStore;
    mapper.registerModule(new JodaModule());
    this.mapper = mapper;
  }

  public TokenChecker create(final AccessToken accessToken) {
    return new TokenChecker(accessToken.accountId, deviceDAO, expansionConfig, expansionStore, externalTokenStore, expansionDataStore, mapper);
  }

  public class TokenChecker implements Runnable {

    private final DeviceDAO deviceDAO;
    private final ExpansionConfiguration expansionConfig;
    private final ExpansionStore<Expansion> expansionStore;
    private final ExternalOAuthTokenStore<ExternalToken> externalTokenStore;
    private final PersistentExpansionDataStore expansionDataStore;
    private final ObjectMapper mapper;
    private Long accountId;

    public TokenChecker(final Long accountId,
                        final DeviceDAO deviceDAO,
                        final ExpansionConfiguration expansionConfig,
                        final ExpansionStore<Expansion> expansionStore,
                        final ExternalOAuthTokenStore<ExternalToken> externalTokenStore,
                        final PersistentExpansionDataStore expansionDataStore,
                        final ObjectMapper mapper) {
      this.accountId = accountId;
      this.deviceDAO = deviceDAO;
      this.expansionConfig = expansionConfig;
      this.expansionStore = expansionStore;
      this.externalTokenStore = externalTokenStore;
      this.expansionDataStore = expansionDataStore;
      this.mapper = mapper;
    }

    @Override
    public void run() {
      LOGGER.debug("message=token-checker-thread-start");
      try {
        final Optional<Expansion> expansionOptional = expansionStore.getApplicationByName(Expansion.ServiceName.NEST.toString());
        if(!expansionOptional.isPresent()) {
          LOGGER.error("error=expansion-not-found app_name=Nest");
          return;
        }

        final Expansion expansion = expansionOptional.get();
        final List<DeviceAccountPair> sensePairedWithAccount = deviceDAO.getSensesForAccountId(accountId);
        if(sensePairedWithAccount.size() == 0){
          LOGGER.error("error=no-sense-paired account_id={}", accountId);
          return;
        }
        final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

        //Check that the deviceId has an enabled expansion token
        final Integer tokenCount = externalTokenStore.getActiveTokenCount(deviceId, expansion.id);
        if(tokenCount < 1) {
          LOGGER.debug("debug=no-active-tokens-found service_name={} device_id={}", Expansion.ServiceName.NEST.toString(), deviceId);
          return;
        }

        final Optional<ExpansionData> expDataOptional = expansionDataStore.getAppData(expansion.id, deviceId);
        if(!expDataOptional.isPresent()) {
          LOGGER.error("error=no-ext-app-data expansion_id={} device_id={}", expansion.id, deviceId);
          return;
        }

        final ExpansionData expData = expDataOptional.get();

        if(expData.data.isEmpty()){
          LOGGER.error("error=no-ext-app-data expansion_id={} device_id={}", expansion.id, deviceId);
          return;
        }

        final Optional<ExpansionDeviceData> expansionDeviceDataOptional = HomeAutomationExpansionDataFactory.getAppData(mapper, expData.data, expansion.serviceName);

        if(!expansionDeviceDataOptional.isPresent()){
          LOGGER.error("error=bad-expansion-data service_name={} device_id={}", Expansion.ServiceName.NEST.toString(), deviceId);
          return;
        }

        final ExpansionDeviceData appData = expansionDeviceDataOptional.get();

        final Optional<String> decryptedTokenOptional = externalTokenStore.getDecryptedExternalToken(deviceId, expansion, false);
        if(!decryptedTokenOptional.isPresent()) {
          LOGGER.warn("warning=token-decrypt-failed service_name={} device_id={}", Expansion.ServiceName.NEST.toString(), deviceId);
          return;
        }

        final String decryptedToken = decryptedTokenOptional.get();

        final Optional<HomeAutomationExpansion> homeAutomationExpansionOptional = HomeAutomationExpansionFactory.getEmptyExpansion(expansionConfig.hueAppName(), expansion.serviceName, appData, decryptedToken);
        if(!expansionOptional.isPresent()){
          LOGGER.error("error=expansion-retrieval-failure service_name={} device_id={}", Expansion.ServiceName.NEST.toString(), deviceId);
          return;
        }

        final HomeAutomationExpansion homeAutomationExpansion = homeAutomationExpansionOptional.get();
        //Check status of token with external service
        final ConfigurationResponse configResponse = homeAutomationExpansion.getConfigurations();
        if(ResponseStatus.UNAUTHORIZED == configResponse.getStatus()) {
          LOGGER.info("info=disabling-invalid-token service_name={} device_id={}", Expansion.ServiceName.NEST.toString(), deviceId);
          //disable the unusable token to force refresh (manual re-auth for Nest)
          externalTokenStore.disableByDeviceId(deviceId, expansion.id);
          final ExpansionData.Builder newDataBuilder = new ExpansionData.Builder();
          newDataBuilder
                  .withAppId(expansion.id)
                  .withDeviceId(deviceId)
                  .withData("")
                  .withEnabled(false)
                  .withAccountId(null);
          expansionDataStore.updateAppData(newDataBuilder.build());
        }

        LOGGER.debug("debug=valid-expansion-token service_name={} device_id={}", Expansion.ServiceName.NEST.toString(), deviceId);
      } catch (Exception exception) {
        LOGGER.error("error={}", exception.getMessage());
      } finally {
        LOGGER.debug("message=token-checker-thread-end");
      }

    }
  }
}
