package sd2526.trab.impl.rest.servers;

import org.glassfish.jersey.server.ResourceConfig;

import java.util.logging.Logger;

import sd2526.trab.api.java.Messages;
import sd2526.trab.api.zoho.*;


public class RestZohoMessagesServer extends AbstractRestServer{

    public static final int PORT = 14567; // Garante que usas uma porta diferente do Users
    private static final Logger Log = Logger.getLogger(RestZohoMessagesServer.class.getName());


    public RestZohoMessagesServer() {
        super(Log, Messages.SERVICE_NAME, PORT);
    }

    @Override
    void registerResources(ResourceConfig config) {

    }

    public static void main(String[] args) {
        try {
            // 1. Ler o parâmetro do Tester (se não houver, assume false)
            boolean cleanState = false;
            if (args.length > 0) {
                cleanState = Boolean.parseBoolean(args[0]);
            }

            // 2. Executar o Clean State se for true
            if (cleanState) {
                Log.info("Clean state solicitado (args[0] = true). A limpar a caixa de correio do Zoho...");
                cleanMailbox();
            } else {
                Log.info("Arranque normal (args[0] = false). O estado da caixa de correio será mantido.");
            }

            // 3. Arrancar o servidor REST normalmente
            new RestZohoMessagesServer().start();

        } catch (Exception e) {
            Log.severe("Erro fatal ao iniciar servidor de Mensagens Zoho: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void cleanMailbox() throws Exception {
        Zoho zoho = Zoho.getInstance();
        var account = zoho.getAccount();

        if (account == null) {
            throw new RuntimeException("Não foi possível aceder à conta Zoho. Verifica o Refresh Token.");
        }
        String accountId = account.accountId();
        // Vamos buscar todos os emails
        var emails = zoho.getMails(accountId);

        if (emails != null && !emails.isEmpty()) {
            Log.info("Encontrados " + emails.size() + " emails. A iniciar limpeza...");

            for (var email : emails) {
                // A API do Zoho precisa do accountId, folderId e messageId para apagar
                boolean apagado = zoho.deleteEmail(accountId, email.folderId(), email.messageId());
                if (!apagado) {
                    Log.warning("Falha ao apagar o email ID: " + email.messageId());
                }
            }
            Log.info("Caixa de correio limpa com sucesso!");
        } else {
            Log.info("A caixa de correio já está vazia.");
        }
    }
}
