package sd2526.trab.api.zoho;

import com.github.scribejava.core.model.*;
import com.github.scribejava.core.oauth.OAuth20Service;
import sd2526.trab.api.Message;
import sd2526.trab.api.User;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;
import sd2526.trab.api.zoho.records.*;
import sd2526.trab.impl.java.servers.JavaBaseService;
import sd2526.trab.impl.java.clients.Clients;
import sd2526.trab.impl.api.java.AdminMessages;
import sd2526.trab.api.zoho.JSON;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static sd2526.trab.api.java.Result.ErrorCode.*;
import static sd2526.trab.api.java.Result.error;
import static sd2526.trab.api.java.Result.ok;

public class JavaZohoMessages extends JavaBaseService implements Messages, AdminMessages {


    private static Logger Log = Logger.getLogger(JavaZohoMessages.class.getName());

    private static final String SEPARATOR = "------";
    static final String MAIL_API_BASE = "https://mail.zoho.eu/api/accounts";
    static final String ZOHO_API_BASE = "https://mail.zoho.eu/api/accounts";
    static final String ACCOUNTS = "/accounts";

    // As tuas credenciais (mantive as tuas)
    static final String CLIENT_ID = "1000.YGFQ2KOF7OT3E3I80JHE3XY05KN0OJ";
    static final String CLIENT_SECRET = "755865b47ff82e3ab4d430945080b171d50402266c";
    static final String REFRESH_TOKEN = "1000.16acd72950f98fc5b4f24edc834568cc.f4b480cf908c28f343e3a491534ca36b";
    public static final String ZOHO_EMAIL = "lms.rodrigues10113@zohomail.eu";

    private String accountId;

    final OAuth20Service service;
    final ZohoTokenManager tokenManager;

    static JavaZohoMessages instance;

    // Construtor
    public JavaZohoMessages(boolean shouldClean) {
        service = ZohoServiceFactory.buildService(CLIENT_ID, CLIENT_SECRET);
        tokenManager = new ZohoTokenManager(service, REFRESH_TOKEN);

        try {
            // 1. Obter o Account ID no arranque
            this.accountId = fetchAccountId();
            Log.info("Zoho Account ID obtido: " + this.accountId);

            // 2. Limpar a Inbox se o Tester pedir (cleanState == true)
            if (shouldClean) {
                Log.info("A limpar a Inbox do Zoho Mail para testes...");
                deleteAllZohoMessages();
            }

        } catch (Exception e) {
            Log.severe("Erro ao inicializar ligação ao Zoho: " + e.getMessage());
            e.printStackTrace();
        }

    }

    synchronized public static JavaZohoMessages getInstance(boolean shouldClean) {
        if (instance == null)
            instance = new JavaZohoMessages(shouldClean);
        return instance;
    }

    private String fetchAccountId() throws Exception {
        OAuthRequest request = new OAuthRequest(Verb.GET, ZOHO_API_BASE);
        service.signRequest(tokenManager.getValidAccessToken(), request);

        Response response = service.execute(request);
        if (!response.isSuccessful()) {
            throw new RuntimeException("Falha ao obter Account ID: " + response.getBody());
        }

        // Usa a tua classe JSON para converter a string para um Map
        Map<String, Object> map = JSON.decode(response.getBody(), Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) map.get("data");

        if (data != null && !data.isEmpty()) {
            // No Gson, números grandes podem vir como Double ou String. Convertemos em segurança.
            Object idObj = data.get(0).get("accountId");
            return idObj instanceof Double ? String.format("%.0f", idObj) : String.valueOf(idObj);
        }
        throw new RuntimeException("Account ID não encontrado na resposta: " + response.getBody());
    }


