package com.hello.suripu.app.resources.v1;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.hello.suripu.core.actions.ActionProcessor;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.core.models.Account;
import com.hello.suripu.core.oauth.OAuthScope;
import com.hello.suripu.core.util.JsonError;
import com.hello.suripu.coredropwizard.oauth.AccessToken;
import com.hello.suripu.coredropwizard.oauth.Auth;
import com.hello.suripu.coredropwizard.oauth.ScopesAllowed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

@Path("/v1/export_data")
public class ExportDataResource {

    @Inject
    ActionProcessor actionProcessor;


    private static final Logger LOGGER = LoggerFactory.getLogger(ExportDataResource.class);

    private final AccountDAO accountDAO;
    private final AmazonSQS amazonSQS;
    private final String exportDataQueueURL;


    private ExportDataResource(final AccountDAO accountDAO, final AmazonSQS amazonSQS, final String exportDataQueueURL) {
        this.accountDAO = accountDAO;
        this.amazonSQS = amazonSQS;
        this.exportDataQueueURL = exportDataQueueURL;
    }


    /**
     * Instantiate PasswordResetResource with the proper dependencies
     * @param accountDAO
     * @param amazonSQS
     * @return
     */
    public static ExportDataResource create(final AccountDAO accountDAO, final AmazonSQS amazonSQS,
                                            final String exportDataQueueURL) {
        if(exportDataQueueURL.isEmpty()) {
            throw new RuntimeException("Missing queue url");
        }
        
        return new ExportDataResource(accountDAO, amazonSQS, exportDataQueueURL);
    }

    @ScopesAllowed({OAuthScope.PASSWORD_RESET})
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{uuid}")
    public Response create(@Auth final AccessToken accessToken, @PathParam("uuid") final String uuid) {
        final UUID accountUUID = UUID.fromString(uuid);
        final Optional<Account> accountOptional = accountDAO.getByExternalId(accountUUID);
        if(!accountOptional.isPresent()) {
            LOGGER.warn("uuid={} action=export-data result=account-not-found", uuid);
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).entity(new JsonError(Response.Status.NOT_FOUND.getStatusCode(), "account not found")).build());
        }
        final Account account = accountOptional.get();
        final Map<String, String> message = Maps.newHashMap();
        message.put("email", account.email);
        message.put("account_id", String.valueOf(account.id.or(0L)));

        final ObjectMapper mapper = new ObjectMapper();
        try {
            final String content = mapper.writeValueAsString(message);
            LOGGER.info("action=export-data account_id={}", account.id.or(0L));
            final SendMessageResult result = amazonSQS.sendMessage(exportDataQueueURL, content);
            LOGGER.info("action=export-data account_id={} message_id={}", result.getMessageId());
        } catch (JsonProcessingException e) {
            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new JsonError(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), "json error")).build());
        }
        
        return Response.noContent().build();
    }
}
