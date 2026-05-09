package sd2526.trab.impl.grpc.interceptors;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class GrpcAuthenticationInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> SECRET_KEY =
            Metadata.Key.of("x-shared-secret", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String officialSecret = System.getProperty("secret");
        String receivedSecret = headers.get(SECRET_KEY);

        if (receivedSecret == null || !receivedSecret.equals(officialSecret)) {
            call.close(Status.PERMISSION_DENIED.withDescription("invalid"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return next.startCall(call, headers);
    }
}