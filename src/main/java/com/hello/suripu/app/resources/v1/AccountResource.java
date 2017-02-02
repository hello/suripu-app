package com.hello.suripu.app.resources.v1;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import com.hello.suripu.core.actions.ActionProcessor;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.db.AccountLocationDAO;
import com.hello.suripu.core.db.util.MatcherPatternsDB;
import com.hello.suripu.core.models.Account;
import com.hello.suripu.core.models.PasswordUpdate;
import com.hello.suripu.core.models.Registration;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.profile.ImmutableProfilePhoto;
import com.hello.suripu.core.profile.ProfilePhotoStore;
import com.hello.suripu.core.util.JsonError;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import com.hello.suripu.coredropwizard.resources.BaseResource;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.regex.Matcher;

@Path("/v1/account")
public class AccountResource {

    @Context
    HttpServletRequest request;

    @Inject
    ActionProcessor actionProcessor;

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountResource.class);
    private final AccountDAO accountDAO;
    private final AccountLocationDAO accountLocationDAO;
    private final ProfilePhotoStore profilePhotoStore;

    public AccountResource(final AccountDAO accountDAO, final AccountLocationDAO accountLocationDAO, final ProfilePhotoStore profilePhotoStore) {
        this.accountDAO = accountDAO;
        this.accountLocationDAO = accountLocationDAO;
        this.profilePhotoStore = profilePhotoStore;
    }

    @ScopesAllowed({OAuthScope.USER_EXTENDED})
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    public Account getAccount(@Auth final AccessToken accessToken, @QueryParam("photo") @DefaultValue("false") final Boolean includePhoto) {

        LOGGER.debug("level=debug action=get-account account_id={}", accessToken.accountId);
        final Optional<Account> accountOptional = accountDAO.getById(accessToken.accountId);
        if(!accountOptional.isPresent()) {
            LOGGER.warn("level=warning error_message=account-not-present account_id={}", accessToken.accountId);
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());
        }

        LOGGER.info("level=info action=show-last-modified last_modified={}", accountOptional.get().lastModified);
        final Account account = accountOptional.get();


        return maybeAddProfilePhoto(account, includePhoto);

    }

    @POST
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Account register(
            @Valid final Registration registration,
            @QueryParam("sig") final String signature) {

        LOGGER.info("level=info action=attempt-to-register-account email={}", registration.email);
        final Optional<Registration.RegistrationError> error = Registration.validate(registration);
        if(error.isPresent()) {
            LOGGER.error("level=error error_message=registration-failed registration_fail_reason={}.", error.get());
            throw new WebApplicationException(Response.status(400).entity(new JsonError(400, error.get().toString())).build());
        }

        // Overriding email address for kaytlin
        final Registration securedRegistration = Registration.secureAndNormalize(registration);
        LOGGER.info("level=info action=email-after-encryption-and-normalizing email={}", securedRegistration.email);

        try {
            return accountDAO.register(securedRegistration);
        } catch (UnableToExecuteStatementException exception) {

            final Matcher matcher = MatcherPatternsDB.PG_UNIQ_PATTERN.matcher(exception.getMessage());

            if(matcher.find()) {
                LOGGER.warn("level=warning error_message=register-account-with-existing-email email={}", registration.email);
                throw new WebApplicationException(Response.status(Response.Status.CONFLICT)
                        .entity(new JsonError(409, "Account already exists.")).build());
            }

            LOGGER.error("level=error error_message=non-unique-email-exception email={}", registration.email);
            LOGGER.error(exception.getMessage());
        }

        throw new WebApplicationException(Response.serverError().build());
    }

    @ScopesAllowed({OAuthScope.USER_EXTENDED})
    @PUT
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Account modify(
            @Auth final AccessToken accessToken,
            @Valid final Account account,
            @DefaultValue("false") @QueryParam("photo") final Boolean includePhoto) {

        LOGGER.warn("level=warning action=modify-account account_id={} last_modified={}", accessToken.accountId, account.lastModified);

        final Optional<Account> optionalAccount = accountDAO.update(account, accessToken.accountId);


        if(!optionalAccount.isPresent()) {
            LOGGER.warn("level=warning error_message=last-modified-condition-did-not-match-DB-data account_id={} last_modified={}",
                    accessToken.accountId, account.lastModified);
            final JsonError error = new JsonError(Response.Status.PRECONDITION_FAILED.getStatusCode(), "pre condition failed");
            throw new WebApplicationException(Response.status(Response.Status.PRECONDITION_FAILED)
                    .entity(error).build());
        }

        // save location if exists
        if (account.hasLocation()) {
            final String ip = BaseResource.getIpAddress(request);
            try {
                LOGGER.debug("level=debug action=insert-account-location account_id={} latitude={} longitude={} ip={}",
                        accessToken.accountId, account.latitude, account.longitude, ip);
                accountLocationDAO.insertNewAccountLatLongIP(accessToken.accountId, ip, account.latitude, account.longitude);
            } catch (UnableToExecuteStatementException exception) {
                LOGGER.error("level=error error_message=fail-to-insert-account-location account_id={} latitude={} longitude={} ip={}",
                        accessToken.accountId, account.latitude, account.longitude, ip);
            }
        }

        return maybeAddProfilePhoto(optionalAccount.get(), includePhoto);
    }

    @ScopesAllowed({OAuthScope.USER_EXTENDED})
    @POST
    @Timed
    @Path("/password")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public void password(
            @Auth final AccessToken accessToken,
            @Valid final PasswordUpdate passwordUpdate) {

        final Optional<Registration.RegistrationError> error = Registration.validatePassword(passwordUpdate.newPassword);
        if(error.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(
                    new JsonError(Response.Status.BAD_REQUEST.getStatusCode(), error.get().toString())).build());
        }

        final PasswordUpdate encrypted = PasswordUpdate.encrypt(passwordUpdate);
        if(!accountDAO.updatePassword(accessToken.accountId, encrypted)) {
            throw new WebApplicationException(Response.Status.CONFLICT);
        }
        // TODO: remove all tokens for this user
    }

    @ScopesAllowed({OAuthScope.USER_EXTENDED})
    @POST
    @Timed
    @Path("/email")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Account updateEmail(
            @Auth final AccessToken accessToken,
            @Valid final Account account,
            @DefaultValue("false") @QueryParam("photo") final Boolean includePhoto) {
        LOGGER.info("level=info action=update-account-email email={}", account.email);
        final Account accountWithId = Account.normalizeWithId(account, accessToken.accountId);
        LOGGER.info("level=info action=new-email-after-normalizing email={}", accountWithId.email);
        final Optional<Registration.RegistrationError> error = Registration.validateEmail(accountWithId.email);
        if(error.isPresent()) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(
                    new JsonError(Response.Status.BAD_REQUEST.getStatusCode(), error.get().toString())).build());
        }

        final Optional<Account> accountOptional = accountDAO.updateEmail(accountWithId);
        if(!accountOptional.isPresent()) {
            throw new WebApplicationException(Response.Status.CONFLICT);
        }

        return maybeAddProfilePhoto(accountOptional.get(), includePhoto);
    }


    /**
     * Add profile photo if found to the account
     */
    private Account maybeAddProfilePhoto(final Account account, final Boolean includePhoto) {
        if(includePhoto && account.id.isPresent()) {
            LOGGER.trace("action=get-profile-photo account_id={}", account.id.get());
            final Optional<ImmutableProfilePhoto> optionalProfilePhoto = profilePhotoStore.get(account.id.get());
            if(optionalProfilePhoto.isPresent()) {
                return Account.withProfilePhoto(account, optionalProfilePhoto.get().photo());
            }
            LOGGER.debug("action=get-profile-photo account_id={} message=missing-profile-photo", account.id.get());
        }

        return account;
    }
}
