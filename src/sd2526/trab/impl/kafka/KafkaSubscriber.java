package sd2526.trab.impl.kafka;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public class KafkaSubscriber {

	static public KafkaSubscriber createSubscriber(String groupId, List<String> topics) {
		// Usamos nanoTime para garantir que cada réplica tem o seu próprio grupo
		// e recebe TODOS os eventos (broadcast em vez de load balancing)
		Properties props = KafkaConfig.getConsumerProperties(groupId + System.nanoTime());
		return new KafkaSubscriber(new KafkaConsumer<String, String>(props), topics);
	}

	private static final long POLL_TIMEOUT = 1L;
	final KafkaConsumer<String, String> consumer;

	public KafkaSubscriber(KafkaConsumer<String, String> consumer, List<String> topics) {
		this.consumer = consumer;
		this.consumer.subscribe(topics);
	}

	public void start(RecordProcessor recordProcessor) {
		new Thread(() -> {
			for (;;) {
				ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(POLL_TIMEOUT));
				Iterator<ConsumerRecord<String, String>> iterator = records.iterator();

				while (iterator.hasNext()) {
					ConsumerRecord<String, String> r = iterator.next();
					recordProcessor.onReceive(r);
				}
			}
		}).start();
	}
}