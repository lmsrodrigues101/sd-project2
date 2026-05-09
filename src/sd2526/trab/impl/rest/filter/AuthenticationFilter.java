package sd2526.trab.impl.rest.filter;

import java.io.IOException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AuthenticationFilter implements ContainerRequestFilter {

    public final static String HEADER_SECRET = "X-Shared-Secret";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String officialSecret = System.getProperty("secret");

        String receivedSecret = requestContext.getHeaderString(HEADER_SECRET);

        if (receivedSecret == null || !receivedSecret.equals(officialSecret)) {
            requestContext.abortWith(
                    Response.status(Response.Status.FORBIDDEN)
                            .entity("acess denied.")
                            .build()
            );
        }
    }
}