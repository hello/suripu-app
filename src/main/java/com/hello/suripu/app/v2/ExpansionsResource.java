package com.hello.suripu.app.v2;


import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hello.suripu.app.configuration.ExpansionConfiguration;
import com.hello.suripu.app.modules.AppFeatureFlipper;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.speech.interfaces.Vault;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import com.hello.suripu.coredropwizard.resources.BaseResource;
import com.librato.rollout.RolloutClient;
import io.dropwizard.jersey.PATCH;
import is.hello.gaibu.core.db.ExternalAuthorizationStateDAO;
import is.hello.gaibu.core.exceptions.InvalidExternalTokenException;
import is.hello.gaibu.core.models.Configuration;
import is.hello.gaibu.core.models.Expansion;
import is.hello.gaibu.core.models.ExpansionData;
import is.hello.gaibu.core.models.ExpansionDeviceData;
import is.hello.gaibu.core.models.ExternalAuthorizationState;
import is.hello.gaibu.core.models.ExternalToken;
import is.hello.gaibu.core.models.StateRequest;
import is.hello.gaibu.core.stores.ExpansionStore;
import is.hello.gaibu.core.stores.ExternalOAuthTokenStore;
import is.hello.gaibu.core.stores.PersistentExpansionDataStore;
import is.hello.gaibu.core.utils.TokenUtils;
import is.hello.gaibu.homeauto.clients.HueLight;
import is.hello.gaibu.homeauto.factories.HomeAutomationExpansionDataFactory;
import is.hello.gaibu.homeauto.factories.HomeAutomationExpansionFactory;
import is.hello.gaibu.homeauto.interfaces.HomeAutomationExpansion;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/v2/expansions")
public class ExpansionsResource extends BaseResource {

