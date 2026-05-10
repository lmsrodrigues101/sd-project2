package sd2526.trab.api.zoho.records;

import java.util.List;

public record ZohoAccountReply(ZohoStatus status, List<ZohoAccount> data) {
	
}