    public List<ZohoMessage> getMails(String accountId) throws Exception {
        var accessToken = new OAuth2AccessToken(tokenManager.getValidAccessToken());
        String url = MAIL_API_BASE + ACCOUNTS + "/" + accountId + "/messages/view";
        OAuthRequest request = new OAuthRequest(Verb.GET, url);
        request.addHeader("Accept", "application/json");
        service.signRequest(accessToken, request);

        try (Response response = service.execute(request)) {
            if (response.isSuccessful()) {
                var reply = JSON.decode(response.getBody(), ZohoMessagesReply.class);
                if (reply == null || reply.data() == null) return List.of();
                return reply.data();
            }
            return null;
        }
    }

    protected Result<User> getUser(String user, String pwd) {
        var name = user.split("@", 2)[0];
        return Clients.UsersClient.get().getUser(name, pwd);
    }

    // --- MÉTODOS DA INTERFACE MESSAGES ---

    @Override
    public Result<String> postMessage(String pwd, Message msg) {
        Log.info("Zoho: postMessage para " + msg.getSender());

        // Para o teste manual no IDE, mantém as duas linhas abaixo comentadas
        // var userRes = getUser(msg.getSender(), pwd);
        // if (!userRes.isOK()) return error(FORBIDDEN);
        try {
            // 1. O enunciado diz que podemos simplesmente gerar um novo ID se não existir
            if (msg.getId() == null) {
                msg.setId(System.currentTimeMillis() + "@zoho");
            }

            // 2. Formatar o corpo do email com o separador sugerido
            String separator = "\n------\n";

            // ESCUDO ANTI-HTML: Trocar < e > por &lt; e &gt; para o Zoho não os comer
            String safeSender = msg.getSender() != null ?
                    msg.getSender().replace("<", "&lt;").replace(">", "&gt;") : "";

            String metadata = String.format("id: %s\nsender: %s\ndestination: %s\ncreationTime: %d",
                    msg.getId(),
                    safeSender,  // Usar o sender protegido!
                    String.join(",", msg.getDestination()),
                    msg.getCreationTime());

            String emailBody = msg.getContents() + separator + metadata;

            Map<String, Object> payloadMap = Map.of(
                    "fromAddress", ZOHO_EMAIL,
                    "toAddress", ZOHO_EMAIL,
                    "subject", msg.getSubject() != null ? msg.getSubject() : "Nova Mensagem",
                    "content", emailBody
            );
            String jsonPayload = JSON.encode(payloadMap);

            // 4. Fazer o POST para a API do Zoho
            String endpoint = ZOHO_API_BASE + "/" + this.accountId + "/messages";
            OAuthRequest request = new OAuthRequest(Verb.POST, endpoint);
            request.addHeader("Content-Type", "application/json");
            request.setPayload(jsonPayload);

            service.signRequest(tokenManager.getValidAccessToken(), request);
            Response response = service.execute(request);

            if (response.isSuccessful()) {
                return Result.ok(msg.getId());
            } else {
                Log.severe("Erro ao enviar email no Zoho: " + response.getBody());
                return Result.error(Result.ErrorCode.INTERNAL_ERROR);
            }

        } catch (Exception e) {
            Log.severe("Erro no postMessage: " + e.getMessage());
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        Log.info(() -> "Zoho getInboxMessage: mid = " + mid);
        try {
            List<Map<String, Object>> zohoMessages = getZohoMessagesMetadata();

            for (Map<String, Object> zMsg : zohoMessages) {
                Object zIdObj = zMsg.get("messageId");
                Object fIdObj = zMsg.get("folderId");
                String zohoId = zIdObj instanceof Double ? String.format("%.0f", zIdObj) : String.valueOf(zIdObj);
                String folderId = fIdObj instanceof Double ? String.format("%.0f", fIdObj) : String.valueOf(fIdObj);
                String subject = (String) zMsg.get("subject");

                Message msg = fetchAndParseMessage(zohoId, folderId, subject);

                // Se encontrarmos o email com o ID que o cliente pediu, devolvemos!
                if (msg != null && mid.equals(msg.getId())) {
                    return Result.ok(msg);
                }
            }
            return Result.error(Result.ErrorCode.NOT_FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        Log.info(() -> "Zoho getAllInboxMessages: name = " + name);
        try {
            List<Map<String, Object>> zohoMessages = getZohoMessagesMetadata();
            List<String> sdIds = new java.util.ArrayList<>();

            for (Map<String, Object> zMsg : zohoMessages) {
                // O Zoho pode devolver IDs como Double no Gson, temos de limpar
                Object zIdObj = zMsg.get("messageId");
                Object fIdObj = zMsg.get("folderId");
                String zohoId = zIdObj instanceof Double ? String.format("%.0f", zIdObj) : String.valueOf(zIdObj);
                String folderId = fIdObj instanceof Double ? String.format("%.0f", fIdObj) : String.valueOf(fIdObj);
                String subject = (String) zMsg.get("subject");

                Message msg = fetchAndParseMessage(zohoId, folderId, subject);
                if (msg != null && msg.getId() != null) {
                    sdIds.add(msg.getId());
                }
            }
            return Result.ok(sdIds);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    // 1. Obtém a lista de metadados de todos os emails no Zoho
    private List<Map<String, Object>> getZohoMessagesMetadata() throws Exception {
        String endpoint = ZOHO_API_BASE + "/" + this.accountId + "/messages/view";
        OAuthRequest request = new OAuthRequest(Verb.GET, endpoint);
        service.signRequest(tokenManager.getValidAccessToken(), request);

        Response response = service.execute(request);
        if (!response.isSuccessful()) return List.of();

        Map<String, Object> map = JSON.decode(response.getBody(), Map.class);
        return (List<Map<String, Object>>) map.get("data");
    }

    // 2. Vai buscar o corpo de um email específico usando o FolderID e o MessageID do Zoho
    private Message fetchAndParseMessage(String zohoId, String folderId, String subject) {
        try {
            String endpoint = ZOHO_API_BASE + "/" + this.accountId + "/folders/" + folderId + "/messages/" + zohoId + "/content";
            OAuthRequest request = new OAuthRequest(Verb.GET, endpoint);
            service.signRequest(tokenManager.getValidAccessToken(), request);

            Response response = service.execute(request);
            if (!response.isSuccessful()) return null;

            Map<String, Object> map = JSON.decode(response.getBody(), Map.class);
            Map<String, Object> data = (Map<String, Object>) map.get("data");
            String content = (String) data.get("content");

            return parseContentToMessage(content, subject);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 3. Extrai o nosso objeto Message do texto do email (limpando HTML)
    private Message parseContentToMessage(String rawContent, String subject) {
        if (rawContent == null) return null;

        // Descodificar o assunto (limpar lixo HTML que o Zoho adiciona aos títulos)
        if (subject != null) {
            subject = subject.replace("&#39;", "'")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">");
        }

        // 1. Converte <br> do Zoho em quebras de linha reais
        String plainText = rawContent.replaceAll("(?i)<br[^>]*>", "\n");

        // 2. Remove TODAS as tags HTML que o Zoho possa ter injetado
        plainText = plainText.replaceAll("<[^>]+>", "");

        // 3. Restaura os caracteres do corpo que foram convertidos em entidades HTML
        plainText = plainText.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&amp;", "&");

        String[] parts = plainText.split("------");
        if (parts.length < 2) return null; // Não é uma mensagem do nosso sistema

        Message msg = new Message();
        msg.setSubject(subject);
        msg.setContents(parts[0].trim());

        String metadata = parts[1];
        for (String line : metadata.split("\n")) {
            line = line.trim();
            if (line.startsWith("id:")) msg.setId(line.substring(3).trim());
            else if (line.startsWith("sender:")) msg.setSender(line.substring(7).trim());
            else if (line.startsWith("destination:")) msg.setDestination(Set.of(line.substring(12).trim().split(",")));
            else if (line.startsWith("creationTime:")) msg.setCreationTime(Long.parseLong(line.substring(13).trim()));
        }
        return msg;
    }

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        Log.info(() -> "Zoho removeInboxMessage: mid = " + mid);
        try {
            List<Map<String, Object>> zohoMessages = getZohoMessagesMetadata();

            for (Map<String, Object> zMsg : zohoMessages) {
                Object zIdObj = zMsg.get("messageId");
                Object fIdObj = zMsg.get("folderId");
                String zohoId = zIdObj instanceof Double ? String.format("%.0f", zIdObj) : String.valueOf(zIdObj);
                String folderId = fIdObj instanceof Double ? String.format("%.0f", fIdObj) : String.valueOf(fIdObj);
                String subject = (String) zMsg.get("subject");

                // Precisamos ler a mensagem para verificar se o nosso 'mid' bate certo
                Message msg = fetchAndParseMessage(zohoId, folderId, subject);

                if (msg != null && mid.equals(msg.getId())) {
                    deleteMessageInZoho(zohoId, folderId);
                    return Result.ok();
                }
            }
            return Result.error(Result.ErrorCode.NOT_FOUND);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    private void deleteMessageInZoho(String zohoId, String folderId) throws Exception {
        // Constrói o URL exato para apagar aquela mensagem naquela pasta
        String endpoint = ZOHO_API_BASE + "/" + this.accountId + "/folders/" + folderId + "/messages/" + zohoId;

        // Prepara um pedido HTTP do tipo DELETE
        OAuthRequest request = new OAuthRequest(Verb.DELETE, endpoint);

        // Assina com o token de acesso
        service.signRequest(tokenManager.getValidAccessToken(), request);

        // Executa o pedido
        Response response = service.execute(request);

        if (!response.isSuccessful()) {
            Log.warning("Falha ao apagar mensagem Zoho ID: " + zohoId + " - " + response.getBody());
        } else {
            Log.info("Mensagem apagada com sucesso no Zoho (ID: " + zohoId + ")");
        }
    }

    @Override
    public Result<Void> deleteMessage(String name, String mid, String pwd) {
        return removeInboxMessage(name, mid, pwd);
    }

    // A rotina de arranque que o Tester exige quando arranca com "cleanState == true"
    private void deleteAllZohoMessages() throws Exception {
        List<Map<String, Object>> zohoMessages = getZohoMessagesMetadata();

        for (Map<String, Object> zMsg : zohoMessages) {
            Object zIdObj = zMsg.get("messageId");
            Object fIdObj = zMsg.get("folderId");
            String zohoId = zIdObj instanceof Double ? String.format("%.0f", zIdObj) : String.valueOf(zIdObj);
            String folderId = fIdObj instanceof Double ? String.format("%.0f", fIdObj) : String.valueOf(fIdObj);

            deleteMessageInZoho(zohoId, folderId);
        }
        Log.info("Clean state concluído. Foram apagadas " + zohoMessages.size() + " mensagens do Zoho Mail.");
    }

    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {

            return Result.error(Result.ErrorCode.INTERNAL_ERROR);

    }

    @Override
    public Result<Void> remotePostMessage(Message msg) {
        Log.info(() -> "Zoho: remotePostMessage recebida de " + msg.getSender());
        // Reutilizamos a nossa função postMessage para enviar o email no Zoho
        // Passamos uma password "dummy" pois a comunicação entre servidores já foi autenticada
        Result<String> res = postMessage("admin", msg);

        if (res.isOK()) {
            return Result.ok();
        } else {
            return Result.error(res.error());
        }
    }

    @Override
    public Result<Void> remoteDeleteMessage(String mid) {
        Log.info(() -> "Zoho: remoteDeleteMessage mid = " + mid);
        return removeInboxMessage("admin", mid, "admin");
    }

    @Override
    public Result<Void> remoteDeleteUserInbox(String name) {
        Log.info(() -> "Zoho: remoteDeleteUserInbox name = " + name);
        try {
            // Como este proxy representa apenas 1 utilizador real, apagamos tudo
            deleteAllZohoMessages();
            return Result.ok();
        } catch (Exception e) {
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
    }

}