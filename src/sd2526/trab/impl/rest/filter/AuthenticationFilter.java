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
        String path = requestContext.getUriInfo().getPath();

        // Apenas intercetar se o pedido for estritamente para as rotas de Admin
        if (path.endsWith("admin") || path.contains("admin/")) {

            String officialSecret = System.getProperty("secret");
            String receivedSecret = requestContext.getHeaderString(HEADER_SECRET);

            // Bloqueia se o servidor tem um segredo definido e o cliente não o enviou corretamente
            if (officialSecret != null && !officialSecret.equals(receivedSecret)) {
                requestContext.abortWith(
                        Response.status(Response.Status.FORBIDDEN)
                                .entity("Acesso Negado: Falha na autenticação entre servidores.")
                                .build()
                );
            }
        }
    }
}