package sd2526.trab.impl.kafka;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

public class KafkaPublisher {

	static public KafkaPublisher createPublisher() {
		Properties props = KafkaConfig.getProducerProperties();
		return new KafkaPublisher(new KafkaProducer<String, String>(props));
	}

	private final KafkaProducer<String, String> producer;

	private KafkaPublisher(KafkaProducer<String, String> producer) {
		this.producer = producer;
	}

	public void close() {
		this.producer.close();
	}

	public long publish(String topic, String key, String value) {
		try {
			Future<RecordMetadata> promise = producer.send(new ProducerRecord<String, String>(topic, key, value));
			RecordMetadata rec = promise.get();
			return rec.offset();
		} catch (ExecutionException | InterruptedException x) {
			x.printStackTrace();
			return -1;
		}
	}

	public long publish(String topic, String value) {
		try {
			Future<RecordMetadata> promise = producer.send(new ProducerRecord<String, String>(topic, value));
			RecordMetadata rec = promise.get();
			return rec.offset();
		} catch (ExecutionException | InterruptedException x) {
			x.printStackTrace();
			return -1;
		}
	}
}