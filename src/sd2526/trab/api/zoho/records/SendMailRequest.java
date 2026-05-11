package sd2526.trab.api.zoho.records;

public record SendMailRequest(String fromAddress, String toAddress, String subject, String content) {}