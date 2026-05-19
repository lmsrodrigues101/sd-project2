package sd2526.trab.impl.zoho;
import sd2526.trab.api.Message;
import sd2526.trab.api.java.Result;

import java.util.List;

public class ManualZohoTest {
    public static void main(String[] args) {
        System.out.println("A iniciar o teste de Pesquisa (searchInbox) no Zoho Mail...");

        try {
            JavaZohoMessages zoho = JavaZohoMessages.getInstance(false);

            System.out.println("\n--- PASSO 1: Criar mensagens de teste ---");

            Message msg1 = new Message("alice@ourorg1", "bob@ourorg2", "Reunião de SD", "Amanhã vamos falar sobre o projeto.");
            Message msg2 = new Message("alice@ourorg1", "bob@ourorg2", "Futebol", "Quem vai jogar logo à noite?");

            zoho.postMessage("pwd", msg1);
            zoho.postMessage("pwd", msg2);

            System.out.println("✅ Mensagens criadas. A aguardar 4 segundos pelo processamento do Zoho...");
            Thread.sleep(4000);

            System.out.println("\n--- PASSO 2: Pesquisar por 'projeto' ---");
            // A palavra "projeto" está no conteúdo da primeira mensagem. Vamos pesquisar em maiúsculas para testar o case-insensitive.
            Result<List<String>> searchRes1 = zoho.searchInbox("dummy", "dummy", "PROJETO");

            if (searchRes1.isOK()) {
                System.out.println("✅ SUCESSO! Resultados para 'PROJETO': " + searchRes1.value().size() + " encontrada(s).");
                System.out.println("IDs: " + searchRes1.value());
            } else {
                System.err.println("❌ FALHA na pesquisa 1: " + searchRes1.error());
            }

            System.out.println("\n--- PASSO 3: Pesquisar por 'Futebol' ---");
            // A palavra "Futebol" está no assunto da segunda mensagem.
            Result<List<String>> searchRes2 = zoho.searchInbox("dummy", "dummy", "futebol");

            if (searchRes2.isOK()) {
                System.out.println("✅ SUCESSO! Resultados para 'futebol': " + searchRes2.value().size() + " encontrada(s).");
                System.out.println("IDs: " + searchRes2.value());
            } else {
                System.err.println("❌ FALHA na pesquisa 2: " + searchRes2.error());
            }

            System.out.println("\n--- PASSO 4: Limpar mensagens de teste ---");
            for(String id : searchRes1.value()) zoho.removeInboxMessage("dummy", id, "dummy");
            for(String id : searchRes2.value()) zoho.removeInboxMessage("dummy", id, "dummy");
            System.out.println("✅ Limpeza concluída!");

        } catch (Exception e) {
            System.err.println("\n❌ Erro inesperado no teste:");
            e.printStackTrace();
        }

        System.exit(0);
    }
}
