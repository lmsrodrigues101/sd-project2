package sd2526.trab.impl.rest.servers;

import org.glassfish.jersey.server.ResourceConfig;
import sd2526.trab.impl.zoho.JavaZohoMessages;

import java.util.logging.Logger;

public class RestZohoMessagesServer extends AbstractRestServer {

    private static Logger Log = Logger.getLogger(RestZohoMessagesServer.class.getName());
    public static final int PORT = 5567;
    private static final String SERVICE = "Messages";
    private final boolean shouldClean;

    public RestZohoMessagesServer(int port, boolean shouldClean) {
        super(Log, SERVICE, port);
        this.shouldClean = shouldClean;
    }

    @Override
    protected void registerResources(ResourceConfig config) {
        var zohoImpl = JavaZohoMessages.getInstance(shouldClean);
        config.register(new RestMessagesResource(zohoImpl));
    }

    public static void main(String[] args) {
        try {
            boolean shouldClean = args.length > 0 && args[0].equalsIgnoreCase("true");
            int port = args.length > 1 ? Integer.parseInt(args[1]) : PORT;

            Log.info("Arrancando o Proxy Zoho... Limpar Inbox? " + shouldClean);
            new RestZohoMessagesServer(port, shouldClean).start();

        } catch (Exception e) {
            Log.severe("Erro ao arrancar o servidor Zoho: " + e.getMessage());
        }
    }
}