package io.atomicbits.scraml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.atomicbits.schema.*;
import io.atomicbits.schema.Method;
import io.atomicbits.scraml.jdsl.*;
import io.atomicbits.scraml.jdsl.client.*;
import io.atomicbits.scraml.rest.user.UserResource;
import io.atomicbits.scraml.rest.user.userid.UseridResource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
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
        // defaultHeaders.put("Accept", "application/vnd-v1.0+json");
        ClientConfig config = new ClientConfig();
        config.setRequestCharset(Charset.forName("UTF-8"));
        client = new TestClient01(host, port, "http", null, config, defaultHeaders);
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        wireMockServer.stop();
        client.close();
    }

    @Test
    public void getRequestTestOk() {

        UserResource userResource = client.rest.user;

        // '[]' url-encoded gives: %5B%5D
        stubFor(get(urlEqualTo("/rest/user?firstName=John&organization%5B%5D=ESA&organization%5B%5D=NASA&age=51"))
                .withHeader("Accept", equalTo("application/vnd-v1.0+json"))
                .willReturn(aResponse()
                        .withBody(
                                "{\"address\": {\"streetAddress\": \"Mulholland Drive\", \"city\": \"LA\", \"state\": \"California\"}, " +
                                        "\"firstName\":\"John\", " +
                                        "\"lastName\": \"Doë\", " +
                                        "\"age\": 21, " +
                                        "\"id\": \"1\"" +
                                        "}")
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withStatus(200)));


//        JsonNodeFactory nodeFactory = new JsonNodeFactory(false);
//        ObjectNode node = nodeFactory.objectNode();
//        node.put("text", "foobar");

        User expectedUser = new User(new UserDefinitionsAddress("LA", "California", "Mulholland Drive"), 21L, "John", null, "1", "Doë", null);

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
    public void getRequestTestError() {

        UserResource userResource = client.rest.user;
        String errorMessage = "Oops";

        // '[]' url-encoded gives: %5B%5D
        stubFor(get(urlEqualTo("/rest/user?firstName=John&organization%5B%5D=ESA&organization%5B%5D=NASA&age=51"))
                .withHeader("Accept", equalTo("application/vnd-v1.0+json"))
                .willReturn(aResponse()
                        .withBody(errorMessage)
                        .withStatus(500)));

        JsonNodeFactory nodeFactory = new JsonNodeFactory(false);
        ObjectNode node = nodeFactory.objectNode();
        node.put("text", "foobar");

        CompletableFuture<Response<User>> eventualUser = userResource.get(51L, "John", null, Arrays.asList("ESA", "NASA"));
        try {
            Response<User> userResponse = eventualUser.get(10, TimeUnit.SECONDS);
            assertEquals(500, userResponse.getStatus());
            assertEquals(errorMessage, userResponse.getStringBody());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Did not expect exception: " + e.getMessage());
        }
    }


    @Test
    public void postRequestTest() {

        UseridResource userFoobarResource = client.rest.user.userid("foobar");

        stubFor(
                post(urlEqualTo("/rest/user/foobar"))
                        .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded; charset=UTF-8"))
                        .withHeader("Accept", equalTo("application/json")) // The default media type applies here!
                        .withRequestBody(equalTo("text=Hello-Foobar")) // "text=Hello%20Foobar"
                        .willReturn(
                                aResponse()
                                        .withBody("Post OK")
                                        .withStatus(200)
                        )
        );

        CompletableFuture<Response<String>> eventualPostResponse = userFoobarResource.post("Hello-Foobar", null);
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
                new Link(null, "http://foo.bar", Method.space),
                "1",
                "John",
                null);

        Link link = new Link(null, "http://foo.bar", Method.$8Trees);

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            stubFor(
                    put(urlEqualTo("/rest/user/foobar"))
                            .withHeader("Content-Type", equalTo("application/vnd-v1.0+json; charset=UTF-8"))
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
                        .contentApplicationVndV10Json
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
                        .withHeader("Accept", equalTo("*/*"))
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
    public void setHeaderRequestTest() {

        stubFor(
                delete(urlEqualTo("/rest/user/foobar"))
                        .withHeader("Accept", equalTo("foo/bar"))
                        .willReturn(
                                aResponse()
                                        .withBody("Delete OK")
                                        .withStatus(200)
                        )
        );

        UseridResource userFoobarResource = client.rest.setHeader("Accept", "*/*").user.setHeader("Accept", "foo/bar").userid("foobar");

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
                        .withHeader("Content-Type", equalTo("multipart/form-data; charset=UTF-8"))
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
                new Link(null, "http://foo.bar", Method.GET),
                "1",
                "Doe",
                null);

        ObjectMapper objectMapper = new ObjectMapper();

        List<User> users = Collections.singletonList(user);

        try {
            stubFor(
                    put(urlEqualTo("/rest/user/activate"))
                            .withHeader("Content-Type", equalTo("application/vnd-v1.0+json; charset=UTF-8"))
                            .withHeader("Accept", equalTo("application/vnd-v1.0+json"))
                            .withRequestBody(equalTo(objectMapper.writeValueAsString(users)))
                            .willReturn(
                                    aResponse()
                                            .withBody(objectMapper.writeValueAsString(users))
                                            .withStatus(202)
                            )
            );
        } catch (JsonProcessingException e) {
            fail("Did not expect exception: " + e.getMessage());
        }

        CompletableFuture<Response<List<User>>> listBodyResponse =
                client.rest.user.addHeader("Content-Type", "application/vnd-v1.0+json; charset=UTF-8").activate.put(users);

        try {
            List<User> receivedUsers = listBodyResponse.get(10, TimeUnit.SECONDS).getBody();
            assertEquals(1, receivedUsers.size());
            assertEquals("John", receivedUsers.get(0).getFirstName());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Did not expect exception: " + e.getMessage());
        }
    }


    @Test
    public void listPostRequestTest() {


        stubFor(
                post(urlEqualTo("/rest/animals"))
                        .withRequestBody(equalTo("[\"1\",\"2\"]"))
                        .willReturn(
                                aResponse()
                                        .withBody("[{\"_type\":\"Dog\",\"canBark\":true,\"gender\":\"female\",\"name\":\"Ziva\"}]")
                                        .withStatus(200)
                        )
        );


        List<String> ids = Arrays.asList("1", "2");
        CompletableFuture<Response<List<Animal>>> listBodyResponse =
                client.rest.animals.post(new ArrayList<>(ids));

        try {
            List<Animal> receivedUsers = listBodyResponse.get(10, TimeUnit.SECONDS).getBody();
            assertEquals(1, receivedUsers.size());
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
                new Fish("female"),
                new Cat("male", "Duster")
        );

        stubFor(
                put(urlEqualTo("/rest/animals"))
                        .withRequestBody(equalTo("[{\"_type\":\"Dog\",\"gender\":\"male\",\"canBark\":true,\"name\":\"Wiskey\"},{\"_type\":\"Fish\",\"gender\":\"female\"},{\"_type\":\"Cat\",\"gender\":\"male\",\"name\":\"Duster\"}]"))
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


    @Test
    public void binaryFileUploadTest() throws URISyntaxException, MalformedURLException {

        stubFor(
                post(urlEqualTo("/rest/animals/datafile/upload"))
                        .withRequestBody(equalTo(new String(binaryData())))
                        .willReturn(
                                aResponse()
                                        .withBody("{\"received\":\"OK\"}")
                                        .withStatus(200)
                        )
        );

        File file = new File(new URI(this.getClass().getResource("/io/atomicbits/scraml/binaryData.bin").toString()));
        CompletableFuture<Response<String>> eventualResponse = client.rest.animals.datafile.upload.post(file);

        try {
            Response<String> response = eventualResponse.get(10, TimeUnit.SECONDS);
            assertEquals(200, response.getStatus());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Did not expect exception: " + e.getMessage());
        }
    }


    @Test
    public void binaryStreamUploadTest() throws URISyntaxException, IOException {

        stubFor(
                post(urlEqualTo("/rest/animals/datafile/upload"))
                        .withRequestBody(equalTo(new String(binaryData())))
                        .willReturn(
                                aResponse()
                                        .withBody("{\"received\":\"OK\"}")
                                        .withStatus(200)
                        )
        );

        InputStream inputStream = this.getClass().getResourceAsStream("/io/atomicbits/scraml/binaryData.bin");
        CompletableFuture<Response<String>> eventualResponse = client.rest.animals.datafile.upload.post(inputStream);

        try {
            Response<String> response = eventualResponse.get(10, TimeUnit.SECONDS);
            assertEquals(200, response.getStatus());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Did not expect exception: " + e.getMessage());
        }
    }


    @Test
    public void binaryByteArrayUploadTest() throws URISyntaxException, IOException {

        stubFor(
                post(urlEqualTo("/rest/animals/datafile/upload"))
                        .withRequestBody(equalTo(new String(binaryData())))
                        .willReturn(
                                aResponse()
                                        .withBody("{\"received\":\"OK\"}")
                                        .withStatus(200)
                        )
        );

        InputStream inputStream = this.getClass().getResourceAsStream("/io/atomicbits/scraml/binaryData.bin");
        byte[] data = new byte[1024];
        inputStream.read(data, 0, 1024);
        inputStream.close();
        CompletableFuture<Response<String>> eventualResponse = client.rest.animals.datafile.upload.post(data);

        try {
            Response<String> response = eventualResponse.get(10, TimeUnit.SECONDS);
            assertEquals(200, response.getStatus());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Did not expect exception: " + e.getMessage());
        }
    }


    @Test
    public void binaryStringUploadTest() throws URISyntaxException, IOException {

        String text = "some test string";

        stubFor(
                post(urlEqualTo("/rest/animals/datafile/upload"))
                        .withRequestBody(equalTo(text))
                        .willReturn(
                                aResponse()
                                        .withBody("{\"received\":\"OK\"}")
                                        .withStatus(200)
                        )
        );

        CompletableFuture<Response<String>> eventualResponse = client.rest.animals.datafile.upload.post(text);

        try {
            Response<String> response = eventualResponse.get(10, TimeUnit.SECONDS);
            assertEquals(200, response.getStatus());
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            fail("Did not expect exception: " + e.getMessage());
        }
    }


    @Test
    public void binaryDownloadTest() {
        stubFor(
                get(urlEqualTo("/rest/animals/datafile/download"))
                        .willReturn(
                                aResponse()
                                        .withBody(binaryData())
                                        .withStatus(200)
                        )
        );

        CompletableFuture<Response<BinaryData>> eventualResponse = client.rest.animals.datafile.download.get();

        try {
            Response<BinaryData> response = eventualResponse.get(10, TimeUnit.SECONDS);
            assertEquals(200, response.getStatus());
            assertArrayEquals(binaryData(), response.getBody().asBytes());
        } catch (InterruptedException | ExecutionException | TimeoutException | IOException e) {
            fail("Did not expect exception: " + e.getMessage());
        }
    }


    private byte[] binaryData() {
        byte[] data = new byte[1024];
        for (int i = 0; i < 1024; i++) {
            data[i] = (byte) i;
        }
        return data;
    }

    private void createBinaryDataFile() throws FileNotFoundException, IOException {
        File file = new File("binaryData.bin");
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        fileOutputStream.write(binaryData());
        fileOutputStream.flush();
        fileOutputStream.close();
    }

}
