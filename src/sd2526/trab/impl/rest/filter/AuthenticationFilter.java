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

        // 1. SÓ APLICAMOS A SEGURANÇA ÀS ROTAS ENTRE SERVIDORES (Geralmente contêm "remote" ou "admin")
        if (path.contains("remote") || path.contains("admin")) {

            // O segredo que o Tester nos passou quando arrancou o servidor
            String expectedSecret = System.getProperty("secret");
            String receivedSecret = requestContext.getHeaderString(SECRET_HEADER);

            // Se o segredo não existir ou estiver errado, bloqueamos o acesso com 403 Forbidden!
            if (expectedSecret != null && !expectedSecret.equals(receivedSecret)) {
                requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                        .entity("Acesso negado: Autenticação de Servidor Falhou.")
                        .build());
            }
        }
    }
}