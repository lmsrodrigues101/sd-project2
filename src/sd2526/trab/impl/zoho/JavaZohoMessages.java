package sd2526.trab.impl.zoho;

import com.github.scribejava.core.model.*;
import com.github.scribejava.core.oauth.OAuth20Service;
import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;
import sd2526.trab.impl.java.servers.JavaBaseService;
import sd2526.trab.impl.api.java.AdminMessages;
import sd2526.trab.impl.zoho.records.*;

import java.util.*;
import java.util.logging.Logger;

import static sd2526.trab.api.java.Result.ErrorCode.*;
import static sd2526.trab.api.java.Result.error;

public class JavaZohoMessages extends JavaBaseService implements Messages, AdminMessages {

    private static final Logger LOG = Logger.getLogger(JavaZohoMessages.class.getName());
    private static final String SEPARATOR = "------";
    static final String ZOHO_API_BASE = "https://mail.zoho.eu/api/accounts";
    static final String CLIENT_ID = "1000.YGFQ2KOF7OT3E3I80JHE3XY05KN0OJ";
    static final String CLIENT_SECRET = "755865b47ff82e3ab4d430945080b171d50402266c";
    static final String REFRESH_TOKEN = "1000.16acd72950f98fc5b4f24edc834568cc.f4b480cf908c28f343e3a491534ca36b";
    public static final String ZOHO_EMAIL = "lms.rodrigues10113@zohomail.eu";

    private String accountId;
    final OAuth20Service service;
    final ZohoTokenManager tokenManager;
    static JavaZohoMessages instance;

    public JavaZohoMessages(boolean shouldClean) {
        service = ZohoServiceFactory.buildService(CLIENT_ID, CLIENT_SECRET);
        tokenManager = new ZohoTokenManager(service, REFRESH_TOKEN);
        try {
            this.accountId = fetchAccountId();
            if (shouldClean) deleteAllZohoMessages();
        } catch (Exception e) {
            LOG.severe("Failed to initialize Zoho: " + e.getMessage());
        }
    }

    synchronized public static JavaZohoMessages getInstance(boolean shouldClean) {
        if (instance == null) instance = new JavaZohoMessages(shouldClean);
        return instance;
    }

