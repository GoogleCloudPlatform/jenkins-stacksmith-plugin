/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.jenkins.plugins.stacksmith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Tests for {@link BitnamiApiManager}.
 */
@RunWith(MockitoJUnitRunner.class)
public class BitnamiApiManagerTest {
  private static final int LARGE_JSON_ITEM_SIZE = 10000;
  private static final String STACK_ID_1 = "stackid1";
  private static final String STACK_URL_1 = "https://example.com/stackurl1";

  private static final String GOOD_DEPENDENCIES_JSON_STRING = "{" +
      "\"total_entries\": 1," +
      "\"total_pages\": 1," +
      "\"items\": [" +
        "\"java\"" +
      "]}";

  private static final String GOOD_FLAVORS_JSON_STRING = "{" +
      "\"total_entries\": 1," +
      "\"total_pages\": 1," +
      "\"items\": [{" +
        "\"id\": \"tomcat-server\"" +
      "}]}";


  private static final String GOOD_COMPONENTS_JSON_STRING = "{" +
      "\"items\": [" +
      "{" +
        "\"id\": \"tomcat\", " +
        "\"name\": \"Tomcat\", " +
        "\"category\": \"service\", " +
        "\"versions\": [" +
        "{" +
          "\"version\": \"2.0\", " +
          "\"revision\": 0, " +
          "\"branch\": \"stable\", " +
          "\"checksum\": \"0xdeadbeef\", " +
          "\"published_at\": \"2015-11-20\"" +
        "}, " +
        "{" +
          "\"version\": \"1.0\", " +
          "\"revision\": 0, " +
          "\"branch\": \"stable\", " +
          "\"checksum\": \"0xbeefbeef\", " +
          "\"published_at\": \"2015-11-10\"" +
        "}" +
      "]}, " +
      "{" +
        "\"id\": \"java\", " +
        "\"name\": \"Java\", " +
        "\"category\": \"runtime\", " +
        "\"versions\": [" +
        "{" +
          "\"version\": \"3.0\", " +
          "\"revision\": 0, " +
          "\"branch\": \"dev\", " +
          "\"checksum\": \"0xdeadbeef\", " +
          "\"published_at\": \"2015-11-20\"" +
        "}" +
      "]}]}";
  private static final String BAD_FORMAT_JSON_STRING = "{\"stuff\" : "
      + "\"This is valid JSON, but not in our format\"}";
  private static final String BAD_JSON_STRING = "This is not valid JSON{{{";
  private static final BranchedVersion STABLE_1_0 = new BranchedVersion("1.0",
      "stable");
  private static final BranchedVersion STABLE_2_0 = new BranchedVersion("2.0",
      "stable");
  private static final BranchedVersion DEV_3_0 = new BranchedVersion("3.0",
      "dev");
  private static final VersionedEntity COMPONENT_TOMCAT
      = VersionedEntity.builder()
      .setId("tomcat")
      .setName("Tomcat")
      .setCategory(VersionedEntityType.COMPONENT)
      .addVersion(STABLE_1_0)
      .addVersion(STABLE_2_0)
      .build();
  private static final VersionedEntity COMPONENT_JAVA
      = VersionedEntity.builder()
      .setId("java")
      .setName("Java")
      .setCategory(VersionedEntityType.COMPONENT)
      .addVersion(DEV_3_0)
      .build();
  private static final String TOMCAT_FLAVOR = "tomcat-server";

  @Mock CloseableHttpClient mockHttpClient;
  @Mock CloseableHttpResponse mockResponse;
  @Captor ArgumentCaptor<HttpUriRequest> requestCaptor;
  @Captor ArgumentCaptor<HttpPost> postCaptor;
  private BitnamiApiManager underTest;

  @Before
  public void setUp() {
    underTest = new BitnamiApiManager(mockHttpClient);
  }

  @Test
  public void wrappedExecuteShouldReturnNullForNullInput() throws Exception {
    assertNull("Null input should result in null output.",
        underTest.wrappedExecute(null, null));
  }

  @Test
  public void wrappedExecuteShouldCatchException() throws Exception {
    HttpPost request = new HttpPost(BitnamiApiManager.STACKS_URL);
    when(mockHttpClient.execute(request)).thenThrow(new IOException());
    assertNull("IOException should result in null output.",
        underTest.wrappedExecute(request, null));
    verify(mockHttpClient).execute(request);
  }

