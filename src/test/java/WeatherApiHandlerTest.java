import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import okhttp3.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import com.iteco.WeatherApiHandler;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class WeatherApiHandlerTest {

    private WeatherApiHandler handler;
    private OkHttpClient mockHttpClient;
    private SecretsManagerClient mockSecretsManagerClient;
    private Context mockContext;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(OkHttpClient.class);
        mockSecretsManagerClient = mock(SecretsManagerClient.class);

        mockContext = mock(Context.class);

        LambdaLogger mockLogger = mock(LambdaLogger.class);
        when(mockContext.getLogger()).thenReturn(mockLogger);
        handler = new WeatherApiHandler(mockHttpClient, mockSecretsManagerClient);

        GetSecretValueResponse secretResponse = GetSecretValueResponse.builder()
                .secretString("{\"WeatherApiKey\": \"test-key\"}")
                .build();
        when(mockSecretsManagerClient.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(secretResponse);
    }

    @Test
    void testValidRequest() throws IOException {
        String city = "Stockholm";
        String fakeWeatherResponse = "{\"temperature\": 10}";

        // Mocking HTTP response
        Response mockResponse = new Response.Builder()
                .request(new Request.Builder().url("https://test.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(fakeWeatherResponse, MediaType.get("application/json")))
                .build();
        Call mockCall = mock(Call.class);
        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);

        // Creating test request
        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent()
                .withBody("{\"city\": \"" + city + "\"}");

        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, mockContext);

        assertEquals(HttpStatusCode.OK, response.getStatusCode());
        assertEquals(fakeWeatherResponse, response.getBody());
    }

    @Test
    void testMissingCity() {
        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent()
                .withBody("{}");

        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, mockContext);

        assertEquals(HttpStatusCode.BAD_REQUEST, response.getStatusCode());
        assertEquals("Missing required parameter: city", response.getBody());
    }

    @Test
    void testInvalidJson() {
        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent()
                .withBody("Invalid JSON");

        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, mockContext);

        assertEquals(HttpStatusCode.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid JSON format", response.getBody());
    }
}