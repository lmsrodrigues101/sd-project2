package sd2526.trab.impl.java.servers;

import java.util.List;
import java.util.logging.Logger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.google.gson.Gson;
import sd2526.trab.api.Message;
import sd2526.trab.api.java.Messages;
import sd2526.trab.api.java.Result;
import sd2526.trab.impl.api.java.AdminMessages;
import sd2526.trab.impl.kafka.KafkaConfig;
import sd2526.trab.impl.kafka.KafkaOperation;
import sd2526.trab.impl.kafka.KafkaPublisher;
import sd2526.trab.impl.kafka.KafkaSubscriber;
import sd2526.trab.impl.kafka.KafkaUtils;
import sd2526.trab.impl.kafka.RecordProcessor;
import sd2526.trab.impl.kafka.utils.SyncPoint;
import sd2526.trab.impl.rest.filter.VersionHeaderHandler;
import java.util.concurrent.ConcurrentHashMap;


public class JavaMessagesRep implements Messages, AdminMessages {

    private static final Logger Log = Logger.getLogger(JavaMessagesRep.class.getName());
    private static JavaMessagesRep instance;

    private final String topic;
    private final KafkaPublisher publisher;
    private final KafkaSubscriber subscriber;
    private final SyncPoint syncPoint;
    private final Gson gson;
    private final ConcurrentHashMap<String, Long> highestSidPerDomain;

    private final JavaMessages localImpl;

    private JavaMessagesRep(String domain) {
        this.topic = KafkaConfig.KAFKA_BASE_TOPIC + domain;
        this.localImpl = JavaMessages.getInstance();
        this.syncPoint = SyncPoint.getSyncPoint();
        this.gson = new Gson();
        this.highestSidPerDomain = new ConcurrentHashMap<>();

        KafkaUtils.createTopic(topic);

        this.publisher = KafkaPublisher.createPublisher();
        this.subscriber = KafkaSubscriber.createSubscriber("messages-group-", List.of(topic));

        startConsuming();
    }

    public static synchronized JavaMessagesRep getInstance(String domain) {
        if (instance == null) {
            instance = new JavaMessagesRep(domain);
        }
        return instance;
    }

    // leituras

    @Override
    public Result<List<String>> getAllInboxMessages(String name, String pwd) {
        Long version = VersionHeaderHandler.version.get();
        if (version != null) {
            syncPoint.waitForVersion(version);
        }
        VersionHeaderHandler.version.set(syncPoint.getVersion());
        return localImpl.getAllInboxMessages(name, pwd);
    }

    @Override
    public Result<Message> getInboxMessage(String name, String mid, String pwd) {
        Long version = VersionHeaderHandler.version.get();
        if (version != null) {
            syncPoint.waitForVersion(version);
        }
        VersionHeaderHandler.version.set(syncPoint.getVersion());
        return localImpl.getInboxMessage(name, mid, pwd);
    }

    @Override
    public Result<List<String>> searchInbox(String name, String pwd, String query) {
        Long version = VersionHeaderHandler.version.get();
        if (version != null) {
            syncPoint.waitForVersion(version);
        }
        VersionHeaderHandler.version.set(syncPoint.getVersion());
        return localImpl.searchInbox(name, pwd, query);
    }

    // escritas

    @Override
    public Result<String> postMessage(String pwd, Message msg) {
        KafkaOperation op = new KafkaOperation(KafkaOperation.OpType.POST_MESSAGE, gson.toJson(new Object[]{pwd, msg}));
        return replicateAndExecuteString(op);
    }

    @Override
    public Result<Void> removeInboxMessage(String name, String mid, String pwd) {
        KafkaOperation op = new KafkaOperation(KafkaOperation.OpType.REMOVE_INBOX_MESSAGE, gson.toJson(new String[]{name, mid, pwd}));
        return replicateAndExecuteVoid(op);
    }

    @Override
    public Result<Void> deleteMessage(String name, String mid, String pwd) {
        KafkaOperation op = new KafkaOperation(KafkaOperation.OpType.DELETE_MESSAGE, gson.toJson(new String[]{name, mid, pwd}));
        return replicateAndExecuteVoid(op);
    }

    @Override
    public Result<Void> remotePostMessage(Message msg) {
        KafkaOperation op = new KafkaOperation(KafkaOperation.OpType.REMOTE_POST_MESSAGE, gson.toJson(msg));
        return replicateAndExecuteVoid(op);
    }

    @Override
    public Result<Void> remoteDeleteMessage(String mid) {
        KafkaOperation op = new KafkaOperation(KafkaOperation.OpType.REMOTE_DELETE_MESSAGE, mid);
        return replicateAndExecuteVoid(op);
    }

    @Override
    public Result<Void> remoteDeleteUserInbox(String name) {
        KafkaOperation op = new KafkaOperation(KafkaOperation.OpType.REMOTE_DELETE_USER_INBOX, name);
        return replicateAndExecuteVoid(op);
    }

    // helpers