  @Test
  public void wrappedExecuteShouldExtractHttpEntity() throws Exception {
    HttpPost request = new HttpPost(BitnamiApiManager.STACKS_URL);
    String expected = "Correct HTTP entity";
    when(mockHttpClient.execute(request)).thenReturn(mockResponse);
    when(mockResponse.getEntity()).thenReturn(new StringEntity(
        expected, ContentType.APPLICATION_JSON));
    String result = underTest.wrappedExecute(request, null);
    assertNotNull("Successful request should not return null.", result);
    assertEquals(expected, result);
    verify(mockHttpClient).execute(request);
  }

  @Test
  public void fetchComponentsShouldRejectNullType() throws Exception {
    NavigableSet<VersionedEntity> result = underTest.fetchComponents();
    assertNull("Null stack component type should produce null output.", result);
  }

  @Test
  public void fetchComponentsShouldWorkWithValidResponse() throws Exception {
    when(mockHttpClient.execute(Mockito.<HttpUriRequest>anyObject()))
        .thenReturn(mockResponse);
    when(mockResponse.getEntity()).thenReturn(new StringEntity(
        GOOD_COMPONENTS_JSON_STRING, ContentType.APPLICATION_JSON));

    NavigableSet<VersionedEntity> result = underTest.fetchComponents();
    assertNotNull("Valid response should not give null output.", result);
    assertEquals("Two components should be parsed from correct JSON.", 2,
        result.size());
    assertTrue("Tomcat component should be included in parsed results.",
        result.contains(COMPONENT_TOMCAT));
    assertTrue("Java component should be included in parsed results.",
        result.contains(COMPONENT_JAVA));
    verify(mockHttpClient).execute(requestCaptor.capture());
    assertEquals("Request for components list should use correct URI.",
        VersionedEntityType.COMPONENT.getListUrl(),
        requestCaptor.getValue().getURI().toString());
    assertEquals("Request for components list should be a GET.",
        "GET", requestCaptor.getValue().getMethod());
  }
  @Test
  public void fetchComponentsShouldWorkWithEmptyResponse() throws Exception {
    when(mockHttpClient.execute(Mockito.<HttpUriRequest>anyObject()))
        .thenReturn(mockResponse);
    when(mockResponse.getEntity()).thenReturn(new StringEntity(
        "{\"items\" : []}", ContentType.APPLICATION_JSON));

    NavigableSet<VersionedEntity> result = underTest.fetchComponents();
    assertNotNull("Valid response should not give null output.", result);
    assertEquals("JSON with no items should result in empty set.", 0,
        result.size());
    verify(mockHttpClient).execute(requestCaptor.capture());
    assertEquals("Request for components list should use correct URI.",
        VersionedEntityType.COMPONENT.getListUrl(),
        requestCaptor.getValue().getURI().toString());
    assertEquals("Request for components list should be a GET.",
        "GET", requestCaptor.getValue().getMethod());
  }

  @Test
  public void fetchOperatingSystemsShouldUseCorrectURI() throws Exception {
    when(mockHttpClient.execute(Mockito.<HttpUriRequest>anyObject()))
        .thenReturn(mockResponse);
    when(mockResponse.getEntity()).thenReturn(new StringEntity(
        "{\"items\" : []}", ContentType.APPLICATION_JSON));

    NavigableSet<VersionedEntity> result = underTest.fetchOperatingSystems();
    assertNotNull("Valid response should not give null output.", result);
    assertEquals("JSON with no items should result in empty set.", 0,
        result.size());
    verify(mockHttpClient).execute(requestCaptor.capture());
    assertEquals("Request for OS list should use correct URI.",
        VersionedEntityType.OPERATING_SYSTEM.getListUrl(),
        requestCaptor.getValue().getURI().toString());
    assertEquals("Request for OS list should be a GET.",
        "GET", requestCaptor.getValue().getMethod());
  }

  @Test
  public void parseEntitiesShouldParseCorrectJSON() {
    JSONObject json = JSONObject.fromObject(GOOD_COMPONENTS_JSON_STRING);
    NavigableSet<VersionedEntity> result = BitnamiApiManager.parseEntities(
        json);
    assertNotNull("Correct JSON should not produce null component set.",
        result);
    assertEquals("Two components should be parsed from correct JSON.", 2,
        result.size());
    assertTrue("Tomcat component should be included in parsed results.",
        result.contains(COMPONENT_TOMCAT));
    assertTrue("Java component should be included in parsed results.",
        result.contains(COMPONENT_JAVA));
  }

