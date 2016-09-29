package com.hello.suripu.app.v2;


import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.hello.suripu.core.db.DeviceDAO;
import com.hello.suripu.core.models.DeviceAccountPair;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.speech.interfaces.Vault;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;

import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
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

import io.dropwizard.jersey.PATCH;
import is.hello.gaibu.core.db.ExternalAuthorizationStateDAO;
import is.hello.gaibu.core.exceptions.InvalidExternalTokenException;
import is.hello.gaibu.core.models.ApplicationData;
import is.hello.gaibu.core.models.Configuration;
import is.hello.gaibu.core.models.Expansion;
import is.hello.gaibu.core.models.ExternalApplication;
import is.hello.gaibu.core.models.ExternalApplicationData;
import is.hello.gaibu.core.models.ExternalAuthorizationState;
import is.hello.gaibu.core.models.ExternalToken;
import is.hello.gaibu.core.models.MultiDensityImage;
import is.hello.gaibu.core.models.StateRequest;
import is.hello.gaibu.core.stores.ExternalApplicationStore;
import is.hello.gaibu.core.stores.ExternalOAuthTokenStore;
import is.hello.gaibu.core.stores.PersistentExternalAppDataStore;
import is.hello.gaibu.homeauto.factories.HomeAutomationExpansionDataFactory;
import is.hello.gaibu.homeauto.factories.HomeAutomationExpansionFactory;
import is.hello.gaibu.homeauto.interfaces.HomeAutomationExpansion;
import is.hello.gaibu.homeauto.models.HueApplicationData;
import is.hello.gaibu.homeauto.models.NestApplicationData;
import is.hello.gaibu.homeauto.services.HueLight;
import is.hello.gaibu.homeauto.services.NestThermostat;

