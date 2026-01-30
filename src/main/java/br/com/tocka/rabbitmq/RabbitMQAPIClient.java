package br.com.tocka.rabbitmq;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.*;

public class RabbitMQAPIClient {

    private String managementUrl;
    private String username;
    private String password;
    private OkHttpClient client;
    private Gson gson;

    public RabbitMQAPIClient(String host, int managementPort, String username, String password) {
        this.managementUrl = "http://" + host + ":" + managementPort + "/api";
        this.username = username;
        this.password = password;
        this.client = new OkHttpClient();
        this.gson = new Gson();
    }

    /**
     * Lista todos os usuários que estão vinculados a um determinado grupo (exchange)
     */
    public List<String> listUsersInGroup(String groupName) throws IOException {
        List<String> users = new ArrayList<>();
        
        // A API retorna as bindings de um exchange
        // Precisamos extrair os nomes das filas (que correspondem aos usuários)
        String url = managementUrl + "/exchanges/%2F/" + groupName + "/bindings/source";
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", Credentials.basic(username, password))
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                JsonArray bindingsArray = gson.fromJson(responseBody, JsonArray.class);
                
                Set<String> uniqueUsers = new HashSet<>();
                for (JsonElement element : bindingsArray) {
                    JsonObject binding = element.getAsJsonObject();
                    if (binding.has("destination")) {
                        String destination = binding.get("destination").getAsString();
                        // A destination é o nome da fila (usuário)
                        uniqueUsers.add(destination);
                    }
                }
                
                users = new ArrayList<>(uniqueUsers);
                Collections.sort(users);
            } else {
                throw new IOException("Erro na API do RabbitMQ: " + response.code() + " - " + response.message());
            }
        }
        
        return users;
    }

    /**
     * Lista todos os grupos (exchanges) dos quais um usuário faz parte
     */
    public List<String> listGroupsForUser(String username) throws IOException {
        List<String> groups = new ArrayList<>();
        
        // Obtém todas as bindings da fila do usuário
        String url = managementUrl + "/queues/%2F/" + username + "/bindings";
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", Credentials.basic(this.username, this.password))
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                JsonArray bindingsArray = gson.fromJson(responseBody, JsonArray.class);
                
                Set<String> uniqueGroups = new HashSet<>();
                for (JsonElement element : bindingsArray) {
                    JsonObject binding = element.getAsJsonObject();
                    if (binding.has("source")) {
                        String source = binding.get("source").getAsString();
                        // source é o nome do exchange (grupo)
                        if (!source.isEmpty() && !source.startsWith("amq.")) {
                            uniqueGroups.add(source);
                        }
                    }
                }
                
                groups = new ArrayList<>(uniqueGroups);
                Collections.sort(groups);
            } else {
                throw new IOException("Erro na API do RabbitMQ: " + response.code() + " - " + response.message());
            }
        }
        
        return groups;
    }
}