  @Test
  public void parseEntitiesShouldRejectBadJSON() {
    JSONObject json = JSONObject.fromObject(BAD_FORMAT_JSON_STRING);
    NavigableSet<VersionedEntity> result = BitnamiApiManager.parseEntities(
        json);
    assertNull("Incorrect JSON data should produce null component set.",
        result);
  }

  @Test
  public void parseEntitiesHandlesVeryLargeJSON() {
    JSONObject json = new JSONObject();
    JSONArray items = new JSONArray();
    Set<VersionedEntity> expected = new HashSet<>();
    for (int i = 0; i < LARGE_JSON_ITEM_SIZE; i++) {
      String id = "id" + i;
      String name = "Name " + i;
      VersionedEntity component = VersionedEntity.builder()
          .setId(id)
          .setName(name)
          .setCategory(VersionedEntityType.COMPONENT)
          .addVersion(STABLE_1_0)
          .build();
      expected.add(component);
      JSONObject item = JSONObject.fromObject(
          "{" +
          "\"id\": \"id" + i + "\", " +
          "\"name\": \"Name " + i + "\", " +
          "\"category\": \"runtime\", " +
          "\"versions\": [" +
          "{" +
            "\"version\": \"1.0\", " +
            "\"revision\": 0, " +
            "\"branch\": \"stable\", " +
            "\"checksum\": \"0xdeadbeef\", " +
            "\"published_at\": \"2015-11-20\"" +
          "}]}");
      items.add(item);
    }
    json.put("items", items);
    NavigableSet<VersionedEntity> result = BitnamiApiManager.parseEntities(
        json);
    assertNotNull("Correct JSON should not produce null component set.",
        result);
    assertEquals("Large JSON should contain " + LARGE_JSON_ITEM_SIZE
        + " runtimes", LARGE_JSON_ITEM_SIZE, result.size());
    assertTrue("Large JSON parsed components should equal expected components.",
        result.containsAll(expected));
  }

  @Test
  public void fetchDockerfileShouldRejectNullInput() throws Exception {
    assertNull("Null StackReference should result in null output.",
        underTest.fetchDockerfile(null, null));
  }

  @Test
  public void fetchDockerfileShouldWorkForGoodInput() throws Exception {
    String expectedDockerfile = "#This is a test Dockerfile.";
    StackReference stack = StackReference.builder()
        .setId(STACK_ID_1)
        .setStackUrl(STACK_URL_1)
        .build();

    when(mockHttpClient.execute(Mockito.<HttpUriRequest>anyObject()))
        .thenReturn(mockResponse);
    when(mockResponse.getEntity()).thenReturn(new StringEntity(
        expectedDockerfile, ContentType.TEXT_PLAIN));

    String result = underTest.fetchDockerfile(stack, null);
    assertNotNull("Valid response should not give null output", result);
    assertEquals("Dockerfile should be parsed from Stacksmith response.",
        expectedDockerfile, result);
    verify(mockHttpClient).execute(requestCaptor.capture());
    assertEquals("Request for Dockerfile should use correct URI.",
        stack.getDockerfileUrl(),
        requestCaptor.getValue().getURI().toString());
    assertEquals("Request for Dockerfile should be a GET.",
        "GET", requestCaptor.getValue().getMethod());
  }

  @Test
  public void makeStackShouldWorkForComponentOnly() throws Exception {
    String expectedJsonRequestString = "{" +
        "\"components\": [{" +
          "\"id\": \"express\", " +
          "\"version\": \"latest\"" +
        "}]}";
    VersionedEntityRequirement express = VersionedEntityRequirement.builder()
        .setId("express")
        .setVersionOperator(VersionRequirementOperator.LATEST)
        .build();

    List<VersionedEntityRequirement> components = new ArrayList<>();
    components.add(express);

    prepareMakeStackResponse();
    StackReference result = underTest.makeStack(components, null, null, null);
    verifyMakeStack(result, expectedJsonRequestString);
  }

