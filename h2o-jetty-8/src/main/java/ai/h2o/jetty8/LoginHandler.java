package ai.h2o.jetty8;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import water.api.RequestServer;
import water.server.ServletUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class LoginHandler extends HandlerWrapper {

  private String _loginTarget;
  private String _errorTarget;

  LoginHandler(String loginTarget, String errorTarget) {
    _loginTarget = loginTarget;
    _errorTarget = errorTarget;
  }

  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    if (isLoginTarget(target)) {
      if (isPageRequest(request)) {
        sendLoginForm(request, response);
      } else {
        ServletUtils.sendResponseError(response, HttpServletResponse.SC_UNAUTHORIZED, "Access denied. Please login.");
      }
      baseRequest.setHandled(true);
    } else {
      // not for us, invoke wrapped handler
      super.handle(target, baseRequest, request, response);
    }
  }

  private void sendLoginForm(HttpServletRequest request, HttpServletResponse response) {
    final String uri = ServletUtils.getDecodedUri(request);
    try {
      byte[] bytes;
      try (InputStream resource = water.init.JarHash.getResource2("/login.html")) {
        if (resource == null) {
          throw new IllegalStateException("Login form not found");
        }
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        water.util.FileUtils.copyStream(resource, baos, 2048);
        bytes = baos.toByteArray();
      }
      response.setContentType(RequestServer.MIME_HTML);
      response.setContentLength(bytes.length);
      ServletUtils.setResponseStatus(response, HttpServletResponse.SC_OK);
      final OutputStream os = response.getOutputStream();
      water.util.FileUtils.copyStream(new ByteArrayInputStream(bytes), os, 2048);
      // TODO: this whole method can be replaced with just:
      // org.apache.commons.io.IOUtils.copy( water.init.JarHash.getResource2("/login.html"), os);
      // but it needs to be properly tested
    } catch (Exception e) {
      ServletUtils.sendErrorResponse(response, e, uri);
    } finally {
      ServletUtils.logRequest("GET", request, response);
    }
  }
  private boolean isPageRequest(HttpServletRequest request) {
    String accept = request.getHeader("Accept");
    return (accept != null) && accept.contains(RequestServer.MIME_HTML);
  }
  private boolean isLoginTarget(String target) {
    return target.equals(_loginTarget) || target.equals(_errorTarget);
  }
}
