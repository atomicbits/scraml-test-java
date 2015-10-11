package io.atomicbits.scraml;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.atomicbits.schema.User;
import io.atomicbits.schema.UserDefinitionsAddress;
import io.atomicbits.scraml.dsl.java.Response;
import io.atomicbits.scraml.dsl.java.client.ClientConfig;
import io.atomicbits.scraml.rest.user.UserResource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Created by peter on 11/10/15.
 */
public class RamlModelGeneratorTest {

    private static int port = 8281;
    private static String host = "localhost";
    private static WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(port));


    @BeforeClass
    public static void setUpClass() {
        wireMockServer.start();
        WireMock.configureFor(host, port);
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        wireMockServer.stop();
    }

    @Test
    public void getRequestTest() {

        TestClient01 client = new TestClient01(host, port, "http", null, new ClientConfig(), null);

        UserResource userResource = client.rest.user;
        // UseridResource userFoobarResource = userResource.userid("foobar");

        stubFor(get(urlEqualTo("/rest/user?firstName=John&organization=ESA&organization=NASA&age=51"))
                .withHeader("Accept", equalTo("application/vnd-v1.0+json"))
                .willReturn(aResponse()
                        .withBody(
                                "{\"address\": {\"streetAddress\": \"Mulholland Drive\", \"city\": \"LA\", \"state\": \"California\"}, " +
                                        "\"firstName\":\"John\", " +
                                        "\"lastName\": \"Doe\", " +
                                        "\"age\": 21, " +
                                        "\"id\": \"1" +
                                        "\"}")
                        .withStatus(200)));

        User expectedUser = new User(21L, "Doe", "John", "1", null, new UserDefinitionsAddress("Mulholland Drive", "LA", "California"));

        CompletableFuture<Response<User>> eventualUser = userResource.get("John", null, 51L, Arrays.asList("ESA", "NASA"));
        try {
            User user = eventualUser.get(10, TimeUnit.SECONDS).getBody();
            assertEquals(expectedUser.getFirstName(), user.getFirstName());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            e.printStackTrace();
        }

    }

}
