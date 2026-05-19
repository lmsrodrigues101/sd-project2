package sd2526.trab.impl.rest.servers;

import java.util.logging.Logger;
import org.glassfish.jersey.server.ResourceConfig;

import sd2526.trab.api.java.Messages;
import sd2526.trab.impl.java.servers.JavaMessagesRep;
import sd2526.trab.impl.rest.filter.AuthenticationFilter;
import sd2526.trab.impl.rest.filter.VersionHeaderHandler;
import sd2526.trab.impl.utils.IP;

public class RestMessagesRepServer extends AbstractRestServer {
    public static final int PORT = 9876;

    private static Logger Log = Logger.getLogger(RestMessagesRepServer.class.getName());

    RestMessagesRepServer() {
        super(Log, Messages.SERVICE_NAME, PORT);
    }

    @Override
    void registerResources(ResourceConfig config) {
        String host = IP.hostname();
        String domain = host.contains(".") ? host.split("\\.")[1] : host;
        Messages repImpl = JavaMessagesRep.getInstance(domain);
        config.register(new RestMessagesResource(repImpl));
        config.register(AuthenticationFilter.class);
        config.register(VersionHeaderHandler.class);
    }

    public static void main(String[] args) {
        new RestMessagesRepServer().start();
    }
}