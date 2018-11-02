package ai.h2o.jetty8;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.servlet.ServletContextHandler;
import water.ExtensionManager;
import water.H2O;
import water.api.DatasetServlet;
import water.api.NpsBinServlet;
import water.api.PostFileServlet;
import water.api.PutKeyServlet;
import water.api.RequestServer;
import water.server.H2OServletContainer;
import water.server.RequestAuthExtension;
import water.server.ServletUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Embedded Jetty instance inside H2O.
 * This is intended to be a singleton per H2O node.
 */
public class Jetty8HTTPD extends AbstractJetty8HTTPD implements H2OServletContainer {
  //------------------------------------------------------------------------------------------
  // Object-specific things.
  //------------------------------------------------------------------------------------------
  static volatile boolean _acceptRequests = false;

  /**
   * Create bare Jetty object.
   */
  Jetty8HTTPD() {
    super(H2O.ARGS);
  }

  public void acceptRequests() {
    _acceptRequests = true;
  }

  @Override
  protected RuntimeException failEx(String message) {
    return H2O.fail(message);
  }

  @Override
  protected void registerHandlers(HandlerWrapper handlerWrapper, ServletContextHandler context) {
    context.addServlet(NpsBinServlet.class,   "/3/NodePersistentStorage.bin/*");
    context.addServlet(PostFileServlet.class, "/3/PostFile.bin");
    context.addServlet(PostFileServlet.class, "/3/PostFile");
    context.addServlet(DatasetServlet.class,  "/3/DownloadDataset");
    context.addServlet(DatasetServlet.class,  "/3/DownloadDataset.bin");
    context.addServlet(PutKeyServlet.class,   "/3/PutKey.bin");
    context.addServlet(PutKeyServlet.class,   "/3/PutKey");
    context.addServlet(RequestServer.class,   "/");

    final List<Handler> extHandlers = new ArrayList<>();
    extHandlers.add(new AuthenticationHandler());
    // here we wrap generic authentication handlers into jetty-aware wrappers
    for (final RequestAuthExtension requestAuthExtension : ExtensionManager.getInstance().getAuthExtensions()) {
      extHandlers.add(new AbstractHandler() {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
          if (requestAuthExtension.handle(target, request, response)) {
            baseRequest.setHandled(true);
          }
        }
      });
    }
    //
    extHandlers.add(context);

    // Handlers that can only be invoked for an authenticated user (if auth is enabled)
    HandlerCollection authHandlers = new HandlerCollection();
    authHandlers.setHandlers(extHandlers.toArray(new Handler[0]));

    // LoginHandler handles directly login requests and delegates the rest to the authHandlers
    LoginHandler loginHandler = new LoginHandler("/login", "/loginError");
    loginHandler.setHandler(authHandlers);

    HandlerCollection hc = new HandlerCollection();
    hc.setHandlers(new Handler[]{
            new GateHandler(),
            loginHandler
    });
    handlerWrapper.setHandler(hc);
  }

  @Override
  protected void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
    ServletUtils.sendResponseError(response, HttpServletResponse.SC_UNAUTHORIZED, message);
  }

}
