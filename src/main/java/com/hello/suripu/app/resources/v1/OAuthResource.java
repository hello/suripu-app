package com.hello.suripu.app.resources.v1;


import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.google.common.base.Optional;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.models.Account;
import com.hello.suripu.core.notifications.NotificationSubscriptionDAOWrapper;
import com.hello.suripu.core.oauth.Application;
import com.hello.suripu.core.oauth.ApplicationRegistration;
import com.hello.suripu.core.oauth.ClientAuthenticationException;
import com.hello.suripu.core.oauth.ClientCredentials;
import com.hello.suripu.core.oauth.ClientDetails;
import com.hello.suripu.core.oauth.GrantType;
import com.hello.suripu.core.oauth.MissingRequiredScopeException;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.oauth.stores.ApplicationStore;
import com.hello.suripu.core.oauth.stores.OAuthTokenStore;
import com.hello.suripu.core.util.PasswordUtil;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.AuthCookie;
import com.hello.suripu.coredropwizard.oauth.ClientAuthRequest;
import com.hello.suripu.coredropwizard.oauth.GrantTypeParam;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import io.dropwizard.views.View;
import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
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
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

@Path("/v1/oauth2")
public class OAuthResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthResource.class);
    private final OAuthTokenStore<AccessToken,ClientDetails, ClientCredentials> tokenStore;
    private final ApplicationStore<Application, ApplicationRegistration> applicationStore;
    private final AccountDAO accountDAO;
    private final NotificationSubscriptionDAOWrapper notificationSubscriptionDAOWrapper;
    private final static String AUTH_COOKIE_NAME = "hello-auth";
    private final static Integer AUTH_COOKIE_EXPIRATION_SECS = 60;
    private static final String AUTH_SECRET = "mbVCyQ^s>r(im7xr";
    private final static Integer INTERNAL_STATE_AUTHENTICATION = 0;
    private final static Integer INTERNAL_STATE_NEEDS_AUTHORIZATION = 1;
    private final static Integer INTERNAL_STATE_AUTHORIZED = 2;

    private ObjectMapper mapper = new ObjectMapper();

    public OAuthResource(
            final OAuthTokenStore<AccessToken, ClientDetails, ClientCredentials> tokenStore,
            final ApplicationStore<Application, ApplicationRegistration> applicationStore,
            final AccountDAO accountDAO,
            final NotificationSubscriptionDAOWrapper notificationSubscriptionDAOWrapper) throws Exception{

        this.tokenStore = tokenStore;
        this.applicationStore = applicationStore;
        this.accountDAO = accountDAO;
        this.notificationSubscriptionDAOWrapper = notificationSubscriptionDAOWrapper;

        mapper.registerModule(new JodaModule());
    }

    @POST
    @Path("/token")
    @Timed
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public AccessToken accessToken(
                @FormParam("grant_type") GrantTypeParam grantType,
                @FormParam("code") String code,
                @FormParam("redirect_uri") String redirectUri,
                @FormParam("client_id") String clientId,
                @FormParam("client_secret") String clientSecret,
                @FormParam("username") String username,
                @FormParam("password") String password,
                @FormParam("scope") String scope,
                @FormParam("refresh_token") String refresh_token
                ) {


        //TODO: Conditional handling of token grant based on client_id

        if(grantType == null) {
            LOGGER.error("GrantType is null");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        // FIXME: this is confusing, are we checking for application, or for installed application for this user
        // FIXME: if that's what we are doing, how did they get a token in the first place?
        // TODO: BE SMARTER


        ClientDetails details;
        Optional<Account> accountOptional = Optional.absent();

        if (grantType.getType().equals(GrantType.AUTHORIZATION_CODE)) {

            final Optional<ClientDetails> optionalClientDetails = tokenStore.getClientDetailsByAuthorizationCode(code);
            if (!optionalClientDetails.isPresent()) {
                LOGGER.error("error=invalid-auth-code auth_code={}", code);
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            final Optional<Application> applicationOptional = applicationStore.getApplicationByClientId(clientId);
            if(!applicationOptional.isPresent()) {
                LOGGER.error("application wasn't found for clientId : {}", clientId);
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            final Application application = applicationOptional.get();
            if (!application.hasScope(OAuthScope.AUTH)) {
                LOGGER.error("application does not have proper scope : {}", clientId);
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            if(!application.grantType.equals(grantType.getType())) {
                LOGGER.error("Grant types don't match : {} and {}", applicationOptional.get().grantType, grantType.getType());
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            //Verify the client_secret
            if (clientSecret == null || !application.clientSecret.equals(clientSecret)) {
                LOGGER.error("error=invalid-client-secret app_id={}", application.id);
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }
            details = optionalClientDetails.get();

        } else if (grantType.getType().equals(GrantType.PASSWORD)) {

            final Optional<Application> applicationOptional = applicationStore.getApplicationByClientId(clientId);
            if(!applicationOptional.isPresent()) {
                LOGGER.error("application wasn't found for clientId : {}", clientId);
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            final Application application = applicationOptional.get();
            if (!application.hasScope(OAuthScope.AUTH)) {
                LOGGER.error("application does not have proper scope : {}", clientId);
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            if(!application.grantType.equals(grantType.getType())) {
                LOGGER.error("Grant types don't match : {} and {}", application.grantType, grantType.getType());
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            if(username == null || password == null || username.isEmpty() || password.isEmpty()) {
                LOGGER.error("username or password is null or empty.");
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            final String normalizedUsername = username.toLowerCase();
            LOGGER.debug("normalized username {}", normalizedUsername);

            accountOptional = accountDAO.exists(normalizedUsername, password);
            if(!accountOptional.isPresent()) {
                LOGGER.error("Account wasn't found: {}, {}", normalizedUsername, PasswordUtil.obfuscate(password));
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            final Account account = accountOptional.get();

            details = new ClientDetails(
                grantType.getType(),
                clientId,
                redirectUri,
                application.scopes,
                "", // state
                code,
                account.id.get(),
                clientSecret
            );
            details.setApp(application);

        } else if (grantType.getType().equals(GrantType.REFRESH_TOKEN)) {
            LOGGER.debug("action=token-refresh client_id={}", clientId);
            
            final Optional<Application> applicationOptional = applicationStore.getApplicationByClientId(clientId);
            if(!applicationOptional.isPresent()) {
                LOGGER.error("application wasn't found for clientId : {}", clientId);
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            final Application application = applicationOptional.get();
            if (!application.hasScope(OAuthScope.AUTH)) {
                LOGGER.error("application does not have proper scope : {}", clientId);
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            if (refresh_token == null) {
                LOGGER.error("error=missing-refresh-token app_id={}", application.id);
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            //Verify the client_secret if it exists
            if (clientSecret != null && !application.clientSecret.equals(clientSecret)) {
                LOGGER.error("error=invalid-client-secret app_id={}", application.id);
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            //Check refresh_token is valid & not expired for this client
            try {
                final Optional<ClientDetails> optionalDetails = tokenStore.getClientDetailsByRefreshToken(refresh_token, DateTime.now(DateTimeZone.UTC));
                if (!optionalDetails.isPresent()){
                    LOGGER.error("error=invalid-refresh-token token={}", refresh_token);
                    throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
                }
                //Refresh access token
                details = optionalDetails.get();

                tokenStore.disableByRefreshToken(refresh_token);

            } catch (MissingRequiredScopeException mrse) {
                LOGGER.error("error=missing-required-scope token={}", refresh_token);
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }
        } else {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        // get account info if not available
        if (!accountOptional.isPresent()) {
            final Long accountId = details.accountId;
            accountOptional = accountDAO.getById(accountId);
            if (!accountOptional.isPresent()) {
                LOGGER.error("error=account-not-found account_id={}", accountId);
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }
        }

        AccessToken accessToken = null;
        try {
            accessToken = tokenStore.storeAccessToken(details);
        } catch (ClientAuthenticationException clientAuthenticationException) {
            LOGGER.error(clientAuthenticationException.getMessage());
            throw new WebApplicationException(Response.serverError().build());
        }

        LOGGER.debug("AccessToken {}", accessToken);
        LOGGER.debug("email {}", username);

        final Optional<UUID> optionalExternalId = accountOptional.get().externalId;
        if (optionalExternalId.isPresent()) {
            LOGGER.debug("action=login account_id={} external_id={}", accessToken.accountId, optionalExternalId.get());
//            return AccessToken.createWithExternalId(accessToken, optionalExternalId.get());
        }

        return accessToken;
    }

    @ScopesAllowed({OAuthScope.AUTH})
    @DELETE
    @Path("/token")
    @Timed
    public void delete(@Auth final AccessToken accessToken) {
        tokenStore.disable(accessToken);
        LOGGER.debug("AccessToken {} deleted", accessToken);
        if(accessToken.hasScope(OAuthScope.PUSH_NOTIFICATIONS)) {
            LOGGER.debug("AccessToken {} has PUSH_NOTIFICATIONS_SCOPE");
            notificationSubscriptionDAOWrapper.unsubscribe(accessToken.serializeAccessToken());
            LOGGER.debug("Unsubscribed from push notifications");
        }
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("/authorize")
    public Response getLoginPrompt(@Context HttpServletRequest request) {

        if (!request.getServerName().endsWith("login.hello.is") && !request.getServerName().equals("dev-api-unstable.hello.is")) {
            LOGGER.error("error=unauthorized-server-name server_name={}", request.getServerName());
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        Optional<ClientAuthRequest> optionalClientRequest = Optional.absent();

        if (request.getParameter("client_id") != null &&
            request.getParameter("response_type") != null &&
            request.getParameter("scope") != null &&
            request.getParameter("state") != null) {

            //Must be initial request coming from client
            //Stuff client request in a ClientAuthRequest object and pass that around as an encrypted serialized string
            optionalClientRequest = Optional.of(new ClientAuthRequest(
                request.getParameter("client_id"),
                request.getParameter("response_type"),
                request.getParameter("state"),
                request.getParameter("scope"),
                INTERNAL_STATE_AUTHENTICATION
            ));

        }

        if (request.getParameter("client_request") != null) {
            try {
                final String clientRequestValue = decrypt(request.getParameter("client_request"), AUTH_SECRET);
                optionalClientRequest = Optional.of(mapper.readValue(clientRequestValue, ClientAuthRequest.class));
            } catch (IOException ioe) {
                LOGGER.error("error=client-request-bad-format request_value={}", request.getParameter("client_request"));
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            } catch (Exception ex) {
                LOGGER.error("error=client-request-decryption-failure request_value={}", request.getParameter("client_request"));
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

        }

        if(!optionalClientRequest.isPresent()) {
            LOGGER.error("error=client-request-missing");
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
        final ClientAuthRequest clientRequest = optionalClientRequest.get();


        //Validate username & pass
        if(request.getParameter("username") != null || request.getParameter("password") != null) {
            if(request.getParameter("username").isEmpty() || request.getParameter("password").isEmpty()) {
                LOGGER.error("username or password is null or empty.");
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            final String username = request.getParameter("username");
            final String password = request.getParameter("password");

            final String normalizedUsername = username.toLowerCase();
            final Optional<Account> accountOptional = accountDAO.exists(normalizedUsername, password);
            if(!accountOptional.isPresent()) {
                LOGGER.error("Account wasn't found: {}, {}", normalizedUsername, PasswordUtil.obfuscate(password));
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }

            final Optional<Application> applicationOptional = applicationStore.getApplicationByClientId(clientRequest.clientId);
            if(!applicationOptional.isPresent()) {
                LOGGER.warn("warning=no_app_with_id client_id={}", clientRequest.clientId);
            }

            final Account acct = accountOptional.get();
            try {
                final AuthCookie authCookie = new AuthCookie(acct.id.get(), true, DateTime.now(DateTimeZone.UTC), 60);
                final String cookieValue = mapper.writeValueAsString(authCookie);
                final String encodedAuthCookie = encrypt(cookieValue, AUTH_SECRET);
                final NewCookie nc = new NewCookie(AUTH_COOKIE_NAME, encodedAuthCookie, "/", request.getServerName(), 0, null, AUTH_COOKIE_EXPIRATION_SECS, false);
                final ClientAuthRequest updatedClientRequest = new ClientAuthRequest(
                    clientRequest.clientId,
                    clientRequest.responseType,
                    clientRequest.state,
                    clientRequest.scope,
                    INTERNAL_STATE_NEEDS_AUTHORIZATION
                );
                final String clientRequestValue = mapper.writeValueAsString(updatedClientRequest);
                final String encryptedClientRequest = encrypt(clientRequestValue, AUTH_SECRET);
                final URI uri = UriBuilder.fromPath(request.getRequestURI())
                    .queryParam("client_request", encryptedClientRequest)
                    .build();
                return Response.seeOther(uri).cookie(nc).build();
            } catch (Exception ex) {
                throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
            }
        }

        final Optional<AuthCookie> optionalAuthCookie = getAuthCookieFromRequest(request);

        //If the user doesn't have an Auth cookie, require Login
        if(!optionalAuthCookie.isPresent()) {
            try {
                final String clientRequestValue = mapper.writeValueAsString(clientRequest);
                final String encryptedClientRequest = encrypt(clientRequestValue, AUTH_SECRET);
                return Response.ok(new LoginView(new Login(
                    request.getRequestURI(),
                    encryptedClientRequest
                    ))).build();
            } catch (Exception ex) {
                throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
            }
        }

        final AuthCookie authCookie = optionalAuthCookie.get();

        //Check that cookie is valid
        final Optional<Account> optionalAccount = accountDAO.getById(authCookie.accountId);
        if (!optionalAccount.isPresent()) {
            LOGGER.warn("warning=invalid-auth-cookie value={}", authCookie.toString());
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
        final Account account = optionalAccount.get();

        final Optional<Application> optionalApp = applicationStore.getApplicationByClientId(clientRequest.clientId);
        if (!optionalApp.isPresent()) {
            LOGGER.warn("warning=invalid-client-id client_id={}", clientRequest.clientId);
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
        final Application application = optionalApp.get();

        if (!clientRequest.internalState.equals(INTERNAL_STATE_AUTHORIZED)) {
            try {
                final ClientAuthRequest updatedClientRequest = new ClientAuthRequest(
                    clientRequest.clientId,
                    clientRequest.responseType,
                    clientRequest.state,
                    clientRequest.scope,
                    INTERNAL_STATE_AUTHORIZED
                );
                final String clientRequestValue = mapper.writeValueAsString(updatedClientRequest);
                final String encryptedClientRequest = encrypt(clientRequestValue, AUTH_SECRET);
                return Response.ok(new ConfirmView(new Confirmation(
                    encryptedClientRequest,
                    account.name(),
                    application.name,
                    clientRequest.scope,
                    request.getRequestURI()
                ))).build();
            } catch (Exception ex) {
                throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).build());
            }
        }

        final Optional<ClientCredentials> optionalCredentials = createAndStoreAuthorizationCode(account, application, request.getParameter("scope"));
        if (!optionalCredentials.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
        final ClientCredentials credentials = optionalCredentials.get();

        //User has AUTHORIZED; Send to Client
        try {
            final URI uri = UriBuilder.fromUri(application.redirectURI)
                .queryParam("state", clientRequest.state)
                .queryParam("code", credentials.tokenOrCode)
                .build();
            return Response.seeOther(uri).build();
        } catch (Exception ex) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
    }

    @POST
    @Produces(MediaType.TEXT_HTML)
    @Path("/logout")
    public Response logout(@Context HttpServletRequest request) {
        //Overwrite auth cookie
        final NewCookie nc = new NewCookie(AUTH_COOKIE_NAME, "", "/", request.getServerName(), 0, null, 0, false);
        return Response.ok(new LoggedOutView()).cookie(nc).build();
    }

    private Optional<AuthCookie> getAuthCookieFromRequest(final HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.absent();
        }

        for (Cookie c : request.getCookies()) {
            if (AUTH_COOKIE_NAME.equals(c.getName())) {
                try {
                    final String cookieValue = decrypt(c.getValue(), AUTH_SECRET);
                    return Optional.of(mapper.readValue(cookieValue, AuthCookie.class));
                } catch (Exception ex) {
                    LOGGER.warn("warning=invalid-cookie value={}", c.getValue());
                    return Optional.absent();
                }

            }
        }
        return Optional.absent();
    }

    private Optional<ClientCredentials> createAndStoreAuthorizationCode(final Account acct, final Application app, final String scope) {
        OAuthScope[] oAuthScopes = {};
        //Check requested scopes against application scopes
        try {
            oAuthScopes = OAuthScope.fromStringArray(scope.split(" "));
            for (final OAuthScope authScope : oAuthScopes) {
                if (!app.hasScope(authScope)) {
                    LOGGER.error("error=scope-not-allowed application_id={} scope={}", app.id, authScope);
                    throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
                }
            }
        } catch (IllegalArgumentException iae) {
            LOGGER.error("error=bad-scope-value application_id={} message=\"{}\"", app.id, iae.getMessage());
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }

        //Generate auth code
        final ClientDetails clientDetails = new ClientDetails(
            GrantType.AUTHORIZATION_CODE,
            app.clientId,
            app.redirectURI,
            oAuthScopes,
            "",
            "",
            acct.id.get(),
            ""
        );
        clientDetails.setApp(app);

        try {
            final ClientCredentials credentials = tokenStore.storeAuthorizationCode(clientDetails);
            return Optional.of(credentials);
        } catch (ClientAuthenticationException cae) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
    }


    //Copied from http://netnix.org/2015/04/19/aes-encryption-with-hmac-integrity-in-java/
    public byte[] deriveKey(String p, byte[] s, int i, int l) throws Exception {
        PBEKeySpec ks = new PBEKeySpec(p.toCharArray(), s, i, l);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        return skf.generateSecret(ks).getEncoded();
    }

    //Copied from http://netnix.org/2015/04/19/aes-encryption-with-hmac-integrity-in-java/
    public String encrypt(String inputString, String key) throws Exception {
        SecureRandom r = SecureRandom.getInstance("SHA1PRNG");

        // Generate 160 bit Salt for Encryption Key
        byte[] esalt = new byte[20]; r.nextBytes(esalt);
        // Generate 128 bit Encryption Key
        byte[] dek = deriveKey(key, esalt, 50000, 128);

        // Perform Encryption
        SecretKeySpec eks = new SecretKeySpec(dek, "AES");
        Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, eks, new IvParameterSpec(new byte[16]));
        byte[] es = c.doFinal(inputString.getBytes(StandardCharsets.UTF_8));

        // Generate 160 bit Salt for HMAC Key
        byte[] hsalt = new byte[20]; r.nextBytes(hsalt);
        // Generate 160 bit HMAC Key
        byte[] dhk = deriveKey(key, hsalt, 50000, 160);

        // Perform HMAC using SHA-256
        SecretKeySpec hks = new SecretKeySpec(dhk, "HmacSHA256");
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(hks);
        byte[] hmac = m.doFinal(es);

        // Construct Output as "ESALT + HSALT + CIPHERTEXT + HMAC"
        byte[] os = new byte[40 + es.length + 32];
        System.arraycopy(esalt, 0, os, 0, 20);
        System.arraycopy(hsalt, 0, os, 20, 20);
        System.arraycopy(es, 0, os, 40, es.length);
        System.arraycopy(hmac, 0, os, 40 + es.length, 32);

        // Return a Base64 Encoded String
        return Base64.encodeBase64String(os);
    }

    //Copied from http://netnix.org/2015/04/19/aes-encryption-with-hmac-integrity-in-java/
    public String decrypt(String encryptedString, String key) throws Exception {
        // Recover our Byte Array by Base64 Decoding
        byte[] os = Base64.decodeBase64(encryptedString);

        // Check Minimum Length (ESALT (20) + HSALT (20) + HMAC (32))
        if (os.length > 72) {
            // Recover Elements from String
            byte[] esalt = Arrays.copyOfRange(os, 0, 20);
            byte[] hsalt = Arrays.copyOfRange(os, 20, 40);
            byte[] es = Arrays.copyOfRange(os, 40, os.length - 32);
            byte[] hmac = Arrays.copyOfRange(os, os.length - 32, os.length);

            // Regenerate HMAC key using Recovered Salt (hsalt)
            byte[] dhk = deriveKey(key, hsalt, 50000, 160);

            // Perform HMAC using SHA-256
            SecretKeySpec hks = new SecretKeySpec(dhk, "HmacSHA256");
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(hks);
            byte[] chmac = m.doFinal(es);

            // Compare Computed HMAC vs Recovered HMAC
            if (MessageDigest.isEqual(hmac, chmac)) {
                // HMAC Verification Passed
                // Regenerate Encryption Key using Recovered Salt (esalt)
                byte[] dek = deriveKey(key, esalt, 50000, 128);

                // Perform Decryption
                SecretKeySpec eks = new SecretKeySpec(dek, "AES");
                Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
                c.init(Cipher.DECRYPT_MODE, eks, new IvParameterSpec(new byte[16]));
                byte[] s = c.doFinal(es);

                // Return our Decrypted String
                return new String(s, StandardCharsets.UTF_8);
            }
        }
        throw new Exception();
    }

    public class ConfirmView extends View {
        private final Confirmation confirm;
        public ConfirmView(Confirmation confirm) {
            super("confirm.ftl");
            this.confirm = confirm;
        }

        public Confirmation getConfirm() {
            return confirm;
        }
    }
    public class Confirmation {
        public String clientRequest;
        public String userName;
        public String applicationName;
        public String scope;
        public String redirectURI;

        public Confirmation(
            final String clientRequest,
            final String userName,
            final String applicationName,
            final String scope,
            final String redirectURI
        ){
            this.clientRequest = clientRequest;
            this.userName = userName;
            this.applicationName = applicationName;
            this.scope = scope;
            this.redirectURI = redirectURI;
        }

        public String getClientRequest() { return clientRequest; }
        public String getUserName() { return userName; }
        public String getApplicationName() { return applicationName; }
        public String getScope() { return scope; }
        public String getRedirectURI() { return redirectURI; }
    }

    public class LoginView extends View {
        private final Login login;
        public LoginView(Login login) {
            super("login.ftl");
            this.login = login;
        }

        public Login getLogin() {
            return login;
        }
    }
    public class Login {
        public String submitURI;
        public String clientRequest;

        public Login(final String submitURI,
                     final String clientRequest){
            this.submitURI = submitURI;
            this.clientRequest = clientRequest;
        }

        public String getSubmitURI() {
            return submitURI;
        }
        public String getClientRequest() { return clientRequest; }
    }

    public class LoggedOutView extends View {
        public LoggedOutView() {
            super("logged_out.ftl");
        }
    }

    @ScopesAllowed({OAuthScope.ALARM_READ})
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/check")
    public Account checkToken(@Auth final AccessToken token) {
//        LOGGER.debug("Query Param {}", queryToken);
        final Optional<Account> optionalAccount = accountDAO.getById(token.accountId);
        if (!optionalAccount.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
        final Account account = optionalAccount.get();

        return account;
    }

    //THIS ENDPOINT IS PRETENDING TO BE AMAZON'S SERVER
    //TODO: REMOVE THIS
    @GET
    @Produces(MediaType.TEXT_HTML)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("/skill_link")
    public Response linkSkill(@Context HttpServletRequest request,
                              @QueryParam("state") String state,
                              @QueryParam("code") String code) {
        LOGGER.debug("Request Server: {}", request.getServerName());

        // Amazon validates code and state. Then, a request is made to our Access token URI to request tokens
        // Amazon passes to token URL: code, clientId, client secret, and grant_type=authorization_code
        Client client = ClientBuilder.newClient();

        WebTarget resourceTarget = client.target(UriBuilder.fromUri("http://localhost:9966/v1/oauth2/token").build());

        Invocation.Builder builder = resourceTarget.request();
        Form form = new Form();
        form.param("grant_type", "authorization_code"); //grant_type must be 'authorization_code' per RFC6749
        form.param("code", code);
        form.param("client_id", "alexa_client_id");
        form.param("client_secret", "alexa_client_secret");

        Response response = builder.accept(MediaType.APPLICATION_JSON)
            .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED));
        final String accessToken = response.readEntity(String.class);
        response.close();
        return Response.ok(accessToken).build();
    }

    //Temp endpoint to allow a refresh token request
    //TODO: REMOVE THIS
    @GET
    @Produces(MediaType.TEXT_HTML)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("/refresh")
    public Response getRefreshedToken(@Context HttpServletRequest request,
                                      @QueryParam("refresh_token") String refresh_token) {
        LOGGER.debug("Token being refreshed! token={}", refresh_token);

        Client client = ClientBuilder.newClient();
        WebTarget resourceTarget = client.target(UriBuilder.fromUri("http://localhost:9966/v1/oauth2/token").build());
        Invocation.Builder builder = resourceTarget.request();
        Form form = new Form();
        //grant_type = refresh_token; client_id; client_secret; refresh_token
        form.param("grant_type", "refresh_token");
        form.param("client_id", "alexa_client_id");
        form.param("client_secret", "alexa_client_secret");
        form.param("refresh_token", refresh_token);

        Response response = builder.accept(MediaType.APPLICATION_JSON)
            .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED));
        final String accessToken = response.readEntity(String.class);
        response.close();
        return Response.ok(accessToken).build();
    }
}
