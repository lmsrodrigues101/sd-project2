package sd2526.trab.api.zoho.records;
import java.util.List;


public record ZohoMessagesReply(ZohoStatus status, List<ZohoMessage> data) {}