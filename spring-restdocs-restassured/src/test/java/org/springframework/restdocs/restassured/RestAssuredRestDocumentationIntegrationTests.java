/*
 * Copyright 2014-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.restdocs.restassured;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.assertj.core.api.Condition;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.restdocs.JUnitRestDocumentation;
import org.springframework.restdocs.templates.TemplateFormat;
import org.springframework.restdocs.templates.TemplateFormats;
import org.springframework.restdocs.testfixtures.SnippetConditions;
import org.springframework.restdocs.testfixtures.SnippetConditions.CodeBlockCondition;
import org.springframework.restdocs.testfixtures.SnippetConditions.HttpRequestCondition;
import org.springframework.restdocs.testfixtures.SnippetConditions.HttpResponseCondition;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestMethod;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.linkWithRel;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.links;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.maskLinks;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.modifyHeaders;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.modifyUris;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.replacePattern;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.partWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.requestParameters;
import static org.springframework.restdocs.request.RequestDocumentation.requestParts;
import static org.springframework.restdocs.restassured.RestAssuredRestDocumentation.document;
import static org.springframework.restdocs.restassured.RestAssuredRestDocumentation.documentationConfiguration;

/**
 * Integration tests for using Spring REST Docs with REST Assured.
 *
 * @author Andy Wilkinson
 * @author Tomasz Kopczynski
 * @author Filip Hrisafov
 */
public class RestAssuredRestDocumentationIntegrationTests {

	@Rule
	public JUnitRestDocumentation restDocumentation = new JUnitRestDocumentation();

	@ClassRule
	public static TomcatServer tomcat = new TomcatServer();