  @Test
  /**
   * While the current API does not permit OS-only stacks, our own code
   * should not be the point of failure.
   */
  public void makeStackShouldWorkForOSOnly() throws Exception {
    String expectedJsonRequestString = "{" +
        "\"os\": {" +
          "\"id\": \"debian\", " +
          "\"version\": \"= wheezy\"" +
        "}}";
    VersionedEntityRequirement os = VersionedEntityRequirement.builder()
        .setId("debian")
        .setVersionOperator(VersionRequirementOperator.EQ)
        .setVersionNumber("wheezy")
        .build();

    prepareMakeStackResponse();
    StackReference result = underTest.makeStack(null, os, null, null);
    verifyMakeStack(result, expectedJsonRequestString);
  }

  @Test
  /**
   * While the current API does not permit flavor-only stacks, our own code
   * should not be the point of failure.
   */
  public void makeStackShouldWorkForFlavorOnly() throws Exception {
    String expectedJsonRequestString = "{" +
        "\"flavor\": \"tomcat-server\"" +
        "}}";

    prepareMakeStackResponse();
    StackReference result = underTest.makeStack(null, null, TOMCAT_FLAVOR, null);
    verifyMakeStack(result, expectedJsonRequestString);
  }

  @Test
  public void makeStackShouldIgnoreEmptyComponents() throws Exception {
    String expectedJsonRequestString = "{" +
        "\"os\": {" +
          "\"id\": \"debian\", " +
          "\"version\": \"= wheezy\"" +
        "}}";
    VersionedEntityRequirement os = VersionedEntityRequirement.builder()
        .setId("debian")
        .setVersionOperator(VersionRequirementOperator.EQ)
        .setVersionNumber("wheezy")
        .build();

    List<VersionedEntityRequirement> components = new ArrayList<>();
    components.add(VersionedEntityRequirement.NO_REQUIREMENT);

    prepareMakeStackResponse();
    StackReference result = underTest.makeStack(components, os, null, null);
    verifyMakeStack(result, expectedJsonRequestString);
  }

  @Test
  public void makeStackShouldWorkForCompleteSpec() throws Exception {
    String expectedJsonRequestString = "{" +
        "\"components\": [{" +
          "\"id\": \"tomcat\", " +
          "\"version\": \"latest\"" +
        "}], " +
        "\"os\": {" +
          "\"id\": \"debian\", " +
          "\"version\": \"= wheezy\"" +
        "}, " +
        "\"flavor\": \"tomcat-server\"" +
        "}";
    VersionedEntityRequirement tomcat = VersionedEntityRequirement.builder()
        .setId("tomcat")
        .setVersionOperator(VersionRequirementOperator.LATEST)
        .build();
    List<VersionedEntityRequirement> components = new ArrayList<>();
    components.add(tomcat);

    VersionedEntityRequirement os = VersionedEntityRequirement.builder()
        .setId("debian")
        .setVersionOperator(VersionRequirementOperator.EQ)
        .setVersionNumber("wheezy")
        .build();

    prepareMakeStackResponse();
    StackReference result = underTest.makeStack(components, os, TOMCAT_FLAVOR, null);
    verifyMakeStack(result, expectedJsonRequestString);
  }

  @Test
  public void fetchDependenciesForEntityShouldWorkOnGoodInput() throws Exception {
    when(mockHttpClient.execute(Mockito.<HttpUriRequest>anyObject()))
        .thenReturn(mockResponse);
    when(mockResponse.getEntity()).thenReturn(new StringEntity(
        GOOD_DEPENDENCIES_JSON_STRING, ContentType.APPLICATION_JSON));
    Set<String> expected = new HashSet<>();
    expected.add("java");

    Set<String> ids = underTest.fetchDependenciesForEntity("jetty");

    assertEquals("One dependency id named 'java' expected.", expected, ids);
    verify(mockHttpClient).execute(requestCaptor.capture());
    assertEquals("Request for dependencies should use correct URI.",
        "https://stacksmith.bitnami.com/api/v1/components/jetty/dependencies",
        requestCaptor.getValue().getURI().toString());
    assertEquals("Request for dependencies should be a GET.",
        "GET", requestCaptor.getValue().getMethod());
  }