    @Inject
    RolloutClient featureFlipper;

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpansionsResource.class);

    private final ExpansionConfiguration expansionConfig;
    private final ExpansionStore<Expansion> expansionStore;
    private final ExternalAuthorizationStateDAO externalAuthorizationStateDAO;
    private final DeviceDAO deviceDAO;
    private final ExternalOAuthTokenStore<ExternalToken> externalTokenStore;
    private final PersistentExpansionDataStore expansionDataStore;
    private final Vault tokenKMSVault;
    private final OkHttpClient httpClient;

    private final ObjectMapper mapper;

    public ExpansionsResource(
            final ExpansionConfiguration expansionConfig,
            final ExpansionStore<Expansion> expansionStore,
            final ExternalAuthorizationStateDAO externalAuthorizationStateDAO,
            final DeviceDAO deviceDAO,
            final ExternalOAuthTokenStore<ExternalToken> externalTokenStore,
            final PersistentExpansionDataStore expansionDataStore,
            final Vault tokenKMSVault,
            final OkHttpClient httpClient,
            final ObjectMapper mapper) throws Exception{

        this.expansionConfig = expansionConfig;
        this.expansionStore = expansionStore;
        this.externalAuthorizationStateDAO = externalAuthorizationStateDAO;
        this.deviceDAO = deviceDAO;
        this.externalTokenStore = externalTokenStore;
        this.expansionDataStore = expansionDataStore;
        this.tokenKMSVault = tokenKMSVault;
        this.httpClient = httpClient;

        mapper.registerModule(new JodaModule());
        this.mapper = mapper;
    }

    @ScopesAllowed({OAuthScope.EXTERNAL_APPLICATION_READ})
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Expansion> getExpansionsDetail(@Auth final AccessToken token,
                                               @Context UriInfo uriInfo) {
        return getAllExpansions(token, uriInfo);
    }

    @ScopesAllowed({OAuthScope.EXTERNAL_APPLICATION_READ})
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{expansion_id}")
    public List<Expansion> getExpansionDetail(@Auth final AccessToken token,
                                              @PathParam("expansion_id") final Long appId,
                                              @Context UriInfo uriInfo) {
        return getExpansions(token.accountId, appId, uriInfo);
    }

    @ScopesAllowed({OAuthScope.EXTERNAL_APPLICATION_READ})
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{expansion_id}")
    public Response setExpansionState(@Auth final AccessToken accessToken,
                                      @PathParam("expansion_id") final Long appId,
                                      final StateRequest stateRequest) {
        final List<DeviceAccountPair> sensePairedWithAccount = this.deviceDAO.getSensesForAccountId(accessToken.accountId);
        if(sensePairedWithAccount.size() == 0){
            LOGGER.error("error=no-sense-paired account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

        final Optional<Expansion> expansionOptional = expansionStore.getApplicationById(appId);
        if(!expansionOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final Expansion expansion = expansionOptional.get();

        final Optional<ExpansionData> expDataOptional = expansionDataStore.getAppData(expansion.id, deviceId);

        final ExpansionData.Builder newDataBuilder = new ExpansionData.Builder();
        if(expDataOptional.isPresent()) {
            final ExpansionData extData = expDataOptional.get();
            newDataBuilder.withAppId(extData.appId)
            .withDeviceId(extData.deviceId)
            .withData(extData.data);
        }

        if(stateRequest.state.equals(Expansion.State.CONNECTED_OFF)){
            newDataBuilder.withEnabled(false);
            LOGGER.debug("action=expansion-disabled expansion_id={}", appId);
        }

        if(stateRequest.state.equals(Expansion.State.CONNECTED_ON)){
            newDataBuilder.withEnabled(true);
            LOGGER.debug("action=expansion-enabled expansion_id={}", appId);
        }

        if(stateRequest.state.equals(Expansion.State.REVOKED)) {
            newDataBuilder.withEnabled(false)
                    .withData("");
            //Revoke tokens too
            final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(deviceId, expansion.id);
            if (!externalTokenOptional.isPresent()) {
                LOGGER.warn("warning=token-not-found");
                throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
            }

            // Specific Nest use case
            if (Expansion.ServiceName.NEST.equals(expansion.serviceName)) {
                try {

                    final Optional<String> decryptedTokenOptional = TokenUtils.getDecryptedExternalToken(externalTokenStore, tokenKMSVault, deviceId, expansion, false);
                    if(!decryptedTokenOptional.isPresent()) {
                        LOGGER.warn("action=deauth-nest result=fail-to-decrypt-token account_id={}", accessToken.accountId);
                    }

                    final String decryptedToken = decryptedTokenOptional.get();
                    final String url = "https://api.home.nest.com/oauth2/access_tokens/" + decryptedToken;
                    final Request request = new Request.Builder()
                            .url(url)
                            .delete()
                            .build();

                    final okhttp3.Response response = httpClient.newCall(request).execute();
                    response.close();
                    LOGGER.info("action=deauth-nest account_id={} http_resp={} success={}", accessToken.accountId, response.code(), response.isSuccessful());
                } catch (IOException e) {
                    LOGGER.error("error=deauth-nest message={}", e.getMessage());
                }
            }

            externalTokenStore.disableByDeviceId(deviceId, appId);
            LOGGER.debug("action=tokens-revoked");
        }

        final ExpansionData newData = newDataBuilder.build();
        expansionDataStore.updateAppData(newData);
        return Response.ok(stateRequest).build();
    }

    @ScopesAllowed({OAuthScope.EXTERNAL_APPLICATION_READ})
    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("/{expansion_id}/auth")
    public Response getAuthURI(@Auth final AccessToken token,
                               @Context HttpServletRequest request,
                               @PathParam("expansion_id") final Long appId) {

        if(appId == null) {
            LOGGER.warn("warning=null-app-id");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final List<DeviceAccountPair> sensePairedWithAccount = this.deviceDAO.getSensesForAccountId(token.accountId);
        if(sensePairedWithAccount.size() == 0){
            LOGGER.error("error=no-sense-paired account_id={}", token.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

        final Optional<Expansion> expansionOptional = expansionStore.getApplicationById(appId);
        if(!expansionOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final Expansion expansion = expansionOptional.get();

        final UUID stateId = UUID.randomUUID();
        final URI authURI = UriBuilder.fromUri(expansion.authURI)
            .queryParam("client_id", expansion.clientId)
            .queryParam("response_type", "code")
            .queryParam("deviceid", deviceId)
            .queryParam("state", stateId.toString()).build();

        externalAuthorizationStateDAO.storeAuthCode(new ExternalAuthorizationState(stateId, DateTime.now(DateTimeZone.UTC), deviceId, appId));

        LOGGER.debug("action=auth-redirect expansion_id={} auth_uri={} redirect_uri={}", appId, expansion.authURI, authURI.toString());
        return Response.temporaryRedirect(authURI).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("redirect")
    public Response handleRedirectRequest(@Context HttpServletRequest request,
                                          @QueryParam("state") String authState,
                                          @QueryParam("code") String authCode) {

        //Lookup external application by auth_code
        final Optional<ExternalAuthorizationState> externalAuthorizationCodeOptional = externalAuthorizationStateDAO.getByAuthState(authState);
        if(!externalAuthorizationCodeOptional.isPresent()) {
            LOGGER.warn("warning=auth-state-not-found");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final ExternalAuthorizationState authorizationState = externalAuthorizationCodeOptional.get();

        final Optional<Expansion> expansionOptional = expansionStore.getApplicationById(authorizationState.appId);
        if(!expansionOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final Expansion expansion = expansionOptional.get();

        //Make request to TOKEN_URL for access_token
        Client client = ClientBuilder.newClient();
        WebTarget resourceTarget = client.target(UriBuilder.fromUri(expansion.tokenURI).build());
        Invocation.Builder builder = resourceTarget.request();

        Form form = new Form();
        form.param("grant_type", "authorization_code"); //grant_type must be 'authorization_code' per RFC6749
        form.param("code", authCode);
        form.param("client_id", expansion.clientId);
        form.param("client_secret", expansion.clientSecret);

        Response response = builder.accept(MediaType.APPLICATION_JSON)
            .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED));
        final String responseValue = response.readEntity(String.class);

        //TODO: Maybe do this via ObjectMapper
        Gson gson = new Gson();
        Type collectionType = new TypeToken<Map<String, String>>(){}.getType();
//        TypeReference<HashMap<String, String>> typeRef = new TypeReference<HashMap<String, String>>() {};
//        final Map<String, String> responseMap = mapper.readValue(responseValue, typeRef);
        Map<String, String> responseMap = gson.fromJson(responseValue, collectionType);

        if(!responseMap.containsKey("access_token")) {
            LOGGER.error("error=no-access-token-returned");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final Map<String, String> encryptionContext = Maps.newHashMap();
        encryptionContext.put("application_id", expansion.id.toString());

        final String newAccessToken = responseMap.get("access_token");
        final Optional<String> encryptedTokenOptional = tokenKMSVault.encrypt(newAccessToken, encryptionContext);
        if (!encryptedTokenOptional.isPresent()) {
            LOGGER.error("error=token-encryption-failure");
            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
        }

        //Store the access_token & refresh_token (if exists)
        final ExternalToken.Builder tokenBuilder = new ExternalToken.Builder()
            .withAccessToken(encryptedTokenOptional.get())
            .withAppId(expansion.id)
            .withDeviceId(authorizationState.deviceId);

        if(responseMap.containsKey("expires_in")) {
            tokenBuilder.withAccessExpiresIn(Long.parseLong(responseMap.get("expires_in")));
        }

        if(responseMap.containsKey("access_token_expires_in")) {
            tokenBuilder.withAccessExpiresIn(Long.parseLong(responseMap.get("access_token_expires_in")));
        }

        if(responseMap.containsKey("refresh_token")) {
            final Optional<String> encryptedRefreshTokenOptional = tokenKMSVault.encrypt(responseMap.get("refresh_token"), encryptionContext);
            if (!encryptedRefreshTokenOptional.isPresent()) {
                LOGGER.error("error=token-encryption-failure");
                throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
            }
            tokenBuilder.withRefreshToken(encryptedRefreshTokenOptional.get());
        }

        if(responseMap.containsKey("refresh_token_expires_in")) {
            tokenBuilder.withRefreshExpiresIn(Long.parseLong(responseMap.get("refresh_token_expires_in")));
        }

        final ExternalToken externalToken = tokenBuilder.build();

        //Store the externalToken
        try {
            externalTokenStore.storeToken(externalToken);
        } catch (InvalidExternalTokenException ie) {
            LOGGER.error("error=token-not-saved");
            return Response.serverError().build();
        }

        response.close();

        //TODO: Replace this one-off with a more generalized approach to initializing expansions
        if(expansion.serviceName == Expansion.ServiceName.HUE) {

            final Optional<ExpansionData> expDataOptional = expansionDataStore.getAppData(expansion.id, authorizationState.deviceId);

            final HueLight hueLight = HueLight.create(expansionConfig.hueAppName(), newAccessToken);
            final String bridgeId = hueLight.getBridge();

            final HueLight hueLightWithBridge = HueLight.create(expansionConfig.hueAppName(), newAccessToken, bridgeId);
            final Optional<String> whitelistIdOptional = hueLightWithBridge.getWhitelistId();
            if(!whitelistIdOptional.isPresent()) {
                LOGGER.warn("warning=whitelistId-not-found");
                throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
            }

            final String whitelistId = whitelistIdOptional.get();

            final Map<String, String> dataMap = Maps.newHashMap();
            dataMap.put("bridge_id", bridgeId);
            dataMap.put("whitelist_id", whitelistId);

            //Store this information in DB
            final ExpansionData appData = new ExpansionData.Builder()
                .withAppId(externalToken.appId)
                .withDeviceId(authorizationState.deviceId)
                .withData(gson.toJson(dataMap))
                .withEnabled(true)
                .build();
            if(expDataOptional.isPresent()){
                expansionDataStore.updateAppData(appData);
            } else {
                expansionDataStore.insertAppData(appData);
            }
        }

        return Response.ok().build();
    }

    @ScopesAllowed({OAuthScope.EXTERNAL_APPLICATION_READ})
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{expansion_id}/refresh")
    public Response refreshToken(@Auth final AccessToken accessToken,
                                 @Context HttpServletRequest request,
                                 @PathParam("expansion_id") final Long appId) {
        final List<DeviceAccountPair> sensePairedWithAccount = this.deviceDAO.getSensesForAccountId(accessToken.accountId);
        if(sensePairedWithAccount.size() == 0){
            LOGGER.error("error=no-sense-paired account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

        final Optional<Expansion> expansionOptional = expansionStore.getApplicationById(appId);
        if(!expansionOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final Expansion expansion = expansionOptional.get();
        if(expansion.refreshURI.isEmpty()) {
            LOGGER.warn("warning=token-refresh-not-allowed expansion_id={}", expansion.id);
            throw new WebApplicationException(Response.status(Response.Status.FORBIDDEN).build());
        }

        final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(deviceId, expansion.id);
        if(!externalTokenOptional.isPresent()) {
            LOGGER.warn("warning=token-not-found");
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }
        final ExternalToken externalToken = externalTokenOptional.get();

        final Optional<String> decryptedAccessTokenOptional = TokenUtils.getDecryptedExternalToken(externalTokenStore, tokenKMSVault, deviceId, expansion, false);
        if(!decryptedAccessTokenOptional.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
        final String decryptedAccessToken = decryptedAccessTokenOptional.get();

        final Optional<String> decryptedRefreshTokenOptional = TokenUtils.getDecryptedExternalToken(externalTokenStore, tokenKMSVault, deviceId, expansion, true);
        if(!decryptedRefreshTokenOptional.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
        final String decryptedRefreshToken = decryptedRefreshTokenOptional.get();

        //Make request to TOKEN_URL for access_token
        Client client = ClientBuilder.newClient();
        WebTarget resourceTarget = client.target(UriBuilder.fromUri(expansion.refreshURI).build());
        Invocation.Builder builder = resourceTarget.request();

        final Form form = new Form();
        form.param("refresh_token", decryptedRefreshToken);
        form.param("client_id", expansion.clientId);
        form.param("client_secret", expansion.clientSecret);

        //Hue documentation does NOT mention that this needs to be done for token refresh, but it does
        final String clientCreds = expansion.clientId + ":" + expansion.clientSecret;
        final byte[] encodedBytes = Base64.encodeBase64(clientCreds.getBytes());
        final String encodedClientCreds = new String(encodedBytes);

        Response response = builder.accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Basic " + encodedClientCreds)
            .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED));
        final String responseValue = response.readEntity(String.class);

        final Gson gson = new Gson();
        final Type collectionType = new TypeToken<Map<String, String>>(){}.getType();
        final Map<String, String> responseJson = gson.fromJson(responseValue, collectionType);

        if(!responseJson.containsKey("access_token")) {
            LOGGER.error("error=no-access-token-returned");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        //Invalidate current token
        externalTokenStore.disableByRefreshToken(externalToken.refreshToken);


        final Map<String, String> encryptionContext = Maps.newHashMap();
        encryptionContext.put("application_id", expansion.id.toString());

        final Optional<String> encryptedTokenOptional = tokenKMSVault.encrypt(responseJson.get("access_token"), encryptionContext);
        if (!encryptedTokenOptional.isPresent()) {
            LOGGER.error("error=token-encryption-failure");
            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
        }

        //Store the access_token & refresh_token (if exists)
        final ExternalToken.Builder tokenBuilder = new ExternalToken.Builder()
            .withAccessToken(encryptedTokenOptional.get())
            .withAppId(expansion.id)
            .withDeviceId(deviceId);

        if(responseJson.containsKey("expires_in")) {
            tokenBuilder.withAccessExpiresIn(Long.parseLong(responseJson.get("expires_in")));
        }

        if(responseJson.containsKey("access_token_expires_in")) {
            tokenBuilder.withAccessExpiresIn(Long.parseLong(responseJson.get("access_token_expires_in")));
        }

        if(responseJson.containsKey("refresh_token")) {
            final Optional<String> encryptedRefreshTokenOptional = tokenKMSVault.encrypt(responseJson.get("refresh_token"), encryptionContext);
            if (!encryptedRefreshTokenOptional.isPresent()) {
                LOGGER.error("error=token-encryption-failure");
                throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
            }
            tokenBuilder.withRefreshToken(encryptedRefreshTokenOptional.get());
        }

        if(responseJson.containsKey("refresh_token_expires_in")) {
            tokenBuilder.withRefreshExpiresIn(Long.parseLong(responseJson.get("refresh_token_expires_in")));
        }

        final ExternalToken newExternalToken = tokenBuilder.build();

        //Store the externalToken
        try {
            externalTokenStore.storeToken(newExternalToken);
        } catch (InvalidExternalTokenException ie) {
            LOGGER.error("error=token-not-saved");
            return Response.serverError().build();
        }

        response.close();
        return Response.ok().build();
    }

    @ScopesAllowed({OAuthScope.EXTERNAL_APPLICATION_READ})
    @GET
    @Timed
    @Path("/{app_id}/configuration")
    @Produces(MediaType.APPLICATION_JSON)
    public Optional<Configuration> getConfiguration(@Auth final AccessToken accessToken,
                                                 @PathParam("app_id") Long appId) {
        final List<DeviceAccountPair> sensePairedWithAccount = this.deviceDAO.getSensesForAccountId(accessToken.accountId);
        if (sensePairedWithAccount.size() == 0) {
            LOGGER.error("error=no-sense-paired account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

        final Optional<Expansion> expansionInfoOptional = expansionStore.getApplicationById(appId);
        if (!expansionInfoOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final Expansion expansionInfo = expansionInfoOptional.get();

        final Optional<ExpansionData> expDataOptional = expansionDataStore.getAppData(expansionInfo.id, deviceId);

        ExpansionData extData = new ExpansionData.Builder().withData("").build();

        if(expDataOptional.isPresent()) {
            extData = expDataOptional.get();
        }

        //Enumerate devices on a service-specific basis
        final Optional<String> decryptedTokenOptional = TokenUtils.getDecryptedExternalToken(externalTokenStore, tokenKMSVault, deviceId, expansionInfo, false);
        if(!decryptedTokenOptional.isPresent()) {
            return Optional.absent();
        }

        final String decryptedToken = decryptedTokenOptional.get();
        Optional<ExpansionDeviceData> appDataOptional;
        appDataOptional = HomeAutomationExpansionDataFactory.getAppData(mapper, extData.data, expansionInfo.serviceName);
        if(!appDataOptional.isPresent()){
            LOGGER.warn("warning=empty-expansion-data expansion_name={} device_id={}", expansionInfo.serviceName, deviceId);
            appDataOptional = HomeAutomationExpansionDataFactory.getEmptyAppData(expansionInfo.serviceName);
        }

        final ExpansionDeviceData appData = appDataOptional.get();

        final Optional<HomeAutomationExpansion> expansionOptional = HomeAutomationExpansionFactory.getEmptyExpansion(expansionConfig.hueAppName(), expansionInfo.serviceName, appData, decryptedToken);
        if(!expansionOptional.isPresent()){
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
        }

        final HomeAutomationExpansion expansion = expansionOptional.get();
        final Optional<Configuration> config = expansion.getSelectedConfiguration(extData);
        return config;

    }

    @ScopesAllowed({OAuthScope.EXTERNAL_APPLICATION_READ})
    @GET
    @Timed
    @Path("/{app_id}/configurations")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Configuration> getConfigurations(@Auth final AccessToken accessToken,
                             @PathParam("app_id") Long appId) {
        final List<DeviceAccountPair> sensePairedWithAccount = this.deviceDAO.getSensesForAccountId(accessToken.accountId);
        if(sensePairedWithAccount.size() == 0){
            LOGGER.error("error=no-sense-paired account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

        final Optional<Expansion> expansionInfoOptional = expansionStore.getApplicationById(appId);
        if(!expansionInfoOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
        }

        final Expansion expansionInfo = expansionInfoOptional.get();

        final Optional<ExpansionData> expDataOptional = expansionDataStore.getAppData(expansionInfo.id, deviceId);

        ExpansionData extData = new ExpansionData.Builder().withData("").build();

        if(expDataOptional.isPresent()) {
            extData = expDataOptional.get();
        }

        //Enumerate devices on a service-specific basis
        final Optional<String> decryptedTokenOptional = TokenUtils.getDecryptedExternalToken(externalTokenStore, tokenKMSVault, deviceId, expansionInfo, false);
        if(!decryptedTokenOptional.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final String decryptedToken = decryptedTokenOptional.get();
        Optional<ExpansionDeviceData> appDataOptional;
        appDataOptional = HomeAutomationExpansionDataFactory.getAppData(mapper, extData.data, expansionInfo.serviceName);
        if(!appDataOptional.isPresent()){
            LOGGER.warn("warning=empty-expansion-data expansion_name={} device_id={}", expansionInfo.serviceName, deviceId);
            appDataOptional = HomeAutomationExpansionDataFactory.getEmptyAppData(expansionInfo.serviceName);
        }

        final ExpansionDeviceData appData = appDataOptional.get();

        final Optional<HomeAutomationExpansion> expansionOptional = HomeAutomationExpansionFactory.getEmptyExpansion(expansionConfig.hueAppName(), expansionInfo.serviceName, appData, decryptedToken);
        if(!expansionOptional.isPresent()){
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
        }

        final HomeAutomationExpansion expansion = expansionOptional.get();
        final List<Configuration> configs = expansion.getConfigurations();

        if(extData.data.isEmpty()) {
            return configs;
        }

        //Set state if we have a configuration selected already
        for(final Configuration config : configs) {
            if(config.getId().equals(appData.getId())) {
                config.setSelected(true);
            }
        }

        return configs;
    }

    @ScopesAllowed({OAuthScope.EXTERNAL_APPLICATION_READ})
    @PATCH
    @Timed
    @Path("/{app_id}/configurations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Configuration setConfiguration(@Auth final AccessToken accessToken,
                                          @PathParam("app_id") Long appId,
                                          final Configuration configuration) {
        if(configuration.getId() == null) {
            LOGGER.error("error=no-config-id account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
        }

        final List<DeviceAccountPair> sensePairedWithAccount = this.deviceDAO.getSensesForAccountId(accessToken.accountId);
        if(sensePairedWithAccount.size() == 0){
            LOGGER.error("error=no-sense-paired account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

        final Optional<Expansion> expansionOptional = expansionStore.getApplicationById(appId);
        if(!expansionOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
        }

        final Expansion expansion = expansionOptional.get();

        final Optional<ExpansionData> expDataOptional = expansionDataStore.getAppData(expansion.id, deviceId);
        if(!expDataOptional.isPresent()) {
            LOGGER.error("error=no-ext-app-data account_id={}", accessToken.accountId);
        }

        try {
            final Optional<ExpansionDeviceData> appDataOptional;
            if(expDataOptional.isPresent() && !expDataOptional.get().data.isEmpty()) {
                final ExpansionData expData = expDataOptional.get();
                appDataOptional = HomeAutomationExpansionDataFactory.getAppData(mapper, expData.data, expansion.serviceName);
            }else {
                appDataOptional = HomeAutomationExpansionDataFactory.getEmptyAppData(expansion.serviceName);
            }

            if(!appDataOptional.isPresent()) {
                LOGGER.error("error=bad-expansion-data account_id={}", accessToken.accountId);
                throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
            }

            final ExpansionDeviceData appData = appDataOptional.get();

            appData.setId(configuration.getId());
            appData.setName(configuration.getName());

            final ExpansionData newData = new ExpansionData.Builder()
                .withAppId(expansion.id)
                .withDeviceId(deviceId)
                .withData(mapper.writeValueAsString(appData))
                .withEnabled(true)
                .build();
            if(expDataOptional.isPresent()){
                expansionDataStore.updateAppData(newData);
            } else {
                expansionDataStore.insertAppData(newData);
            }
            configuration.setSelected(true);
            return configuration;
        } catch (IOException io) {
            LOGGER.warn("warn=bad-json-data");
        }

        return configuration;

    }

    private Expansion.State getStateFromExternalAppId(final Long appId, final String deviceId) {

        final Optional<Expansion> expansionOptional = expansionStore.getApplicationById(appId);
        if(!expansionOptional.isPresent()) {
            LOGGER.warn("warning=expansion-not-found expansion_id={} device_id={}", appId, deviceId);
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        final Expansion expansion = expansionOptional.get();

        final Integer externalTokenCount = externalTokenStore.getTokenCount(deviceId, appId);
        if(externalTokenCount < 1) {
            LOGGER.warn("warning=token-not-found expansion_id={} device_id={}", appId, deviceId);
            return Expansion.State.NOT_CONNECTED;
        }

        final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(deviceId, appId);
        if(!externalTokenOptional.isPresent()) {
            LOGGER.warn("warning=valid-token-not-found expansion_id={} device_id={}", appId, deviceId);
            return Expansion.State.REVOKED;
        }

        final Optional<ExpansionData> expDataOptional = expansionDataStore.getAppData(appId, deviceId);
        if(!expDataOptional.isPresent()) {
            LOGGER.error("error=no-ext-app-data expansion_id={} device_id={}", appId, deviceId);
            return Expansion.State.NOT_CONFIGURED;
        }

        final ExpansionData expData = expDataOptional.get();

        if(expData.data.isEmpty()){
            LOGGER.error("error=no-ext-app-data expansion_id={} device_id={}", appId, deviceId);
            return Expansion.State.NOT_CONFIGURED;
        }

        final Optional<ExpansionDeviceData> expansionDeviceDataOptional = HomeAutomationExpansionDataFactory.getAppData(mapper, expData.data, expansion.serviceName);

        if(!expansionDeviceDataOptional.isPresent()){
            LOGGER.error("error=bad-expansion-data expansion_id={} device_id={}", appId, deviceId);
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
        }

        final ExpansionDeviceData appData = expansionDeviceDataOptional.get();

        if(appData.getId().isEmpty()) {
            LOGGER.error("error=no-ext-app-device-id expansion_id={} device_id={}", appId, deviceId);
            return Expansion.State.NOT_CONFIGURED;
        }

        if(!expData.enabled) {
            return Expansion.State.CONNECTED_OFF;
        }

        return Expansion.State.CONNECTED_ON;
    }

    private List<Expansion> getAllExpansions(final AccessToken accessToken, final UriInfo uriInfo) {
        return getExpansions(accessToken.accountId, 0L, uriInfo);
    }

    private List<Expansion> getExpansions(final Long accountId, final Long appId, final UriInfo uriInfo) {
        final List<DeviceAccountPair> sensePairedWithAccount = this.deviceDAO.getSensesForAccountId(accountId);
        if(sensePairedWithAccount.size() == 0){
            LOGGER.error("error=no-sense-paired account_id={}", accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final String senseId = sensePairedWithAccount.get(0).externalDeviceId;

        final List<Expansion> expansions = Lists.newArrayList();

        if(appId > 0L) {
            final Optional<Expansion> expansionOptional = expansionStore.getApplicationById(appId);
            if(!expansionOptional.isPresent()) {
                LOGGER.warn("warning=application-not-found app_id={} account_id={}", appId, accountId);
                throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
            }
            expansions.add(expansionOptional.get());
        } else {
            expansions.addAll(expansionStore.getAll());
        }

        final List<Expansion> updatedExpansions = Lists.newArrayList();

        final String baseURI = uriInfo.getBaseUriBuilder().path(ExpansionsResource.class).build().toString();
        for(final Expansion exp : expansions) {

            Expansion.State state =  getStateFromExternalAppId(exp.id, senseId);
            if(!isEnabled(exp, senseId) && state.equals(Expansion.State.NOT_CONNECTED)) {
                LOGGER.warn("warning=expansion_not_available expansion={} device_id={}", exp.serviceName, senseId);
                state = Expansion.State.NOT_AVAILABLE;
            }

            final Expansion newExpansion = new Expansion.Builder()
                .withId(exp.id)
                .withServiceName(exp.serviceName)
                .withDeviceName(exp.deviceName)
                .withCompanyName(exp.companyName)
                .withDescription(exp.description)
                .withCategory(exp.category)
                .withIcon(exp.icon)
                .withState(state)
                .withCompletionURI(baseURI + "/redirect")
                .withAuthURI(String.format("%s/%s/auth", baseURI, exp.id.toString()))
                .withApiURI(exp.apiURI)
                .withValueRange(HomeAutomationExpansionFactory.getValueRangeByServiceName(exp.serviceName))
                .build();
            updatedExpansions.add(newExpansion);
        }

        return updatedExpansions;
    }

    private Boolean isEnabled(final Expansion expansion, final String senseId) {
        boolean isNest = expansion.serviceName.equals(Expansion.ServiceName.NEST);
        if(isNest && !hasNest(senseId)) {
            return false;
        }
        return true;
    }

    private boolean hasNest(final String senseId) {
        return featureFlipper.deviceFeatureActive(AppFeatureFlipper.NEST_ENABLED, senseId, Collections.emptyList());
    }
}
