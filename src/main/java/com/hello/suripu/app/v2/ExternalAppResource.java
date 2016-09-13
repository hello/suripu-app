package com.hello.suripu.app.v2;


import com.google.common.base.Optional;
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
import com.hello.suripu.coredropwizard.db.ExternalAuthorizationStateDAO;
import com.hello.suripu.coredropwizard.models.HueApplicationData;
import com.hello.suripu.coredropwizard.models.NestApplicationData;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ExternalApplication;
import com.hello.suripu.coredropwizard.oauth.ExternalApplicationData;
import com.hello.suripu.coredropwizard.oauth.ExternalAuthorizationState;
import com.hello.suripu.coredropwizard.oauth.ExternalToken;
import com.hello.suripu.coredropwizard.oauth.InvalidExternalTokenException;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import com.hello.suripu.coredropwizard.oauth.stores.ExternalApplicationStore;
import com.hello.suripu.coredropwizard.oauth.stores.ExternalOAuthTokenStore;
import com.hello.suripu.coredropwizard.oauth.stores.PersistentExternalAppDataStore;

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
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
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

import is.hello.gaibu.homeauto.services.HueLight;
import is.hello.gaibu.homeauto.services.NestThermostat;

@Path("/v2/external")
public class ExternalAppResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalAppResource.class);
    private final ExternalApplicationStore<ExternalApplication> externalApplicationStore;
    private final ExternalAuthorizationStateDAO externalAuthorizationStateDAO;
    private final DeviceDAO deviceDAO;
    private final ExternalOAuthTokenStore<ExternalToken> externalTokenStore;
    private final PersistentExternalAppDataStore externalAppDataStore;
    private final Vault tokenKMSVault;

    private ObjectMapper mapper = new ObjectMapper();

    public ExternalAppResource(
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


    @ScopesAllowed({OAuthScope.AUTH})
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("apps")
    public Map<Long, String> getExternalApps(@Auth final AccessToken token) {
        final Map<Long, String> appIds = Maps.newHashMap();
        final List<ExternalApplication> externalApps = externalApplicationStore.getAll();

        for(final ExternalApplication extApp : externalApps) {
            appIds.put(extApp.id, extApp.name);
        }

        return appIds;
    }

    @ScopesAllowed({OAuthScope.AUTH})
    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("auth")
    public Response getAuthURI(@Auth final AccessToken token,
                               @Context HttpServletRequest request,
                               @QueryParam("app_id") Long appId) {

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
        Map<String, String> responseJson = gson.fromJson(responseValue, collectionType);

        if(!responseJson.containsKey("access_token")) {
            LOGGER.error("error=no-access-token-returned");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

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
            .withDeviceId(authorizationState.deviceId);

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

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_READ})
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
        if(extAppDataOptional.isPresent()) {
            return extAppDataOptional.get();
        }

        final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(deviceId, externalApplication.id);
        if(!externalTokenOptional.isPresent()) {
            LOGGER.warn("warning=token-not-found");
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final ExternalToken externalToken = externalTokenOptional.get();
        final String decryptedToken = getDecryptedExternalToken(deviceId, externalApplication.id);
        final String bridgeId = HueLight.getBridge(decryptedToken);

        final Optional<String> whitelistIdOptional = HueLight.getWhitelistId(bridgeId, decryptedToken);
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
            .build();
        externalAppDataStore.insertAppData(appData);

        return appData;
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_WRITE})
    @POST
    @Timed
    @Path("hue/state")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setState(@Auth final AccessToken accessToken,
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

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_WRITE})
    @POST
    @Timed
    @Path("hue/group")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setGroup(@Auth final AccessToken accessToken,
                             @QueryParam("group_id") Integer groupId) {
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
        try {
            final HueApplicationData currentHueData = mapper.readValue(extData.data, HueApplicationData.class);
            final HueApplicationData updatedHueData = new HueApplicationData(currentHueData.bridgeId, currentHueData.whitelistId, groupId);
            final ExternalApplicationData newData = new ExternalApplicationData.Builder()
                .withAppId(extData.appId)
                .withDeviceId(extData.deviceId)
                .withData(mapper.writeValueAsString(updatedHueData))
                .build();
            externalAppDataStore.updateAppData(newData);
        } catch (IOException io) {
            LOGGER.warn("warn=bad-json-data");
        }

        return Response.ok().build();
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_READ})
    @GET
    @Timed
    @Path("hue/groups")
    @Produces(MediaType.APPLICATION_JSON)
    public String getGroups(@Auth final AccessToken accessToken) {

        final Optional<HueLight> hueLightOptional = getHueFromToken(accessToken);
        if(!hueLightOptional.isPresent()) {
            LOGGER.error("error=failure-fetching-hue account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final HueLight hueLight = hueLightOptional.get();
        return hueLight.getGroups();
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_WRITE})
    @POST
    @Timed
    @Path("nest/state")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setState(@Auth final AccessToken accessToken,
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

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_READ})
    @GET
    @Timed
    @Path("nest/thermostats")
    @Produces(MediaType.APPLICATION_JSON)
    public String getThermostats(@Auth final AccessToken accessToken) {
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
        final String decryptedToken = getDecryptedExternalToken(deviceId, externalApplication.id);
        return NestThermostat.getThermostats(decryptedToken);
    }

    @ScopesAllowed({OAuthScope.DEVICE_INFORMATION_WRITE})
    @POST
    @Timed
    @Path("nest/thermostat")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setGroup(@Auth final AccessToken accessToken,
                             @QueryParam("thermostat_id") String thermostatId) {
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

        final Optional<ExternalApplicationData> extAppDataOptional = externalAppDataStore.getAppData(externalApplication.id, deviceId);

        final NestApplicationData nestData = new NestApplicationData(thermostatId);
        try {
            final ExternalApplicationData newData = new ExternalApplicationData.Builder()
                .withAppId(externalApplication.id)
                .withDeviceId(deviceId)
                .withData(mapper.writeValueAsString(nestData))
                .build();

            //Create new external app data if none exists
            if(!extAppDataOptional.isPresent()) {
                LOGGER.error("error=no-ext-app-data account_id={} application_id={}", accessToken.accountId, externalApplication.id);
                externalAppDataStore.insertAppData(newData);
            }
            externalAppDataStore.updateAppData(newData);

        } catch (IOException io) {
            LOGGER.warn("warn=bad-json-data");
        }

        return Response.ok().build();
    }

    private String getDecryptedExternalToken(final String deviceId, final Long applicationId) {
        final Optional<ExternalToken> externalTokenOptional = externalTokenStore.getTokenByDeviceId(deviceId, applicationId);
        if(!externalTokenOptional.isPresent()) {
            LOGGER.warn("warning=token-not-found");
            throw new WebApplicationException(Response.status(Response.Status.NO_CONTENT).build());
        }

        final ExternalToken externalToken = externalTokenOptional.get();

        final Map<String, String> encryptionContext = Maps.newHashMap();
        encryptionContext.put("application_id", externalToken.appId.toString());
        final Optional<String> decryptedTokenOptional = tokenKMSVault.decrypt(externalToken.accessToken, encryptionContext);

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

        try {
            final HueApplicationData hueData = mapper.readValue(extData.data, HueApplicationData.class);
            final String decryptedToken = getDecryptedExternalToken(deviceId, externalApplication.id);
            if(hueData.groupId == null || hueData.groupId < 1) {
                LOGGER.warn("warn=no-hue-group-defined message='Defaulting to single light control'");
                return Optional.of(new HueLight(HueLight.DEFAULT_API_PATH, decryptedToken, hueData.bridgeId, hueData.whitelistId));
            }
            return Optional.of(new HueLight(HueLight.DEFAULT_API_PATH, decryptedToken, hueData.bridgeId, hueData.whitelistId, hueData.groupId));

        } catch (IOException io) {
            LOGGER.warn("warn=bad-json-data");
        }

        return Optional.absent();
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
            final String decryptedToken = getDecryptedExternalToken(deviceId, externalApplication.id);
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
}
