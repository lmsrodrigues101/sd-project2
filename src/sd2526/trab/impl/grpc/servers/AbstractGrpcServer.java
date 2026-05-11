package sd2526.trab.impl.grpc.servers;

import java.io.FileInputStream;
import java.net.InetAddress;
import java.security.KeyStore;
import java.util.List;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import sd2526.trab.impl.discovery.Discovery;
import sd2526.trab.impl.grpc.interceptors.GrpcAuthenticationInterceptor;
import sd2526.trab.impl.java.servers.AbstractServer;
import sd2526.trab.impl.utils.IP;

public abstract class AbstractGrpcServer extends AbstractServer {
	private static final String SERVER_BASE_URI = "grpc://%s:%s%s";
	private static final String GRPC_CTX = "/grpc";

	protected Server server;
	protected final int port;

	protected AbstractGrpcServer(Logger log, String service, int port) {
		super(log, service, String.format(SERVER_BASE_URI, IP.hostname(), port, GRPC_CTX));
		this.port = port;
	}

	protected abstract List<GrpcController> controllers();

	@Override
	protected void start() {
		try {
			String keyStoreFilename = System.getProperty("javax.net.ssl.keyStore");
			String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword");

			KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
			try (FileInputStream input = new FileInputStream(keyStoreFilename)) {
				keystore.load(input, keyStorePassword.toCharArray());
			}

			KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyManagerFactory.init(keystore, keyStorePassword.toCharArray());

			SslContext sslContext = GrpcSslContexts.configure(
					SslContextBuilder.forServer(keyManagerFactory)
			).build();

			NettyServerBuilder builder = NettyServerBuilder.forPort(port)
					.sslContext(sslContext);

			for (BindableService s : controllers()) {
				builder.addService(s);
			}

			this.server = builder.build();
			this.server.start();
			Discovery.getInstance().announce(serviceName(), super.serverURI);

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				System.err.println("*** shutting down gRPC server since JVM is shutting down");
				if (server != null) server.shutdownNow();
			}));
			server.awaitTermination();

		} catch (Exception e) {
			Log.severe("Erro fatal ao iniciar servidor gRPC: " + e.getMessage());
			e.printStackTrace();
		}
	}
}