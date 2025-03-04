package com.iteco;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.io.IOException;

public class WeatherApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final OkHttpClient client;
    private final Gson gson;
    private final SecretsManagerClient secretsClient;

    public WeatherApiHandler() {
        client = new OkHttpClient();
        gson = new Gson();
        secretsClient = SecretsManagerClient.builder().region(Region.EU_NORTH_1).build();
    }

    public WeatherApiHandler(OkHttpClient mockHttpClient, SecretsManagerClient mockSecretsManagerClient) {
        secretsClient = mockSecretsManagerClient;
        client = mockHttpClient;
        gson = new Gson();
    }

    private String getSecret() {

        String secretName = "WeatherApiKey";

        GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();

        GetSecretValueResponse getSecretValueResponse = secretsClient.getSecretValue(getSecretValueRequest);
        String secretString = getSecretValueResponse.secretString();

        JsonObject json = gson.fromJson(secretString, JsonObject.class);
        return json.get(secretName).getAsString();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {

        try {

            JsonObject requestBody = gson.fromJson(requestEvent.getBody(), JsonObject.class);

            if (requestBody == null || !requestBody.has("city")) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("Missing required parameter: city");
            }

            String city = requestBody.get("city").getAsString();
            String BASE_URL = "https://weather.visualcrossing.com/VisualCrossingWebServices/rest/services/timeline/";
            String apiURL = BASE_URL + city + "/today?unitGroup=metric&include=days&key=" + getSecret() + "&contentType=json";

            Request request = new Request.Builder().url(apiURL).build();

            try (Response response = client.newCall(request).execute()) {

                if (!response.isSuccessful() || response.body() == null) {
                    return new APIGatewayProxyResponseEvent()
                            .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                            .withBody("Weather Api Error " + response.code());
                }

                String responseBody = response.body().string();
                context.getLogger().log("Response body: " + responseBody);
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(HttpStatusCode.OK)
                        .withBody(responseBody);
            }
        } catch (JsonSyntaxException e) {
            context.getLogger().log("Invalid JSON format: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.BAD_REQUEST)
                    .withBody("Invalid JSON format");
        } catch (IOException e) {
            context.getLogger().log("Network Error: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.BAD_REQUEST)
                    .withBody("Network error");
        } catch (Exception e) {
            context.getLogger().log("Unexpected error: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("An unexpected error occurred");
        }
    }
}