@Path("/v2/expansions")
public class ExpansionsResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExpansionsResource.class);
    private final ExternalApplicationStore<ExternalApplication> externalApplicationStore;
    private final ExternalAuthorizationStateDAO externalAuthorizationStateDAO;
    private final DeviceDAO deviceDAO;
    private final ExternalOAuthTokenStore<ExternalToken> externalTokenStore;
    private final PersistentExternalAppDataStore externalAppDataStore;
    private final Vault tokenKMSVault;

    private ObjectMapper mapper = new ObjectMapper();

    public ExpansionsResource(
            final ExternalApplicationStore<ExternalApplication> externalApplicationStore,
            final ExternalAuthorizationStateDAO externalAuthorizationStateDAO,
            final DeviceDAO deviceDAO,
            final ExternalOAuthTokenStore<ExternalToken> externalTokenStore,
            final PersistentExternalAppDataStore externalAppDataStore,
            final Vault tokenKMSVault) throws Exception{

        this.externalApplicationStore = externalApplicationStore;
        this.externalAuthorizationStateDAO = externalAuthorizationStateDAO;
        this.deviceDAO = deviceDAO;
        this.externalTokenStore = externalTokenStore;
        this.externalAppDataStore = externalAppDataStore;
        this.tokenKMSVault = tokenKMSVault;

        mapper.registerModule(new JodaModule());
    }

    @ScopesAllowed({OAuthScope.EXTERNAL_APPLICATION_READ})
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public List<Expansion> getExpansionsDetail(@Auth final AccessToken token) {
        return getAllExpansions(token);
    }

    @ScopesAllowed({OAuthScope.EXTERNAL_APPLICATION_READ})
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{external_app_id}")
    public List<Expansion> getExpansionDetail(@Auth final AccessToken token,
                                     @PathParam("external_app_id") final Long appId) {
        return getExpansions(token, appId);
    }

    @ScopesAllowed({OAuthScope.EXTERNAL_APPLICATION_READ})
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{external_app_id}")
    public Response setExpansionState(@Auth final AccessToken accessToken,
                                      @PathParam("external_app_id") final Long appId,
                                      final StateRequest stateRequest) {
        final List<DeviceAccountPair> sensePairedWithAccount = this.deviceDAO.getSensesForAccountId(accessToken.accountId);
        if(sensePairedWithAccount.size() == 0){
            LOGGER.error("error=no-sense-paired account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

        final Optional<ExternalApplication> externalApplicationOptional = externalApplicationStore.getApplicationById(appId);
        if(!externalApplicationOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final ExternalApplication externalApplication = externalApplicationOptional.get();

        //Check to see if we need to whitelist
        final Optional<ExternalApplicationData> extAppDataOptional = externalAppDataStore.getAppData(externalApplication.id, deviceId);
        if(!extAppDataOptional.isPresent()) {
            LOGGER.error("error=no-ext-app-data account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final ExternalApplicationData extData = extAppDataOptional.get();

        final ExternalApplicationData.Builder newDataBuilder = new ExternalApplicationData.Builder()
            .withAppId(extData.appId)
            .withDeviceId(extData.deviceId)
            .withData(extData.data);

        if(stateRequest.state.equals(Expansion.State.CONNECTED_OFF)){
            newDataBuilder.withEnabled(false);
            LOGGER.debug("action=expansion-disabled expansion_id={}", appId);
        }

        if(stateRequest.state.equals(Expansion.State.CONNECTED_ON)){
            newDataBuilder.withEnabled(true);
            LOGGER.debug("action=expansion-enabled expansion_id={}", appId);
        }

        if(stateRequest.state.equals(Expansion.State.REVOKED)){
            newDataBuilder.withEnabled(false)
            .withData("");
            //Revoke tokens too
            final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(deviceId, externalApplication.id);
            if(!externalTokenOptional.isPresent()) {
                LOGGER.warn("warning=token-not-found");
                throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
            }
            final ExternalToken externalToken = externalTokenOptional.get();
            externalTokenStore.disable(externalToken);
            LOGGER.debug("action=tokens-revoked");
        }

        final ExternalApplicationData newData = newDataBuilder.build();
        externalAppDataStore.updateAppData(newData);
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

        final Optional<ExternalApplication> externalApplicationOptional = externalApplicationStore.getApplicationById(appId);
        if(!externalApplicationOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final ExternalApplication externalApplication = externalApplicationOptional.get();

        final UUID stateId = UUID.randomUUID();
        final URI authURI = UriBuilder.fromUri(externalApplication.authURI)
            .queryParam("client_id", externalApplication.clientId)
            .queryParam("response_type", "code")
            .queryParam("deviceid", deviceId)
            .queryParam("state", stateId.toString()).build();

        externalAuthorizationStateDAO.storeAuthCode(new ExternalAuthorizationState(stateId, DateTime.now(DateTimeZone.UTC), deviceId, appId));

        LOGGER.debug("Application ID: {}, ClientID: {}", appId, externalApplication.clientId);
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

        final Optional<ExternalApplication> externalApplicationOptional = externalApplicationStore.getApplicationById(authorizationState.appId);
        if(!externalApplicationOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final ExternalApplication externalApplication = externalApplicationOptional.get();

        //Make request to TOKEN_URL for access_token
        Client client = ClientBuilder.newClient();
        WebTarget resourceTarget = client.target(UriBuilder.fromUri(externalApplication.tokenURI).build());
        Invocation.Builder builder = resourceTarget.request();

        Form form = new Form();
        form.param("grant_type", "authorization_code"); //grant_type must be 'authorization_code' per RFC6749
        form.param("code", authCode);
        form.param("client_id", externalApplication.clientId);
        form.param("client_secret", externalApplication.clientSecret);

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
        encryptionContext.put("application_id", externalApplication.id.toString());

        final Optional<String> encryptedTokenOptional = tokenKMSVault.encrypt(responseMap.get("access_token"), encryptionContext);
        if (!encryptedTokenOptional.isPresent()) {
            LOGGER.error("error=token-encryption-failure");
            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
        }

        //Store the access_token & refresh_token (if exists)
        final ExternalToken.Builder tokenBuilder = new ExternalToken.Builder()
            .withAccessToken(encryptedTokenOptional.get())
            .withAppId(externalApplication.id)
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

        final Optional<ExternalApplication> externalApplicationOptional = externalApplicationStore.getApplicationById(appId);
        if(!externalApplicationOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final ExternalApplication externalApplication = externalApplicationOptional.get();
        if(externalApplication.refreshURI.isEmpty()) {
            LOGGER.warn("warning=token-refresh-not-allowed expansion_id={}", externalApplication.id);
            throw new WebApplicationException(Response.status(Response.Status.FORBIDDEN).build());
        }

        final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(deviceId, externalApplication.id);
        if(!externalTokenOptional.isPresent()) {
            LOGGER.warn("warning=token-not-found");
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }
        final ExternalToken externalToken = externalTokenOptional.get();

        final String decryptedAccessToken = getDecryptedExternalToken(deviceId, externalApplication.id, false);
        final String decryptedRefreshToken = getDecryptedExternalToken(deviceId, externalApplication.id, true);

        //Make request to TOKEN_URL for access_token
        Client client = ClientBuilder.newClient();
        WebTarget resourceTarget = client.target(UriBuilder.fromUri(externalApplication.refreshURI).build());
        Invocation.Builder builder = resourceTarget.request();

        final Form form = new Form();
        form.param("refresh_token", decryptedRefreshToken);
        form.param("client_id", externalApplication.clientId);
        form.param("client_secret", externalApplication.clientSecret);

        //Hue documentation does NOT mention that this needs to be done for token refresh, but it does
        final String clientCreds = externalApplication.clientId + ":" + externalApplication.clientSecret;
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
        encryptionContext.put("application_id", externalApplication.id.toString());

        final Optional<String> encryptedTokenOptional = tokenKMSVault.encrypt(responseJson.get("access_token"), encryptionContext);
        if (!encryptedTokenOptional.isPresent()) {
            LOGGER.error("error=token-encryption-failure");
            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
        }

        //Store the access_token & refresh_token (if exists)
        final ExternalToken.Builder tokenBuilder = new ExternalToken.Builder()
            .withAccessToken(encryptedTokenOptional.get())
            .withAppId(externalApplication.id)
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

    //TODO: Handle whitelisting in the redirect endpoint
    @ScopesAllowed({OAuthScope.EXTERNAL_APPLICATION_READ})
    @GET
    @Timed
    @Path("hue/whitelist")
    @Produces(MediaType.APPLICATION_JSON)
    public ExternalApplicationData getWhitelist(@Auth final AccessToken accessToken) {

        final List<DeviceAccountPair> sensePairedWithAccount = this.deviceDAO.getSensesForAccountId(accessToken.accountId);
        if(sensePairedWithAccount.size() == 0){
            LOGGER.error("error=no-sense-paired account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

        final Optional<ExternalApplication> externalApplicationOptional = externalApplicationStore.getApplicationByName("Hue");
        if(!externalApplicationOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final ExternalApplication externalApplication = externalApplicationOptional.get();

        //Check to see if we need to whitelist
        final Optional<ExternalApplicationData> extAppDataOptional = externalAppDataStore.getAppData(externalApplication.id, deviceId);
        if(extAppDataOptional.isPresent() && !extAppDataOptional.get().data.isEmpty()) {
            return extAppDataOptional.get();
        }

        final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(deviceId, externalApplication.id);
        if(!externalTokenOptional.isPresent()) {
            LOGGER.warn("warning=token-not-found");
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final ExternalToken externalToken = externalTokenOptional.get();
        final String decryptedToken = getDecryptedExternalToken(deviceId, externalApplication.id, false);
        final HueLight hueLight = new HueLight(decryptedToken);
        final String bridgeId = hueLight.getBridge(decryptedToken);

        final Optional<String> whitelistIdOptional = hueLight.getWhitelistId(bridgeId, decryptedToken);
        if(!whitelistIdOptional.isPresent()) {
            LOGGER.warn("warning=whitelistId-not-found");
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final String whitelistId = whitelistIdOptional.get();

        final Map<String, String> dataMap = Maps.newHashMap();
        dataMap.put("bridge_id", bridgeId);
        dataMap.put("whitelist_id", whitelistId);

        final Gson gson = new Gson();

        //Store this information in DB
        final ExternalApplicationData appData = new ExternalApplicationData.Builder()
            .withAppId(externalToken.appId)
            .withDeviceId(deviceId)
            .withData(gson.toJson(dataMap))
            .withEnabled(true)
            .build();
        if(extAppDataOptional.isPresent()){
            externalAppDataStore.updateAppData(appData);
        } else {
            externalAppDataStore.insertAppData(appData);
        }


        return appData;
    }

    @ScopesAllowed({OAuthScope.EXTERNAL_APPLICATION_WRITE})
    @POST
    @Timed
    @Path("hue/state")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setHueState(@Auth final AccessToken accessToken,
                             @QueryParam("light_on") Boolean lightOn,
                             @QueryParam("adjust_bright") Integer adjBright,
                             @QueryParam("adjust_temp") Integer adjTemp) {
        final Optional<HueLight> hueLightOptional = getHueFromToken(accessToken);
        if(!hueLightOptional.isPresent()) {
            LOGGER.error("error=failure-fetching-hue account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final HueLight hueLight = hueLightOptional.get();

        if(lightOn != null) {
            hueLight.setLightState(lightOn);
        }

        if(adjBright != null) {
            hueLight.adjustBrightness(adjBright);
        }

        if(adjTemp != null) {
            hueLight.adjustTemperature(adjTemp);
        }

        return Response.ok().build();
    }

    @ScopesAllowed({OAuthScope.EXTERNAL_APPLICATION_WRITE})
    @PATCH
    @Timed
    @Path("nest/state")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setNestState(@Auth final AccessToken accessToken,
                                 @QueryParam("temp") Integer temperature) {
        final Optional<NestThermostat> nestThermostatOptional = getNestFromToken(accessToken);
        if(!nestThermostatOptional.isPresent()) {
            LOGGER.error("error=failure-fetching-nest account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final NestThermostat nestThermostat = nestThermostatOptional.get();

        if(temperature != null) {
            nestThermostat.setTargetTemperature(temperature);
        }

        return Response.ok().build();
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
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

        final Optional<ExternalApplication> externalApplicationOptional = externalApplicationStore.getApplicationById(appId);
        if(!externalApplicationOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final ExternalApplication externalApplication = externalApplicationOptional.get();

        final Optional<ExternalApplicationData> extAppDataOptional = externalAppDataStore.getAppData(externalApplication.id, deviceId);

        ExternalApplicationData extData = new ExternalApplicationData.Builder().withData("").build();

        if(extAppDataOptional.isPresent()) {
            extData = extAppDataOptional.get();
        }

        //Enumerate devices on a service-specific basis
        final String decryptedToken = getDecryptedExternalToken(deviceId, externalApplication.id, false);
        final ApplicationData appData = HomeAutomationExpansionDataFactory.getAppData(mapper, extData.data, externalApplication.serviceName);

        final HomeAutomationExpansion expansion = HomeAutomationExpansionFactory.getEmptyExpansion(externalApplication.serviceName, appData, decryptedToken);

        return expansion.getConfigurations();
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
            throw new WebApplicationException(Response.status(Response.Status.NOT_ACCEPTABLE).build());
        }

        final List<DeviceAccountPair> sensePairedWithAccount = this.deviceDAO.getSensesForAccountId(accessToken.accountId);
        if(sensePairedWithAccount.size() == 0){
            LOGGER.error("error=no-sense-paired account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

        final Optional<ExternalApplication> externalApplicationOptional = externalApplicationStore.getApplicationById(appId);
        if(!externalApplicationOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final ExternalApplication externalApplication = externalApplicationOptional.get();

        final Optional<ExternalApplicationData> extAppDataOptional = externalAppDataStore.getAppData(externalApplication.id, deviceId);
        if(!extAppDataOptional.isPresent()) {
            LOGGER.error("error=no-ext-app-data account_id={}", accessToken.accountId);
        }

        ExternalApplicationData extData = new ExternalApplicationData.Builder()
            .withData("")
            .build();

        if(extAppDataOptional.isPresent()) {
            extData = extAppDataOptional.get();
        }

        try {
            final ApplicationData appData;
            if(extAppDataOptional.isPresent()) {
                extData = extAppDataOptional.get();
                appData = HomeAutomationExpansionDataFactory.getAppData(mapper, extData.data, externalApplication.serviceName);
            }else {
                appData = HomeAutomationExpansionDataFactory.getEmptyAppData(externalApplication.serviceName);
            }

            appData.setId(configuration.getId());
            final ExternalApplicationData newData = new ExternalApplicationData.Builder()
                .withAppId(externalApplication.id)
                .withDeviceId(deviceId)
                .withData(mapper.writeValueAsString(appData))
                .withEnabled(true)
                .build();
            if(extAppDataOptional.isPresent()){
                externalAppDataStore.updateAppData(newData);
            } else {
                externalAppDataStore.insertAppData(newData);
            }
        } catch (IOException io) {
            LOGGER.warn("warn=bad-json-data");
        }

        return configuration;

    }

    private String getDecryptedExternalToken(final String deviceId, final Long applicationId, final Boolean isRefreshToken) {
        final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(deviceId, applicationId);
        if(!externalTokenOptional.isPresent()) {
            LOGGER.warn("warning=token-not-found");
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final ExternalToken externalToken = externalTokenOptional.get();

        final Map<String, String> encryptionContext = Maps.newHashMap();
        encryptionContext.put("application_id", externalToken.appId.toString());
        final Optional<String> decryptedTokenOptional;
        if(isRefreshToken) {
            decryptedTokenOptional = tokenKMSVault.decrypt(externalToken.refreshToken, encryptionContext);
        } else {
            decryptedTokenOptional = tokenKMSVault.decrypt(externalToken.accessToken, encryptionContext);
        }


        if(!decryptedTokenOptional.isPresent()) {
            LOGGER.error("error=token-decryption-failure device_id={}", deviceId);
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
        return decryptedTokenOptional.get();
    }


    private Optional<HueLight> getHueFromToken(final AccessToken accessToken) {
        final List<DeviceAccountPair> sensePairedWithAccount = this.deviceDAO.getSensesForAccountId(accessToken.accountId);
        if(sensePairedWithAccount.size() == 0){
            LOGGER.error("error=no-sense-paired account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

        final Optional<ExternalApplication> externalApplicationOptional = externalApplicationStore.getApplicationByName("Hue");
        if(!externalApplicationOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final ExternalApplication externalApplication = externalApplicationOptional.get();

        //Check to see if we need to whitelist
        final Optional<ExternalApplicationData> extAppDataOptional = externalAppDataStore.getAppData(externalApplication.id, deviceId);
        if(!extAppDataOptional.isPresent()) {
            LOGGER.error("error=no-ext-app-data account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final ExternalApplicationData extData = extAppDataOptional.get();

        final HueApplicationData hueData = (HueApplicationData) HomeAutomationExpansionDataFactory.getAppData(mapper, extData.data, "Hue");

        final String decryptedToken = getDecryptedExternalToken(deviceId, externalApplication.id, false);
        if(hueData.groupId == null || hueData.groupId < 1) {
            LOGGER.warn("warn=no-hue-group-defined message='Defaulting to single light control'");
            return Optional.of(new HueLight(HueLight.DEFAULT_API_PATH, decryptedToken, hueData.bridgeId, hueData.whitelistId));
        }
        return Optional.of(new HueLight(HueLight.DEFAULT_API_PATH, decryptedToken, hueData.bridgeId, hueData.whitelistId, hueData.groupId));

    }

    private Optional<NestThermostat> getNestFromToken(final AccessToken accessToken) {
        final List<DeviceAccountPair> sensePairedWithAccount = this.deviceDAO.getSensesForAccountId(accessToken.accountId);
        if(sensePairedWithAccount.size() == 0){
            LOGGER.error("error=no-sense-paired account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

        final Optional<ExternalApplication> externalApplicationOptional = externalApplicationStore.getApplicationByName("Nest");
        if(!externalApplicationOptional.isPresent()) {
            LOGGER.warn("warning=application-not-found");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        final ExternalApplication externalApplication = externalApplicationOptional.get();

        //Check to see if we need to whitelist
        final Optional<ExternalApplicationData> extAppDataOptional = externalAppDataStore.getAppData(externalApplication.id, deviceId);
        if(!extAppDataOptional.isPresent()) {
            LOGGER.error("error=no-ext-app-data account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final ExternalApplicationData extData = extAppDataOptional.get();

        try {
            final NestApplicationData nestData = mapper.readValue(extData.data, NestApplicationData.class);
            final String decryptedToken = getDecryptedExternalToken(deviceId, externalApplication.id, false);
            if(nestData.thermostatId == null) {
                LOGGER.warn("warn=no-thermostat-defined");
                return Optional.absent();
            }
            return Optional.of(new NestThermostat(nestData.thermostatId, NestThermostat.DEFAULT_API_PATH, decryptedToken));

        } catch (IOException io) {
            LOGGER.warn("warn=bad-json-data");
        }

        return Optional.absent();
    }

    private Expansion.State getStateFromExternalAppId(final Long appId, final String deviceId) {

        final Integer externalTokenCount = externalTokenStore.getTokenCount(deviceId, appId);
        if(externalTokenCount < 1) {
            LOGGER.warn("warning=token-not-found");
            return Expansion.State.NOT_CONNECTED;
        }

        final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(deviceId, appId);
        if(!externalTokenOptional.isPresent()) {
            LOGGER.warn("warning=valid-token-not-found");
            return Expansion.State.REVOKED;
        }

        final Optional<ExternalApplicationData> extAppDataOptional = externalAppDataStore.getAppData(appId, deviceId);
        if(!extAppDataOptional.isPresent()) {
            LOGGER.error("error=no-ext-app-data device_id={}", deviceId);
            return Expansion.State.NOT_CONFIGURED;
        }

        final ExternalApplicationData extData = extAppDataOptional.get();
        if(extData.data.isEmpty()){
            LOGGER.error("error=no-ext-app-data device_id={}", deviceId);
            return Expansion.State.NOT_CONFIGURED;
        }

        if(!extData.enabled) {
            return Expansion.State.CONNECTED_OFF;
        }

        return Expansion.State.CONNECTED_ON;
    }

    private List<Expansion> getAllExpansions(final AccessToken accessToken) {
        return getExpansions(accessToken, 0L);
    }

    private List<Expansion> getExpansions(final AccessToken accessToken, final Long appId) {
        final List<DeviceAccountPair> sensePairedWithAccount = this.deviceDAO.getSensesForAccountId(accessToken.accountId);
        if(sensePairedWithAccount.size() == 0){
            LOGGER.error("error=no-sense-paired account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final String deviceId = sensePairedWithAccount.get(0).externalDeviceId;

        final List<ExternalApplication> externalApps = Lists.newArrayList();

        if(appId > 0L) {
            final Optional<ExternalApplication> externalApplicationOptional = externalApplicationStore.getApplicationById(appId);
            if(!externalApplicationOptional.isPresent()) {
                LOGGER.warn("warning=application-not-found");
                throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
            }
            externalApps.add(externalApplicationOptional.get());
        } else {
            externalApps.addAll(externalApplicationStore.getAll());
        }

        final List<Expansion> expansions = Lists.newArrayList();

        for(final ExternalApplication extApp : externalApps) {

            final String iconFilenameBase = "icon-" + extApp.serviceName.toLowerCase();
            //TODO: Move These Icons and pull this base URL from configs!
            final MultiDensityImage icon = MultiDensityImage.create("https://hello-dev.s3.amazonaws.com/josef/expansions/",
                Optional.of(iconFilenameBase + "@1x.png"),
                Optional.of(iconFilenameBase + "@2x.png"),
                Optional.of(iconFilenameBase + "@3x.png"));

            final Expansion exp = new Expansion.Builder()
                .withId(extApp.id)
                .withCategory(extApp.category)
                .withDeviceName(extApp.deviceName)
                .withServiceName(Expansion.ServiceName.valueOf(extApp.serviceName.toUpperCase()))
                .withDescription(extApp.description)
                .withIcon(icon)
                .withAuthURI(String.format("https://dev-api-unstable.hello.is/v2/expansions/%s/auth", extApp.id.toString()))
                .withCompletionURI("https://dev-api-unstable.hello.is/v2/expansions/redirect")
                .withState(getStateFromExternalAppId(extApp.id, deviceId))
                .build();
            expansions.add(exp);
        }

        return expansions;

    }
}
