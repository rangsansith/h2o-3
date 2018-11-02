package ai.h2o.jetty8;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import water.server.ServletUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class GateHandler extends AbstractHandler {
  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
    ServletUtils.startRequestLifecycle();
    while (!Jetty8HTTPD._acceptRequests) {
      try { Thread.sleep(100); }
      catch (Exception ignore) {}
    }

    boolean isXhrRequest = false;
    if (request != null) {
      isXhrRequest = ServletUtils.isXhrRequest(request);
    }
    ServletUtils.setCommonResponseHttpHeaders(response, isXhrRequest);
  }
}
