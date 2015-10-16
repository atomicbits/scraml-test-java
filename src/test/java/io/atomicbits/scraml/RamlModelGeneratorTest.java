package io.atomicbits.scraml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.atomicbits.schema.*;
import io.atomicbits.scraml.dsl.java.BodyPart;
import io.atomicbits.scraml.dsl.java.Response;
import io.atomicbits.scraml.dsl.java.StringPart;
import io.atomicbits.scraml.dsl.java.client.ClientConfig;
import io.atomicbits.scraml.rest.user.UserResource;
import io.atomicbits.scraml.rest.user.userid.UseridResource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Created by peter on 11/10/15.
 */
public class RamlModelGeneratorTest {

    private static int port = 8281;
    private static String host = "localhost";
    private static WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(port));
    private static TestClient01 client;


    @BeforeClass
    public static void setUpClass() {
        wireMockServer.start();
        WireMock.configureFor(host, port);
        Map<String, String> defaultHeaders = new HashMap<>();
        defaultHeaders.put("Accept", "application/vnd-v1.0+json");
        client = new TestClient01(host, port, "http", null, new ClientConfig(), defaultHeaders);
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        wireMockServer.stop();
        client.close();
    }

    @Test
    public void getRequestTest() {

        UserResource userResource = client.rest.user;

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

        User expectedUser = new User(new UserDefinitionsAddress("LA", "California", "Mulholland Drive"), 21L, "John", null, "1", "Doe");

        CompletableFuture<Response<User>> eventualUser = userResource.get(51L, "John", null, Arrays.asList("ESA", "NASA"));
        try {
            User user = eventualUser.get(10, TimeUnit.SECONDS).getBody();
            assertEquals(expectedUser.getFirstName(), user.getFirstName());
            assertEquals(expectedUser.getLastName(), user.getLastName());
            assertEquals(expectedUser.getAge(), user.getAge());
            assertEquals(expectedUser.getAddress().getCity(), user.getAddress().getCity());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Did not expect exception: " + e.getMessage());
        }

    }


    @Test
    public void postRequestTest() {

        UseridResource userFoobarResource = client.rest.user.userid("foobar");

        stubFor(
                post(urlEqualTo("/rest/user/foobar"))
                        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
                        .withHeader("Accept", equalTo("application/vnd-v1.0+json"))
                        .withRequestBody(equalTo("text=Hello%20Foobar"))
                        .willReturn(
                                aResponse()
                                        .withBody("Post OK")
                                        .withStatus(200)
                        )
        );

        CompletableFuture<Response<String>> eventualPostResponse = userFoobarResource.post("Hello Foobar", null);
        try {
            String responseText = eventualPostResponse.get(10, TimeUnit.SECONDS).getBody();
            assertEquals("Post OK", responseText);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Did not expect exception: " + e.getMessage());
        }

    }


    @Test
    public void putRequestTest() {

        User user = new User(
                new UserDefinitionsAddress("LA", "California", "Mulholland Drive"),
                21L,
                "Doe",
                new Link(null, "http://foo.bar", LinkMethod.GET),
                "1",
                "John");

        Link link = new Link(null, "http://foo.bar", LinkMethod.GET);

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            stubFor(
                    put(urlEqualTo("/rest/user/foobar"))
                            .withHeader("Content-Type", equalTo("application/vnd-v1.0+json"))
                            .withHeader("Accept", equalTo("application/vnd-v1.0+json"))
                            .withRequestBody(equalTo(objectMapper.writeValueAsString(user)))
                            .willReturn(
                                    aResponse()
                                            .withBody(objectMapper.writeValueAsString(link))
                                            .withStatus(200)
                            )
            );
        } catch (JsonProcessingException e) {
            fail("Did not expect exception: " + e.getMessage());
        }


        UseridResource userFoobarResource = client.rest.user.userid("foobar");

        CompletableFuture<Response<Link>> eventualPutResponse =
                userFoobarResource
                        ._contentApplicationVndV10Json
                        .put(user);

        try {
            Link receivedLink = eventualPutResponse.get(10, TimeUnit.SECONDS).getBody();
            assertEquals(link.getAccept(), receivedLink.getAccept());
            assertEquals(link.getHref(), receivedLink.getHref());
            assertEquals(link.getMethod(), receivedLink.getMethod());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Did not expect exception: " + e.getMessage());
        }

    }


    @Test
    public void deleteRequestTest() {

        stubFor(
                delete(urlEqualTo("/rest/user/foobar"))
                        .withHeader("Accept", equalTo("application/vnd-v1.0+json"))
                        .willReturn(
                                aResponse()
                                        .withBody("Delete OK")
                                        .withStatus(200)
                        )
        );

        UseridResource userFoobarResource = client.rest.user.userid("foobar");

        CompletableFuture<Response<String>> eventualDeleteResponse = userFoobarResource.delete();

        try {
            String deleteResponseText = eventualDeleteResponse.get(10, TimeUnit.SECONDS).getBody();
            assertEquals("Delete OK", deleteResponseText);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Did not expect exception: " + e.getMessage());
        }
    }


    @Test
    public void multipartFormRequestTest() {

        stubFor(
                post(urlEqualTo("/rest/user/upload"))
                        .withHeader("Content-Type", equalTo("multipart/form-data"))
                        .willReturn(
                                aResponse()
                                        .withBody("Post OK")
                                        .withStatus(200)
                        )
        );

        List<BodyPart> bodyParts = Collections.singletonList(new StringPart("test", "string part value"));

        CompletableFuture<Response<String>> multipartFormPostResponse = client.rest.user.upload.post(bodyParts);

        // ToDo...
    }


    @Test
    public void listRequestTest() {

        User user = new User(
                new UserDefinitionsAddress("LA", "California", "Mulholland Drive"),
                21L,
                "John",
                new Link(null, "http://foo.bar", LinkMethod.GET),
                "1",
                "Doe");

        ObjectMapper objectMapper = new ObjectMapper();

        List<User> users = Collections.singletonList(user);

        try {
            stubFor(
                    put(urlEqualTo("/rest/user/activate"))
                            .withHeader("Content-Type", equalTo("application/vnd-v1.0+json"))
                            .withHeader("Accept", equalTo("application/vnd-v1.0+json"))
                            .withRequestBody(equalTo(objectMapper.writeValueAsString(users)))
                            .willReturn(
                                    aResponse()
                                            .withBody(objectMapper.writeValueAsString(users))
                                            .withStatus(200)
                            )
            );
        } catch (JsonProcessingException e) {
            fail("Did not expect exception: " + e.getMessage());
        }

        CompletableFuture<Response<List<User>>> listBodyResponse =
                client.rest.user.activate.addHeader("Content-Type", "application/vnd-v1.0+json").put(users);

        try {
            List<User> receivedUsers = listBodyResponse.get(10, TimeUnit.SECONDS).getBody();
            assertEquals(1, receivedUsers.size());
            assertEquals("John", receivedUsers.get(0).getFirstName());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Did not expect exception: " + e.getMessage());
        }
    }


    @Test
    public void classListAndGenericsRequestTest() {

        stubFor(
                get(urlEqualTo("/rest/animals"))
                        .willReturn(
                                aResponse()
                                        .withBody("[{\"_type\":\"Dog\",\"canBark\":true,\"gender\":\"female\",\"name\":\"Ziva\"}]")
                                        .withStatus(200)
                        )
        );

        CompletableFuture<Response<List<Animal>>> eventualAnimal = client.rest.animals.get();

        try {
            List<Animal> animals = eventualAnimal.get(10, TimeUnit.SECONDS).getBody();
            assertEquals(1, animals.size());
            Animal animal = animals.get(0);
            assertTrue(animal instanceof Dog);
            Dog theDog = (Dog) animal;
            assertEquals("Ziva", theDog.getName());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Did not expect exception: " + e.getMessage());
        }
    }

    @Test
    public void putListRequestTest() {

        List<Animal> animals = Arrays.asList(
                new Dog(true, "male", "Wiskey"),
                new Fish("Wanda"),
                new Cat("male", "Duster")
        );

        stubFor(
                put(urlEqualTo("/rest/animals"))
                        .withRequestBody(equalTo("[{\"_type\":\"Dog\",\"canBark\":true,\"gender\":\"male\",\"name\":\"Wiskey\"},{\"_type\":\"Fish\",\"gender\":\"Wanda\"},{\"_type\":\"Cat\",\"gender\":\"male\",\"name\":\"Duster\"}]"))
                        .willReturn(
                                aResponse()
                                        .withBody("[{\"_type\":\"Cat\",\"gender\":\"female\",\"name\":\"Orelia\"}]")
                                        .withStatus(200)
                        )
        );

        CompletableFuture<Response<List<Animal>>> eventualAnimals = client.rest.animals.put(animals);

        try {
            List<Animal> receivedAnimals = eventualAnimals.get(10, TimeUnit.SECONDS).getBody();
            assertEquals(1, receivedAnimals.size());
            Animal animal = receivedAnimals.get(0);
            assertTrue(animal instanceof Cat);
            Cat orelia = (Cat) animal;
            assertEquals("Orelia", orelia.getName());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Did not expect exception: " + e.getMessage());
        }
    }

}
