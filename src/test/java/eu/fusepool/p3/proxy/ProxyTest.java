/*
 * Copyright 2014 reto.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fusepool.p3.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.jayway.restassured.RestAssured;
import java.net.ServerSocket;
import org.apache.http.HttpStatus;
import org.eclipse.jetty.server.Server;
import org.hamcrest.core.IsEqual;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author reto
 */
public class ProxyTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(backendPort);
    //TODO choose free port instead
    private static final int backendPort = findFreePort();
    private static int proxyPort = 8080;
    static Server server;

    @BeforeClass
    public static void startProxy() throws Exception {
        proxyPort = findFreePort();
        Assert.assertNotEquals("the assignment of different ports went wrong", backendPort, proxyPort);
        server = new Server(proxyPort);
        server.setHandler(new ProxyHandler("http://localhost:" + backendPort));
        server.start();
        RestAssured.baseURI = "http://localhost:" + proxyPort + "/";
    }

    @AfterClass
    public static void stopProxy() throws Exception {
        server.stop();
    }

    @Test
    public void nonTransformingContainer() throws Exception {

        final String turtleLdpc = "@prefix dcterms: <http://purl.org/dc/terms/>.\n"
                + "@prefix ldp: <http://www.w3.org/ns/ldp#>.\n"
                + "@prefix eldp: <http://vocab.fusepool.info/eldp#>.\n"
                + "\n"
                + "<http://example.org/container1/>\n"
                + "   a ldp:DirectContainer;\n"
                + "   dcterms:title \"An extracting LDP Container using simple-transformer\";\n"
                + "   ldp:membershipResource <http://example.org/container1/>;\n"
                + "   ldp:hasMemberRelation ldp:member;\n"
                + "   ldp:insertedContentRelation ldp:MemberSubject.";
        
        stubFor(get(urlEqualTo("/my/resource"))
                .withHeader("Accept", equalTo("text/turtle"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/turtle")
                        .withBody(turtleLdpc)));

        RestAssured.given().header("Accept", "text/turtle")
                .expect().statusCode(HttpStatus.SC_OK)
                .header("Content-Type", "text/turtle")
                .body(new IsEqual(turtleLdpc)).when()
                .get("/my/resource");

    }
    
    @Test
    public void transformingContainer() throws Exception {

        final String turtleLdpc = "@prefix dcterms: <http://purl.org/dc/terms/>.\n"
                + "@prefix ldp: <http://www.w3.org/ns/ldp#>.\n"
                + "@prefix eldp: <http://vocab.fusepool.info/eldp#>.\n"
                + "\n"
                + "<http://example.org/container1/>\n"
                + "   a ldp:DirectContainer;\n"
                + "   dcterms:title \"An extracting LDP Container using simple-transformer\";\n"
                + "   ldp:membershipResource <http://example.org/container1/>;\n"
                + "   ldp:hasMemberRelation ldp:member;\n"
                + "   ldp:insertedContentRelation ldp:MemberSubject;\n"
                + "   fp:transformer <http://localhost"+backendPort+"/simple-transformer>.";
        stubFor(get(urlEqualTo("/my/resource"))
                .withHeader("Accept", equalTo("text/turtle"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.SC_OK)
                        .withHeader("Content-Type", "text/turtle")
                        .withBody(turtleLdpc)));
        
        stubFor(post(urlEqualTo("/my/resource"))
                //.withHeader("Conetnt-Type", matching("text/plain*"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.SC_CREATED)));
        
        //A GET request returns the unmodified answer
        RestAssured.given().header("Accept", "text/turtle")
                .expect().statusCode(HttpStatus.SC_OK).header("Content-Type", "text/turtle").when()
                .get("/my/resource");
        //Certainly the backend got the request
        verify(getRequestedFor(urlMatching("/my/resource")));
        
        //Let's post some content
        RestAssured.given()
                .contentType("text/plain;charset=UTF-8")
                .content("hello")
                .expect().statusCode(HttpStatus.SC_CREATED).when()
                .post("/my/resource");
        //the backend got the post request aiaginst the LDPC
        verify(postRequestedFor(urlMatching("/my/resource")));
        //and after a while also against the Transformer
        Thread.sleep(100);
        //TODO make this test pass (Issue #3)
        //verify(getRequestedFor(urlMatching("/simple-transformer")));
        //Since the media-type is supported
        //verify(postRequestedFor(urlMatching("/simple-transformer")));
    }

    public static int findFreePort() {
        int port = 0;
        try (ServerSocket server = new ServerSocket(0);) {
            port = server.getLocalPort();
        } catch (Exception e) {
            throw new RuntimeException("unable to find a free port");
        }
        return port;
    }

}