    @Override
    public Result<String> postMessage(String pwd, Message msg) {
        try {
            if (msg.getId() == null) msg.setId(System.currentTimeMillis() + "@zoho");

            String safeSender = msg.getSender() != null
                    ? msg.getSender().replace("<", "&lt;").replace(">", "&gt;")
                    : "";

            String emailBody = msg.getContents() + "\n" + SEPARATOR + "\n" +
                    String.format("id: %s\nsender: %s\ndestination: %s\ncreationTime: %d",
                            msg.getId(),
                            safeSender,
                            String.join(",", msg.getDestination()),
                            msg.getCreationTime());

            Map<String, Object> payload = Map.of("fromAddress", ZOHO_EMAIL, "toAddress", ZOHO_EMAIL,
                    "subject", msg.getSubject() != null ? msg.getSubject() : "New Message",
                    "content", emailBody);

            OAuthRequest request = new OAuthRequest(Verb.POST, ZOHO_API_BASE + "/" + accountId + "/messages");
            request.addHeader("Content-Type", "application/json");
            request.setPayload(JSON.encode(payload));
            service.signRequest(tokenManager.getValidAccessToken(), request);

            return service.execute(request).isSuccessful() ? Result.ok(msg.getId()) : error(INTERNAL_ERROR);
        } catch (Exception e) { return error(INTERNAL_ERROR); }
    }

    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        try {
            for (ZohoMessage zMsg : getZohoMessagesMetadata()) {
                Message msg = fetchAndParseMessage(zMsg);
                if (msg != null && mid.equals(msg.getId())) return Result.ok(msg);
            }
            return error(NOT_FOUND);
        } catch (Exception e) { return error(INTERNAL_ERROR); }
    }

    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        try {
            List<String> ids = new ArrayList<>();
            for (ZohoMessage zMsg : getZohoMessagesMetadata()) {
                Message msg = fetchAndParseMessage(zMsg);
                if (msg != null) ids.add(msg.getId());
            }
            return Result.ok(ids);
        } catch (Exception e) { return error(INTERNAL_ERROR); }
    }

    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {
        try {
            List<String> matches = new ArrayList<>();
            String q = query.toLowerCase();
            for (ZohoMessage zMsg : getZohoMessagesMetadata()) {
                Message msg = fetchAndParseMessage(zMsg);
                if (msg != null && (msg.getSubject().toLowerCase().contains(q) || msg.getContents().toLowerCase().contains(q)))
                    matches.add(msg.getId());
            }
            return Result.ok(matches);
        } catch (Exception e) { return error(INTERNAL_ERROR); }
    }

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        try {
            for (ZohoMessage zMsg : getZohoMessagesMetadata()) {
                Message msg = fetchAndParseMessage(zMsg);
                if (msg != null && mid.equals(msg.getId())) {
                    deleteMessageInZoho(zMsg.messageId(), zMsg.folderId());
                    return Result.ok();
                }
            }
            return error(NOT_FOUND);
        } catch (Exception e) { return error(INTERNAL_ERROR); }
    }

    @Override
    public Result<Void> deleteMessage(String name, String mid, String pwd) { return removeInboxMessage(name, mid, pwd); }

    @Override
    public Result<Void> remotePostMessage(Message msg) {
        return postMessage("admin", msg).isOK() ? Result.ok() : error(INTERNAL_ERROR);
    }

    @Override
    public Result<Void> remoteDeleteMessage(String mid) { return removeInboxMessage("admin", mid, "admin"); }

    @Override
    public Result<Void> remoteDeleteUserInbox(String name) {
        try { deleteAllZohoMessages(); return Result.ok(); } catch (Exception e) { return error(INTERNAL_ERROR); }
    }



    private String fetchAccountId() throws Exception {
        OAuthRequest request = new OAuthRequest(Verb.GET, ZOHO_API_BASE);
        service.signRequest(tokenManager.getValidAccessToken(), request);
        Response response = service.execute(request);
        return ((List<Map<String, Object>>) JSON.decode(response.getBody(), Map.class).get("data")).get(0).get("accountId").toString();
    }

    private List<ZohoMessage> getZohoMessagesMetadata() throws Exception {
        OAuthRequest request = new OAuthRequest(Verb.GET, ZOHO_API_BASE + "/" + accountId + "/messages/view");
        service.signRequest(tokenManager.getValidAccessToken(), request);
        return JSON.decode(service.execute(request).getBody(), ZohoMessagesReply.class).data();
    }

    private Message fetchAndParseMessage(ZohoMessage zMsg) throws Exception {
        OAuthRequest request = new OAuthRequest(Verb.GET, ZOHO_API_BASE + "/" + accountId + "/folders/" + zMsg.folderId() + "/messages/" + zMsg.messageId() + "/content");
        service.signRequest(tokenManager.getValidAccessToken(), request);
        String content = JSON.decode(service.execute(request).getBody(), ZohoMessageContentReply.class).data().content();

        String plain = content.replaceAll("(?i)<br[^>]*>", "\n")
                .replaceAll("<[^>]+>", "");

        plain = plain.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&amp;", "&");
        String[] parts = plain.split(SEPARATOR);
        if (parts.length < 2) return null;

        Message m = new Message();
        String subject = zMsg.subject();
        if (subject != null) {
            subject = subject.replace("&#39;", "'")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">");
        }
        m.setSubject(subject);
        m.setContents(parts[0].trim());


        for (String line : parts[1].split("\n")) {
            line = line.trim();
            if (line.startsWith("id:")) m.setId(line.substring(3).trim());
            else if (line.startsWith("sender:")) m.setSender(line.substring(7).trim());
            else if (line.startsWith("destination:")) {
                String dests = line.substring(12).trim();
                if (!dests.isEmpty()) m.setDestination(Set.of(dests.split(",")));
                else m.setDestination(Collections.emptySet());
            }
            else if (line.startsWith("creationTime:")) m.setCreationTime(Long.parseLong(line.substring(13).trim()));
        }
        return m;
    }

    private void deleteMessageInZoho(String mId, String fId) throws Exception {
        OAuthRequest request = new OAuthRequest(Verb.DELETE, ZOHO_API_BASE + "/" + accountId + "/folders/" + fId + "/messages/" + mId);
        service.signRequest(tokenManager.getValidAccessToken(), request);
        service.execute(request);
    }

    private void deleteAllZohoMessages() throws Exception {
        for (ZohoMessage zMsg : getZohoMessagesMetadata()) deleteMessageInZoho(zMsg.messageId(), zMsg.folderId());
    }
}