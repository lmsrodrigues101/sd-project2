package sd2526.trab.impl.grpc.servers;

import java.io.FileInputStream;
import java.net.InetAddress;
import java.security.KeyStore;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;

import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import sd2526.trab.api.java.Users;
import sd2526.trab.impl.discovery.Discovery;
import sd2526.trab.impl.utils.IP;


public class GrpcUsersServer {

	public static final int PORT = 13456;
	private static final String GRPC_CTX = "/grpc";
	private static final String SERVER_BASE_URI = "grpc://%s:%s%s";

	private static Logger Log = Logger.getLogger(GrpcUsersServer.class.getName());

	public static void main(String[] args) {
		try {
			String keyStoreFilename = System.getProperty("javax.net.ssl.keyStore");
			String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword");

			KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
			try (FileInputStream input = new FileInputStream(keyStoreFilename)) {
				keystore.load(input, keyStorePassword.toCharArray());
			}

			KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyManagerFactory.init(keystore, keyStorePassword.toCharArray());

			SslContext sslContext = GrpcSslContexts.configure(SslContextBuilder.forServer(keyManagerFactory)).build();

			String hostname = InetAddress.getLocalHost().getHostName();
			String serverURI = String.format(SERVER_BASE_URI, hostname, PORT, GRPC_CTX);

			Server server = NettyServerBuilder.forPort(PORT)
					.addService(new GrpcUsersController())
					.sslContext(sslContext)
					.build();

			String serviceName = String.format("%s@%s", Users.SERVICE_NAME, IP.domain());
			Discovery.getInstance().announce(serviceName, serverURI);

			Log.info(String.format("Users gRPC Server (TLS) ready @ %s\n", serverURI));

			server.start();
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				System.err.println("*** shutting down gRPC server since JVM is shutting down");
				server.shutdownNow();
				System.err.println("*** server shut down");
			}));

			server.awaitTermination();

		} catch (Exception e) {
			Log.severe("Erro no servidor gRPC: " + e.getMessage());
			e.printStackTrace();
		}
	}
}