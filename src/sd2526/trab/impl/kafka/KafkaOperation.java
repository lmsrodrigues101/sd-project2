package sd2526.trab.impl.kafka;

public class KafkaOperation {

    public enum OpType {
        POST_MESSAGE,
        DELETE_MESSAGE,
        REMOVE_INBOX_MESSAGE,
        REMOTE_POST_MESSAGE,
        REMOTE_DELETE_MESSAGE,
        REMOTE_DELETE_USER_INBOX
    }

    public OpType type;
    public String argsJson;

    public KafkaOperation() {}

    public KafkaOperation(OpType type, String argsJson) {
        this.type = type;
        this.argsJson = argsJson;
    }
}