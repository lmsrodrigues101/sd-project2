package sd2526.trab.api.zoho;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;
import sd2526.trab.api.zoho.records.ZohoAccount;
import sd2526.trab.api.zoho.records.ZohoAccountReply;
import sd2526.trab.api.zoho.records.ZohoMessage;
import sd2526.trab.api.zoho.records.ZohoMessagesReply;

import java.util.List;

public class Zoho {
	static final String MAIL_API_BASE = "https://mail.zoho.eu/api";
    static final String CLIENT_ID     = "1000.YGFQ2KOF7OT3E3I80JHE3XY05KN0OJ";
    static final String CLIENT_SECRET = "755865b47ff82e3ab4d430945080b171d50402266c";
    static final String REFRESH_TOKEN = "1000.16acd72950f98fc5b4f24edc834568cc.f4b480cf908c28f343e3a491534ca36b";

	private static final String ACCOUNTS = "/accounts";

    final OAuth20Service service;
    final ZohoTokenManager tokenManager;

    static Zoho instance;
    
    private Zoho() {
    	service = ZohoServiceFactory.buildService(CLIENT_ID, CLIENT_SECRET);
        tokenManager = new ZohoTokenManager(service, REFRESH_TOKEN);
    }
 
    synchronized public static Zoho getInstance() {
    	if( instance == null )
    		instance = new Zoho();
    	return instance;
    }

    public ZohoAccount getAccount() throws Exception {
        var accessToken = new OAuth2AccessToken( tokenManager.getValidAccessToken() );

        OAuthRequest request = new OAuthRequest(Verb.GET, MAIL_API_BASE + ACCOUNTS);
        service.signRequest(accessToken, request);

        try (Response response = service.execute(request)) {
            if( response.isSuccessful() ) {
                var body = response.getBody();
                var data = JSON.decode(body, ZohoAccountReply.class).data();
                if (data == null || data.isEmpty()) return null;
                return data.get(0);
            }
            else {
                System.err.println( response.getCode() + "/" + response.getBody() );
                return null;
            }
        }
    }
    public List<ZohoMessage> getMails(String accountId) throws Exception {
        var accessToken = new OAuth2AccessToken(tokenManager.getValidAccessToken());
        String url = MAIL_API_BASE + ACCOUNTS + "/" + accountId + "/messages/view";

        OAuthRequest request = new OAuthRequest(Verb.GET, url);
        request.addHeader("Accept", "application/json");

        service.signRequest(accessToken, request);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                var body = response.getBody();
                var reply = JSON.decode(body, ZohoMessagesReply.class);

                // Se a lista estiver vazia (nenhum e-mail), devolve uma lista vazia
                if (reply == null || reply.data() == null) {
                    return List.of();
                }
                return reply.data();
            } else {
                System.err.println("Erro ao listar emails: " + response.getCode() + "/" + response.getBody());
                return null;
            }
        }
    }

    public boolean deleteEmail(String accountId, String folderId, String messageId) throws Exception {
        // 1. Obter o token
        var accessToken = new OAuth2AccessToken(tokenManager.getValidAccessToken());

        // 2. Construir a URL (Slide 24: /api/accounts/{accountId}/folders/{folderId}/messages/{messageId})
        String url = MAIL_API_BASE + ACCOUNTS + "/" + accountId + "/folders/" + folderId + "/messages/" + messageId;

        // 3. Criar a requisição com o Verb.DELETE
        OAuthRequest request = new OAuthRequest(Verb.DELETE, url);
        request.addHeader("Accept", "application/json");

        // 4. Assinar a requisição
        service.signRequest(accessToken, request);

        // 5. Executar o pedido
        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                // O slide 24 mostra que o Zoho devolve um código 200 de sucesso
                return true;
            } else {
                System.err.println("Erro ao apagar email: " + response.getCode() + "/" + response.getBody());
                return false;
            }
        }
    }


    
    
}