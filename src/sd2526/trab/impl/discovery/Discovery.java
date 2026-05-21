package sd2526.trab.impl.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentSkipListSet;

import sd2526.trab.impl.utils.Sleep;


/**
 * <p>A class interface to perform service discovery based on periodic
 * announcements over multicast communication.</p>
 *
 */

public interface Discovery {

	/**
	 * Used to announce the URI of the given service name.
	 * @param serviceName - the name of the service
	 * @param serviceURI - the uri of the service
	 */
	public void announce(String serviceName, String serviceURI);

	/**
	 * Get discovered URIs for a given service name
	 * @param serviceName - name of the service
	 * @param minReplies - minimum number of requested URIs. Blocks until the number is satisfied.
	 * @return array with the discovered URIs for the given service name.
	 */
	public URI[] knownUrisOf(String serviceName, int minReplies);

	/**
	 * Get the instance of the Discovery service
	 * @return the singleton instance of the Discovery service
	 */
	public static Discovery getInstance() {
		return DiscoveryImpl.getInstance();
	}
}

/**
 * Implementation of the multicast discovery service
 */
class DiscoveryImpl implements Discovery {

	private static Logger Log = Logger.getLogger(Discovery.class.getName());

	static final int DISCOVERY_RETRY_TIMEOUT = 5000;
	static final int DISCOVERY_ANNOUNCE_PERIOD = 1000;
	static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("226.226.226.226", 2266);

	// Used separate the two fields that make up a service announcement.
	private static final String DELIMITER = "\t";

	private static final int MAX_DATAGRAM_SIZE = 65536;

	private static Discovery singleton;

	private final Map<String, ConcurrentSkipListSet<ServerInfo>> storedAnnouncements = new ConcurrentHashMap<>();

	synchronized static Discovery getInstance() {
		if (singleton == null) {
			singleton = new DiscoveryImpl();
		}
		return singleton;
	}

	private DiscoveryImpl() {
		this.startListener();
	}

	@Override
	public void announce(String serviceName, String serviceURI) {
		Log.info(String.format("Starting Discovery announcements on: %s for: %s -> %s\n", DISCOVERY_ADDR, serviceName, serviceURI));

		var pktBytes = String.format("%s%s%s", serviceName, DELIMITER, serviceURI).getBytes();
		var pkt = new DatagramPacket(pktBytes, pktBytes.length, DISCOVERY_ADDR);

		// start thread to send periodic announcements
		new Thread(() -> {
			try (var ds = new DatagramSocket()) {
				while (true) {
					try {
						ds.send(pkt);
						Sleep.ms(DISCOVERY_ANNOUNCE_PERIOD);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}).start();
	}


	@Override
	public URI[] knownUrisOf(String serviceName, int minEntries) {
		while(true) {
			long now = System.currentTimeMillis();
			// apagar o servidor se ele nao anunciar-se em 5 segundos
			storedAnnouncements.computeIfPresent(serviceName, (k, set) -> {
				set.removeIf(info -> (now - info.lastAnnouncement) > DISCOVERY_ANNOUNCE_PERIOD * 5);
				return set;
			});

			var res = storedAnnouncements.getOrDefault(serviceName, new ConcurrentSkipListSet<>());
			if( res.size() >= minEntries ) {
				// Transforma o TreeSet ordenado num Array de URIs limpinho!
				return res.stream().map(info -> info.uri).toArray(URI[]::new);
			} else {
				Sleep.ms(DISCOVERY_ANNOUNCE_PERIOD);
			}
		}
	}

	private void startListener() {
		Log.info(String.format("Starting discovery on multicast group: %s, port: %d\n", DISCOVERY_ADDR.getAddress(), DISCOVERY_ADDR.getPort()));

		new Thread(() -> {
			try (var ms = new MulticastSocket(DISCOVERY_ADDR.getPort())) {
				ms.joinGroup(DISCOVERY_ADDR, pickMulticastInterface(DISCOVERY_ADDR));
				for (;;) {
					try {
						var pkt = new DatagramPacket(new byte[MAX_DATAGRAM_SIZE], MAX_DATAGRAM_SIZE);
						ms.receive(pkt);
						var msg = new String(pkt.getData(), 0, pkt.getLength());
						Log.finest(String.format("Received: %s", msg));
						var parts = msg.split(DELIMITER);
						if (parts.length == 2) {
							var serviceName = parts[0];
							var uri = URI.create(parts[1]);
							storedAnnouncements.compute(serviceName, (k, v) -> {
								if (v == null) v = new ConcurrentSkipListSet<>();
								ServerInfo newInfo = new ServerInfo(uri, System.currentTimeMillis());
								v.remove(newInfo); // Remove o antigo (baseado no equals do URI)
								v.add(newInfo);    // Adiciona o novo com o timestamp atualizado
								return v;
							});}

					} catch (Exception x) {
						x.printStackTrace();
					}
				}
			} catch (Exception x) {
				x.printStackTrace();
			}
		}).start();
	}

	private NetworkInterface pickMulticastInterface(InetSocketAddress group) throws IOException {
		try(var tmp = new DatagramSocket()){
			tmp.connect(group);
			return NetworkInterface.getByInetAddress(tmp.getLocalAddress());
		}
	}

	class ServerInfo implements Comparable<ServerInfo> {
		final URI uri;
		final long lastAnnouncement;

		public ServerInfo(URI uri, long lastAnnouncement) {
			this.uri = uri;
			this.lastAnnouncement = lastAnnouncement;
		}

		@Override
		public int compareTo(ServerInfo other) {
			return this.uri.toString().compareTo(other.uri.toString());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null || getClass() != obj.getClass()) return false;
			ServerInfo that = (ServerInfo) obj;
			return uri.equals(that.uri);
		}

		@Override
		public int hashCode() {
			return uri.hashCode();
		}
	}

}