    private Result<String> replicateAndExecuteString(KafkaOperation op) {
        long offset = publisher.publish(topic, gson.toJson(op));
        if (offset < 0) return Result.error(Result.ErrorCode.INTERNAL_ERROR);

        String resultStr = syncPoint.waitForResult(offset);
        VersionHeaderHandler.version.set(offset);
        if (resultStr != null && resultStr.startsWith("ERROR:")) {
            return Result.error(Result.ErrorCode.valueOf(resultStr.split(":")[1]));
        }
        return Result.ok(resultStr);
    }

    private Result<Void> replicateAndExecuteVoid(KafkaOperation op) {
        long offset = publisher.publish(topic, gson.toJson(op));
        if (offset < 0) return Result.error(Result.ErrorCode.INTERNAL_ERROR);

        String resultStr = syncPoint.waitForResult(offset);
        VersionHeaderHandler.version.set(offset);

        if (resultStr != null && resultStr.startsWith("ERROR:")) {
            return Result.error(Result.ErrorCode.valueOf(resultStr.split(":")[1]));
        }
        return Result.ok();
    }

    private void startConsuming() {
        subscriber.start(new RecordProcessor() {
            @Override
            public void onReceive(ConsumerRecord<String, String> r) {
                long offset = r.offset();
                try {
                    KafkaOperation op = gson.fromJson(r.value(), KafkaOperation.class);
                    Log.info("KAFKA: Processing offset " + offset + " - Operation: " + op.type);
                    Result<?> executionResult = executeLocally(op);
                    if (executionResult.isOK()) {
                        Object val = executionResult.value();
                        syncPoint.setResult(offset, val == null ? "SUCCESS" : val.toString());
                    } else {
                        syncPoint.setResult(offset, "ERROR:" + executionResult.error().name());
                    }

                } catch (Exception e) {
                    Log.severe("Error processing Kafka message: " + e.getMessage());
                    e.printStackTrace();
                    syncPoint.setResult(offset, "ERROR:INTERNAL_ERROR");
                }
            }
        });
    }
    private Result<?> executeLocally(KafkaOperation op) {
        com.google.gson.JsonArray args = op.argsJson != null && op.argsJson.startsWith("[")
                ? com.google.gson.JsonParser.parseString(op.argsJson).getAsJsonArray()
                : null;

        Result<?> result;

        try {
            switch (op.type) {
                case POST_MESSAGE:
                    result = localImpl.postMessage(args.get(0).getAsString(), gson.fromJson(args.get(1), Message.class));
                    break;

                case REMOVE_INBOX_MESSAGE:
                    result = localImpl.removeInboxMessage(args.get(0).getAsString(), args.get(1).getAsString(), args.get(2).getAsString());
                    break;

                case DELETE_MESSAGE:
                    result = localImpl.deleteMessage(args.get(0).getAsString(), args.get(1).getAsString(), args.get(2).getAsString());
                    break;
                case REMOTE_POST_MESSAGE: {
                    Message remoteMsg = gson.fromJson(op.argsJson, Message.class);
                    if (!shouldExecuteRemotePost(remoteMsg.getId())) {
                        return Result.ok();
                    }
                    result = localImpl.remotePostMessage(remoteMsg);
                    break;
                }
                case REMOTE_DELETE_MESSAGE: {
                    String mid = op.argsJson;
                    updateMaxSidForDelete(mid);
                    result = localImpl.remoteDeleteMessage(mid);
                    break;
                }
                case REMOTE_DELETE_USER_INBOX:
                    result = localImpl.remoteDeleteUserInbox(op.argsJson);
                    break;

                default:
                    result = Result.error(Result.ErrorCode.NOT_IMPLEMENTED);
            }
        } catch (Exception e) {
            Log.severe("Error parsing JSON arguments: " + e.getMessage());
            return Result.error(Result.ErrorCode.INTERNAL_ERROR);
        }
        if (!result.isOK() && result.error() == Result.ErrorCode.CONFLICT) {
            Log.info("Duplicate operatioon.");
            return Result.ok();
        }

        return result;
    }
    private boolean shouldExecuteRemotePost(String mid) {
        try {
            String[] parts = mid.split("\\+");
            String sourceDomain = parts[0];
            long currentSid = Long.parseLong(parts[1]);
            long maxSid = highestSidPerDomain.getOrDefault(sourceDomain, -1L);
            if (currentSid <= maxSid) {
                Log.info("Operation from domain " + sourceDomain +
                        " ignored. Current SID (" + currentSid +
                        ") is <= max SID (" + maxSid + ").");
                return false;
            }
            highestSidPerDomain.put(sourceDomain, currentSid);
            return true;

        } catch (Exception e) {
            return true;
        }
    }
    private void updateMaxSidForDelete(String mid) {
        if (mid == null || !mid.contains("+")) return;

        try {
            String[] parts = mid.split("\\+");
            String sourceDomain = parts[0];
            long currentSid = Long.parseLong(parts[1]);
            highestSidPerDomain.compute(sourceDomain, (k, v) -> (v == null || currentSid > v) ? currentSid : v);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}