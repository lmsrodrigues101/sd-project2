package sd2526.trab.impl.rest.filter;

import java.io.IOException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AuthenticationFilter implements ContainerRequestFilter {

    private static final String SECRET_HEADER = "X-Shared-Secret";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        if (path.contains("remote") || path.contains("admin")) {
            String expectedSecret = System.getProperty("secret");
            String receivedSecret = requestContext.getHeaderString(SECRET_HEADER);

            if (expectedSecret != null && !expectedSecret.equals(receivedSecret)) {
                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                        .entity("Acess denied")
                        .build());
            }
        }
    }
}