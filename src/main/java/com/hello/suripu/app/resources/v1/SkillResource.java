package com.hello.suripu.app.resources.v1;

import com.amazon.speech.speechlet.servlet.SpeechletServlet;
import com.hello.suripu.app.service.SenseSpeechlet;
import com.hello.suripu.core.db.AccountDAO;
import com.hello.suripu.coredw8.db.AccessTokenDAO;


import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * Created by jnorgan on 6/16/16.
 */
@Path("/skill")
@Produces(MediaType.APPLICATION_JSON)
public class SkillResource extends SpeechletServlet {
  @Context
  private HttpServletRequest servletRequest;

  @Context
  HttpServletResponse servletResponse;

  @POST
  public void postWrapper() throws ServletException, IOException {
    super.doPost(servletRequest, servletResponse);
  }

  public SkillResource(final AccountDAO accountDAO, final AccessTokenDAO accessTokenDAO) {
    setSpeechlet(new SenseSpeechlet(accountDAO, accessTokenDAO));
  }

}
