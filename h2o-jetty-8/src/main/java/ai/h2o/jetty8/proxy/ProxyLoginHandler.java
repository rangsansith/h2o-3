package ai.h2o.jetty8.proxy;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

//TODO check if similarities with LoginHandler class can be used for more elegant code with less duplications

public class ProxyLoginHandler extends HandlerWrapper {

  private final String _loginTarget;
  private final String _errorTarget;
  private final byte[] _loginFormData;

  ProxyLoginHandler(String loginTarget, String errorTarget) {
    _loginTarget = loginTarget;
    _errorTarget = errorTarget;
    try {
      _loginFormData = loadLoginFormResource();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load the login form!", e);
    }
  }

  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
          throws IOException, ServletException {
    if (isLoginTarget(target)) {
      if (isPageRequest(request)) {
        sendLoginForm(response);
      } else {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Access denied. Please login.");
      }
      baseRequest.setHandled(true);
    } else {
      // not for us, invoke wrapped handler
      super.handle(target, baseRequest, request, response);
    }
  }

  private static byte[] loadLoginFormResource() throws IOException {
    final InputStream loginFormStream = ProxyLoginHandler.class.getResourceAsStream("/www/login.html");
    if (loginFormStream == null) {
      throw new IllegalStateException("Login form resource is missing.");
    }
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    IOUtils.copy(loginFormStream, baos);
    return baos.toByteArray();
  }

  private void sendLoginForm(HttpServletResponse response) throws IOException {
    response.setContentType("text/html");
    response.setContentLength(_loginFormData.length);
    response.setStatus(HttpServletResponse.SC_OK);
    IOUtils.write(_loginFormData, response.getOutputStream());
    // TODO: this whole method and all about can be replaced with just:
    // org.apache.commons.io.IOUtils.copy(getClass().getResourceAsStream("/www/login.html"), os);
    // but it needs to be properly tested
  }

  private boolean isPageRequest(HttpServletRequest request) {
    final String accept = request.getHeader("Accept");
    return (accept != null) && accept.contains("text/html");
  }

  private boolean isLoginTarget(String target) {
    return target.equals(_loginTarget) || target.equals(_errorTarget);
  }
}