  @Test
  public void fetchFlavorsForEntityShouldWorkOnGoodInput() throws Exception {
    when(mockHttpClient.execute(Mockito.<HttpUriRequest>anyObject()))
        .thenReturn(mockResponse);
    when(mockResponse.getEntity()).thenReturn(new StringEntity(
        GOOD_FLAVORS_JSON_STRING, ContentType.APPLICATION_JSON));
    Set<String> expected = new HashSet<>();
    expected.add("tomcat-server");

    Set<String> ids = underTest.fetchFlavorsForEntity("tomcat");

    assertEquals("One flavor id named 'tomcat-server' expected.", expected, ids);
    verify(mockHttpClient).execute(requestCaptor.capture());
    assertEquals("Request for flavors should use correct URI.",
        "https://stacksmith.bitnami.com/api/v1/components/tomcat/flavors",
        requestCaptor.getValue().getURI().toString());
    assertEquals("Request for flavors should be a GET.",
        "GET", requestCaptor.getValue().getMethod());
  }

  @Test
  public void parseDependencyIdsShouldWorkOnGoodInput() throws Exception {
    Set<String> ids = BitnamiApiManager.parseDependencyIds(
        JSONObject.fromObject(GOOD_DEPENDENCIES_JSON_STRING));
    Set<String> expected = new HashSet<>();
    expected.add("java");
    assertEquals("One dependency id named 'java' expected.", expected, ids);
  }

  @Test
  public void parseDependencyIdsShouldReturnNullOnNullInput() throws Exception {
    Set<String> ids = BitnamiApiManager.parseDependencyIds(null);
    assertNull(ids);
  }

  @Test
  public void parseDependencyIdsShouldReturnNullOnBadInput() throws Exception {
    Set<String> ids = BitnamiApiManager.parseDependencyIds(
        JSONObject.fromObject(BAD_FORMAT_JSON_STRING));
    assertNull(ids);
  }

  @Test
  public void parseFlavorIdsShouldWorkOnGoodInput() throws Exception {
    Set<String> ids = BitnamiApiManager.parseFlavorIds(
        JSONObject.fromObject(GOOD_FLAVORS_JSON_STRING));
    Set<String> expected = new HashSet<>();
    expected.add("tomcat-server");
    assertEquals("One dependency id named 'tomcat-server' expected.", expected, ids);
  }

  @Test
  public void parseFlavorIdsShouldReturnNullOnNullInput() throws Exception {
    Set<String> ids = BitnamiApiManager.parseFlavorIds(null);
    assertNull(ids);
  }

  @Test
  public void parseFlavorIdsShouldReturnNullOnBadInput() throws Exception {
    Set<String> ids = BitnamiApiManager.parseFlavorIds(
        JSONObject.fromObject(BAD_FORMAT_JSON_STRING));
    assertNull(ids);
  }

  /**
   * Helper method that constructs a correctly-formatted response to a stack creation
   * request, and configures the mock HTTP objects to return that response.
   */
  private void prepareMakeStackResponse() throws Exception {
    String jsonResponseString = "{" +
        "\"id\": \"" + STACK_ID_1 + "\", " +
        "\"stack_url\": \"" + STACK_URL_1 + "\"" +
        "}";
    when(mockHttpClient.execute(Mockito.<HttpUriRequest>anyObject()))
        .thenReturn(mockResponse);
    when(mockResponse.getEntity()).thenReturn(new StringEntity(
        jsonResponseString, ContentType.APPLICATION_JSON));
  }

  private void verifyMakeStack(StackReference result, String expectedJson)
      throws Exception {
    assertNotNull(
        "Valid Stacksmith response should not result in null StackReference.",
        result);
    assertEquals("Unexpected stack ID parsed from Stacksmith response.",
        STACK_ID_1, result.getId());
    assertEquals("Unexpected stack URL parsed from Stacksmith response.",
        STACK_URL_1, result.getStackUrl());

    verify(mockHttpClient).execute(postCaptor.capture());
    assertEquals("Stack creation request should use correct URI.",
        BitnamiApiManager.STACKS_URL,
        postCaptor.getValue().getURI().toString());
    assertEquals("Stack creation request should be a POST.",
        "POST", postCaptor.getValue().getMethod());

    // We use this construction instead of direct String comparisons because
    // whitespace and formatting should be meaningless.
    assertEquals("Stack creation request should contain expected JSON.",
        JSONObject.fromObject(expectedJson),
        JSONObject.fromObject(
            EntityUtils.toString(postCaptor.getValue().getEntity())));
  }
}