	@Test
	public void defaultSnippetGeneration() {
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation))
				.filter(document("default")).get("/").then().statusCode(200);
		assertExpectedSnippetFilesExist(new File("build/generated-snippets/default"), "http-request.adoc",
				"http-response.adoc", "curl-request.adoc");
	}

	@Test
	public void curlSnippetWithContent() {
		String contentType = "text/plain; charset=UTF-8";
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation))
				.filter(document("curl-snippet-with-content")).accept("application/json").body("content")
				.contentType(contentType).post("/").then().statusCode(200);

		assertThat(new File("build/generated-snippets/curl-snippet-with-content/curl-request.adoc")).has(content(
				codeBlock(TemplateFormats.asciidoctor(), "bash").withContent(String.format("$ curl 'http://localhost:"
						+ tomcat.getPort() + "/' -i -X POST \\%n" + "    -H 'Accept: application/json' \\%n"
						+ "    -H 'Content-Type: " + contentType + "' \\%n" + "    -d 'content'"))));
	}

	@Test
	public void curlSnippetWithCookies() {
		String contentType = "text/plain; charset=UTF-8";
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation))
				.filter(document("curl-snippet-with-cookies")).accept("application/json").contentType(contentType)
				.cookie("cookieName", "cookieVal").get("/").then().statusCode(200);
		assertThat(new File("build/generated-snippets/curl-snippet-with-cookies/curl-request.adoc")).has(content(
				codeBlock(TemplateFormats.asciidoctor(), "bash").withContent(String.format("$ curl 'http://localhost:"
						+ tomcat.getPort() + "/' -i -X GET \\%n" + "    -H 'Accept: application/json' \\%n"
						+ "    -H 'Content-Type: " + contentType + "' \\%n" + "    --cookie 'cookieName=cookieVal'"))));
	}

	@Test
	public void curlSnippetWithEmptyParameterQueryString() {
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation))
				.filter(document("curl-snippet-with-empty-parameter-query-string")).accept("application/json")
				.param("a", "").get("/").then().statusCode(200);
		assertThat(
				new File("build/generated-snippets/curl-snippet-with-empty-parameter-query-string/curl-request.adoc"))
						.has(content(codeBlock(TemplateFormats.asciidoctor(), "bash")
								.withContent(String.format("$ curl 'http://localhost:" + tomcat.getPort()
										+ "/?a=' -i -X GET \\%n    -H 'Accept: application/json'"))));
	}

	@Test
	public void curlSnippetWithQueryStringOnPost() {
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation))
				.filter(document("curl-snippet-with-query-string")).accept("application/json").param("foo", "bar")
				.param("a", "alpha").post("/?foo=bar").then().statusCode(200);
		String contentType = "application/x-www-form-urlencoded; charset=ISO-8859-1";
		assertThat(new File("build/generated-snippets/curl-snippet-with-query-string/curl-request.adoc"))
				.has(content(codeBlock(TemplateFormats.asciidoctor(), "bash")
						.withContent(String.format("$ curl " + "'http://localhost:" + tomcat.getPort()
								+ "/?foo=bar' -i -X POST \\%n" + "    -H 'Accept: application/json' \\%n"
								+ "    -H 'Content-Type: " + contentType + "' \\%n" + "    -d 'a=alpha'"))));
	}

	@Test
	public void linksSnippet() {
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation))
				.filter(document("links", links(linkWithRel("rel").description("The description"))))
				.accept("application/json").get("/").then().statusCode(200);
		assertExpectedSnippetFilesExist(new File("build/generated-snippets/links"), "http-request.adoc",
				"http-response.adoc", "curl-request.adoc", "links.adoc");
	}

	@Test
	public void pathParametersSnippet() {
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation))
				.filter(document("path-parameters",
						pathParameters(parameterWithName("foo").description("The description"))))
				.accept("application/json").get("/{foo}", "").then().statusCode(200);
		assertExpectedSnippetFilesExist(new File("build/generated-snippets/path-parameters"), "http-request.adoc",
				"http-response.adoc", "curl-request.adoc", "path-parameters.adoc");
	}

	@Test
	public void requestParametersSnippet() {
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation))
				.filter(document("request-parameters",
						requestParameters(parameterWithName("foo").description("The description"))))
				.accept("application/json").param("foo", "bar").get("/").then().statusCode(200);
		assertExpectedSnippetFilesExist(new File("build/generated-snippets/request-parameters"), "http-request.adoc",
				"http-response.adoc", "curl-request.adoc", "request-parameters.adoc");
	}

	@Test
	public void requestFieldsSnippet() {
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation))
				.filter(document("request-fields", requestFields(fieldWithPath("a").description("The description"))))
				.accept("application/json").body("{\"a\":\"alpha\"}").post("/").then().statusCode(200);
		assertExpectedSnippetFilesExist(new File("build/generated-snippets/request-fields"), "http-request.adoc",
				"http-response.adoc", "curl-request.adoc", "request-fields.adoc");
	}

	@Test
	public void requestPartsSnippet() {
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation))
				.filter(document("request-parts", requestParts(partWithName("a").description("The description"))))
				.multiPart("a", "foo").post("/upload").then().statusCode(200);
		assertExpectedSnippetFilesExist(new File("build/generated-snippets/request-parts"), "http-request.adoc",
				"http-response.adoc", "curl-request.adoc", "request-parts.adoc");
	}

	@Test
	public void responseFieldsSnippet() {
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation))
				.filter(document("response-fields",
						responseFields(fieldWithPath("a").description("The description"),
								subsectionWithPath("links").description("Links to other resources"))))
				.accept("application/json").get("/").then().statusCode(200);
		assertExpectedSnippetFilesExist(new File("build/generated-snippets/response-fields"), "http-request.adoc",
				"http-response.adoc", "curl-request.adoc", "response-fields.adoc");
	}

	@Test
	public void parameterizedOutputDirectory() {
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation))
				.filter(document("{method-name}")).get("/").then().statusCode(200);
		assertExpectedSnippetFilesExist(new File("build/generated-snippets/parameterized-output-directory"),
				"http-request.adoc", "http-response.adoc", "curl-request.adoc");
	}

	@Test
	public void multiStep() {
		RequestSpecification spec = new RequestSpecBuilder().setPort(tomcat.getPort())
				.addFilter(documentationConfiguration(this.restDocumentation))
				.addFilter(document("{method-name}-{step}")).build();
		given(spec).get("/").then().statusCode(200);
		assertExpectedSnippetFilesExist(new File("build/generated-snippets/multi-step-1/"), "http-request.adoc",
				"http-response.adoc", "curl-request.adoc");
		given(spec).get("/").then().statusCode(200);
		assertExpectedSnippetFilesExist(new File("build/generated-snippets/multi-step-2/"), "http-request.adoc",
				"http-response.adoc", "curl-request.adoc");
		given(spec).get("/").then().statusCode(200);
		assertExpectedSnippetFilesExist(new File("build/generated-snippets/multi-step-3/"), "http-request.adoc",
				"http-response.adoc", "curl-request.adoc");
	}

	@Test
	public void additionalSnippets() {
		RestDocumentationFilter documentation = document("{method-name}-{step}");
		RequestSpecification spec = new RequestSpecBuilder().setPort(tomcat.getPort())
				.addFilter(documentationConfiguration(this.restDocumentation)).addFilter(documentation).build();
		given(spec).filter(documentation.document(
				responseHeaders(headerWithName("a").description("one"), headerWithName("Foo").description("two"))))
				.get("/").then().statusCode(200);
		assertExpectedSnippetFilesExist(new File("build/generated-snippets/additional-snippets-1/"),
				"http-request.adoc", "http-response.adoc", "curl-request.adoc", "response-headers.adoc");
	}

	@Test
	public void responseWithCookie() {
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation))
				.filter(document("set-cookie",
						preprocessResponse(modifyHeaders().remove(HttpHeaders.DATE).remove(HttpHeaders.CONTENT_TYPE))))
				.get("/set-cookie").then().statusCode(200);
		assertExpectedSnippetFilesExist(new File("build/generated-snippets/set-cookie"), "http-request.adoc",
				"http-response.adoc", "curl-request.adoc");
		assertThat(new File("build/generated-snippets/set-cookie/http-response.adoc"))
				.has(content(httpResponse(TemplateFormats.asciidoctor(), HttpStatus.OK)
						.header(HttpHeaders.SET_COOKIE, "name=value; Domain=localhost; HttpOnly")
						.header("Keep-Alive", "timeout=60").header("Connection", "keep-alive")));
	}

	@Test
	public void preprocessedRequest() {
		Pattern pattern = Pattern.compile("(\"alpha\")");
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation)).header("a", "alpha")
				.header("b", "bravo").contentType("application/json").accept("application/json")
				.body("{\"a\":\"alpha\"}").filter(document("original-request"))
				.filter(document("preprocessed-request",
						preprocessRequest(prettyPrint(), replacePattern(pattern, "\"<<beta>>\""),
								modifyUris().removePort(),
								modifyHeaders().remove("a").remove(HttpHeaders.CONTENT_LENGTH))))
				.get("/").then().statusCode(200);
		assertThat(new File("build/generated-snippets/original-request/http-request.adoc"))
				.has(content(httpRequest(TemplateFormats.asciidoctor(), RequestMethod.GET, "/").header("a", "alpha")
						.header("b", "bravo").header("Accept", MediaType.APPLICATION_JSON_VALUE)
						.header("Content-Type", "application/json").header("Host", "localhost:" + tomcat.getPort())
						.header("Content-Length", "13").content("{\"a\":\"alpha\"}")));
		String prettyPrinted = String.format("{%n  \"a\" : \"<<beta>>\"%n}");
		assertThat(new File("build/generated-snippets/preprocessed-request/http-request.adoc"))
				.has(content(httpRequest(TemplateFormats.asciidoctor(), RequestMethod.GET, "/").header("b", "bravo")
						.header("Accept", MediaType.APPLICATION_JSON_VALUE).header("Content-Type", "application/json")
						.header("Host", "localhost").content(prettyPrinted)));
	}

	@Test
	public void defaultPreprocessedRequest() {
		Pattern pattern = Pattern.compile("(\"alpha\")");
		given().port(tomcat.getPort())
				.filter(documentationConfiguration(this.restDocumentation).operationPreprocessors().withRequestDefaults(
						prettyPrint(), replacePattern(pattern, "\"<<beta>>\""), modifyUris().removePort(),
						modifyHeaders().remove("a").remove(HttpHeaders.CONTENT_LENGTH)))
				.header("a", "alpha").header("b", "bravo").contentType("application/json").accept("application/json")
				.body("{\"a\":\"alpha\"}").filter(document("default-preprocessed-request")).get("/").then()
				.statusCode(200);
		String prettyPrinted = String.format("{%n  \"a\" : \"<<beta>>\"%n}");
		assertThat(new File("build/generated-snippets/default-preprocessed-request/http-request.adoc"))
				.has(content(httpRequest(TemplateFormats.asciidoctor(), RequestMethod.GET, "/").header("b", "bravo")
						.header("Accept", MediaType.APPLICATION_JSON_VALUE).header("Content-Type", "application/json")
						.header("Host", "localhost").content(prettyPrinted)));
	}

	@Test
	public void preprocessedResponse() {
		Pattern pattern = Pattern.compile("(\"alpha\")");
		given().port(tomcat.getPort()).filter(documentationConfiguration(this.restDocumentation))
				.filter(document("original-response"))
				.filter(document("preprocessed-response",
						preprocessResponse(prettyPrint(), maskLinks(),
								modifyHeaders().remove("a").remove("Transfer-Encoding").remove("Date").remove("Server"),
								replacePattern(pattern, "\"<<beta>>\""),
								modifyUris().scheme("https").host("api.example.com").removePort())))
				.get("/").then().statusCode(200);
		String prettyPrinted = String.format("{%n  \"a\" : \"<<beta>>\",%n  \"links\" : "
				+ "[ {%n    \"rel\" : \"rel\",%n    \"href\" : \"...\"%n  } ]%n}");
		assertThat(new File("build/generated-snippets/preprocessed-response/http-response.adoc"))
				.has(content(httpResponse(TemplateFormats.asciidoctor(), HttpStatus.OK)
						.header("Foo", "https://api.example.com/foo/bar")
						.header("Content-Type", "application/json;charset=UTF-8").header("Keep-Alive", "timeout=60")
						.header("Connection", "keep-alive")
						.header(HttpHeaders.CONTENT_LENGTH, prettyPrinted.getBytes().length).content(prettyPrinted)));
	}

	@Test
	public void defaultPreprocessedResponse() {
		Pattern pattern = Pattern.compile("(\"alpha\")");
		given().port(tomcat.getPort())
				.filter(documentationConfiguration(this.restDocumentation).operationPreprocessors()
						.withResponseDefaults(prettyPrint(), maskLinks(),
								modifyHeaders().remove("a").remove("Transfer-Encoding").remove("Date").remove("Server"),
								replacePattern(pattern, "\"<<beta>>\""),
								modifyUris().scheme("https").host("api.example.com").removePort()))
				.filter(document("default-preprocessed-response")).get("/").then().statusCode(200);
		String prettyPrinted = String.format("{%n  \"a\" : \"<<beta>>\",%n  \"links\" : "
				+ "[ {%n    \"rel\" : \"rel\",%n    \"href\" : \"...\"%n  } ]%n}");
		assertThat(new File("build/generated-snippets/default-preprocessed-response/http-response.adoc"))
				.has(content(httpResponse(TemplateFormats.asciidoctor(), HttpStatus.OK)
						.header("Foo", "https://api.example.com/foo/bar")
						.header("Content-Type", "application/json;charset=UTF-8").header("Keep-Alive", "timeout=60")
						.header("Connection", "keep-alive")
						.header(HttpHeaders.CONTENT_LENGTH, prettyPrinted.getBytes().length).content(prettyPrinted)));
	}

	@Test
	public void customSnippetTemplate() throws MalformedURLException {
		ClassLoader classLoader = new URLClassLoader(
				new URL[] { new File("src/test/resources/custom-snippet-templates").toURI().toURL() },
				getClass().getClassLoader());
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(classLoader);
		try {
			given().port(tomcat.getPort()).accept("application/json")
					.filter(documentationConfiguration(this.restDocumentation))
					.filter(document("custom-snippet-template")).get("/").then().statusCode(200);
		}
		finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
		assertThat(new File("build/generated-snippets/custom-snippet-template/curl-request.adoc"))
				.hasContent("Custom curl request");
	}

	@Test
	public void exceptionShouldBeThrownWhenCallDocumentRequestSpecificationNotConfigured() {
		assertThatThrownBy(() -> given().port(tomcat.getPort()).filter(document("default")).get("/"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("REST Docs configuration not found. Did you forget to add a "
						+ "RestAssuredRestDocumentationConfigurer as a filter when building the RequestSpecification?");
	}

	@Test
	public void exceptionShouldBeThrownWhenCallDocumentSnippetsRequestSpecificationNotConfigured() {
		RestDocumentationFilter documentation = document("{method-name}-{step}");
		assertThatThrownBy(() -> given().port(tomcat.getPort())
				.filter(documentation.document(responseHeaders(headerWithName("a").description("one")))).get("/"))
						.isInstanceOf(IllegalStateException.class)
						.hasMessage("REST Docs configuration not found. Did you forget to add a "
								+ "RestAssuredRestDocumentationConfigurer as a filter when building the "
								+ "RequestSpecification?");
	}

	private void assertExpectedSnippetFilesExist(File directory, String... snippets) {
		for (String snippet : snippets) {
			assertThat(new File(directory, snippet)).isFile();
		}
	}

	private Condition<File> content(final Condition<String> delegate) {
		return new Condition<>() {

			@Override
			public boolean matches(File value) {
				try {
					return delegate.matches(FileCopyUtils
							.copyToString(new InputStreamReader(new FileInputStream(value), StandardCharsets.UTF_8)));
				}
				catch (IOException ex) {
					fail("Failed to read '" + value + "'", ex);
					return false;
				}
			}

		};
	}

	private CodeBlockCondition<?> codeBlock(TemplateFormat format, String language) {
		return SnippetConditions.codeBlock(format, language);
	}

	private HttpRequestCondition httpRequest(TemplateFormat format, RequestMethod requestMethod, String uri) {
		return SnippetConditions.httpRequest(format, requestMethod, uri);
	}

	private HttpResponseCondition httpResponse(TemplateFormat format, HttpStatus status) {
		return SnippetConditions.httpResponse(format, status);
	}

}
