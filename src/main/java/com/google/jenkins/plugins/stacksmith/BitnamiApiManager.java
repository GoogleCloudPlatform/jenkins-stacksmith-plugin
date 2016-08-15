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

import java.io.IOException;
import java.io.PrintStream;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

/**
 * Handles interaction with the Bitnami Stacksmith API.
 */
@ThreadSafe
public class BitnamiApiManager {
  /**
   * The base URL for all API requests.
   */
  public static final String API_BASE_URL
      = "https://stacksmith.bitnami.com/api/v1/";
  /**
   * The URL for the stack API.
   */
  public static final String STACKS_URL = API_BASE_URL + "stacks";
  private static final Logger LOGGER = Logger.getLogger(
      BitnamiApiManager.class.getName());
  private static final CombinedLogger COMBINED_LOGGER = new CombinedLogger(
      LOGGER);
  private static final BitnamiApiManager INSTANCE = new BitnamiApiManager(
      HttpClients.createDefault());

  protected final CloseableHttpClient httpClient;

  @VisibleForTesting
  BitnamiApiManager(CloseableHttpClient httpClient) {
    this.httpClient = Preconditions.checkNotNull(httpClient);
  }

  /**
   * @return the default instance of {@link BitnamiApiManager}.
   */
  public static BitnamiApiManager get() {
    return INSTANCE;
  }

  /**
   * Executes the given request, catching common exceptions.
   * @return the response entity, if any, as a {@link String} - or {@code null}
   * if an error occurs.
   */
  protected String wrappedExecute(HttpUriRequest request,
      PrintStream buildLogger) {
    if (request == null) {
      COMBINED_LOGGER.logWarning(buildLogger,
          "Cannot execute a null HTTP request.");
      return null;
    }

    CloseableHttpResponse response = null;
    String result = null;
    try {
      LOGGER.finer("Executing HTTP request: " + request);
      response = httpClient.execute(request);
      if (response == null) {
        COMBINED_LOGGER.logWarning(buildLogger,
            "Null response from Stacksmith API.");
        return null;
      }
      if (response.getStatusLine() != null) {
        LOGGER.finer("HTTP response status: " + response.getStatusLine());
      }
      result = EntityUtils.toString(response.getEntity());
      LOGGER.finest("HTTP response entity: "
          + (result == null ? "null" : result));
    } catch (IOException e) {
      COMBINED_LOGGER.logWarning(buildLogger,
          "IOException when contacting Stackmith: " + e);
      return null;
    } finally {
      try {
        if (response != null) {
          response.close();
        } else {
          // This has already been logged above; no need to log it twice.
          return null;
        }
      } catch (IOException e) {
        COMBINED_LOGGER.logWarning(buildLogger,
            "IOException when closing HTTP resposne: " + e);
        return null;
      }
    }
    return result;
  }

  /**
   * Queries the Stacksmith discovery API to get the set of valid entities
   * of the given type.
   * @return the set of entities, or {@code null} if an error occurs.
   */
  protected NavigableSet<VersionedEntity> fetchEntities(VersionedEntityType category) {
    if (category == null) {
      LOGGER.warning("Stack entity type is null, can't fetch entity.");
      return null;
    }
    NavigableSet<VersionedEntity> entities = null;
    HttpGet httpGet = new HttpGet(category.getListUrl());
    String result = wrappedExecute(httpGet, null);
    if (result == null) {
      // This is at FINE rather than WARNING because we expect wrappedExecute
      // to log a warning for any major error.
      LOGGER.fine("No JSON result from querying the Discovery API.");
      return null;
    }

    try {
      JSONObject json = JSONObject.fromObject(result);
      entities = parseEntities(json);
    } catch (JSONException e) {
      LOGGER.warning("JSONException while parsing entities: " + e);
      return null;
    }
    return entities;
  }

  /**
   * Queries the Stacksmith discovery API to get the set of valid
   * operating systems that can be used in a Stack.
   * @return the set of operating systems, or {@code null} if an error occurs.
   */
  public NavigableSet<VersionedEntity> fetchOperatingSystems() {
    return fetchEntities(VersionedEntityType.OPERATING_SYSTEM);
  }

  /**
   * Queries the Stacksmith discovery API to get the set of valid components
   * that can be used in a Stack.
   * @return the set of components, or {@code null} if an error occurs.
   */
  public NavigableSet<VersionedEntity> fetchComponents() {
    return fetchEntities(VersionedEntityType.COMPONENT);
  }

  /**
   * Queries the Stacksmith discovery API to get the set of dependencies associated
   * with a given entity.
   * @param id the {@link String} ID of the entity to get data for.
   * @return the set of dependency IDs, or {@code null} if an error occurs.
   */
  public NavigableSet<String> fetchDependenciesForEntity(String id) {
    if (id == null) {
      LOGGER.warning("Cannot fetch dependencies for a null entity ID");
      return null;
    }
    NavigableSet<String> dependencies = null;
    HttpGet httpGet = new HttpGet(makeDependenciesForEntityUrl(id));
    String result = wrappedExecute(httpGet, null);
    if (result == null) {
      LOGGER.fine("No JSON result.");
      return null;
    }

    try {
      JSONObject json = JSONObject.fromObject(result);
      dependencies = parseDependencyIds(json);
    } catch (JSONException e) {
      LOGGER.warning("JSONException while parsing dependencies: " + e);
      return null;
    }
    return dependencies;
  }

  /**
   * Queries the Stacksmith discovery API to get the set of flavors associated
   * with a given entity.
   * @param id the {@link String} ID of the entity to get data for.
   * @return the set of flavor IDs, or {@code null} if an error occurs.
   */
  public NavigableSet<String> fetchFlavorsForEntity(String id) {
    if (id == null) {
      LOGGER.warning("Cannot fetch flavors for a null entity ID");
      return null;
    }
    NavigableSet<String> flavors = null;
    HttpGet httpGet = new HttpGet(makeFlavorsForEntityUrl(id));
    String result = wrappedExecute(httpGet, null);
    if (result == null) {
      LOGGER.fine("No JSON result.");
      return null;
    }

    try {
      JSONObject json = JSONObject.fromObject(result);
      flavors = parseFlavorIds(json);
    } catch (JSONException e) {
      LOGGER.warning("JSONException while parsing flavors: " + e);
      return null;
    }
    return flavors;
  }

  /**
   * Queries Stacksmith to get the Dockerfile corresponding to a given stack
   * reference.
   * @return the contents of the Dockerfile, or {@code null} if an error occurs.
   */
  public String fetchDockerfile(StackReference stack,
      @Nullable PrintStream buildLogger) {
    if (stack == null) {
      COMBINED_LOGGER.logWarning(buildLogger,
          "Null stack reference in fetchDockerFile.");
      return null;
    }

    HttpGet httpGet = new HttpGet(stack.getDockerfileUrl());
    String result = wrappedExecute(httpGet, buildLogger);
    if (result != null) {
      COMBINED_LOGGER.logFine(buildLogger,
          "Dockerfile received from Stacksmith API.");
    }
    return result;
  }

  /**
   * Sends a query to the Stacksmith API requesting a new stack to be created
   * using the specified components and OS.
   * @param components the components to use in the stack.
   * @param os the operating system, if any, to include in the stack.
   * @param flavor the flavor, if any, to include in the stack.
   * @param buildLogger a stream to use for logging.
   * @return the resulting stack reference, or {@code null} if an error occurs.
   */
  public StackReference makeStack(Iterable<VersionedEntityRequirement> components,
      @Nullable VersionedEntityRequirement os,
      @Nullable String flavor,
      @Nullable PrintStream buildLogger) {
    HttpPost httpPost = new HttpPost(STACKS_URL);
    JSONObject requirements = new JSONObject();

    if (components != null) {
      JSONArray componentsJSON = new JSONArray();
      boolean nonemptyComponents = false;
      for (VersionedEntityRequirement component : components) {
        if (!component.isNone()) {
          componentsJSON.add(component.toJSON());
          nonemptyComponents = true;
        }
      }
      if (nonemptyComponents) {
        requirements.element("components", componentsJSON);
      }
    }

    if (os != null && !os.isNone()) {
      requirements.element("os", os.toJSON());
    }

    if (!Strings.isNullOrEmpty(flavor)) {
      requirements.element("flavor", flavor);
    }

    httpPost.setEntity(new StringEntity(requirements.toString(),
        ContentType.APPLICATION_JSON));
    String result = wrappedExecute(httpPost, buildLogger);

    try {
      JSONObject json = JSONObject.fromObject(result);
      return StackReference.builder()
          .setId(json.getString("id"))
          .setStackUrl(json.getString("stack_url"))
          .build();
    } catch (JSONException e) {
      COMBINED_LOGGER.logWarning(buildLogger, "JSONException: " + e);
      return null;
    }
  }

  /**
   * Creates the Stacksmith API URL for Dependency discovery for a given entity.
   */
  protected static String makeDependenciesForEntityUrl(String entityId) {
    return API_BASE_URL + "components/" + entityId + "/dependencies";
  }

  /**
   * Creates the Stacksmith API URL for Flavor discovery for a given entity.
   */
  protected static String makeFlavorsForEntityUrl(String entityId) {
    return API_BASE_URL + "components/" + entityId + "/flavors";
  }

  /**
   * Attempts to parse the input into a set of {@link VersionedEntity} objects.
   * @return the parsed set, or {@code null} if the input JSON is invalid.
   */
  protected static NavigableSet<VersionedEntity> parseEntities(JSONObject json) {
    if (json == null) {
      LOGGER.warning("Cannot parse entities from null JSON.");
      return null;
    }

    NavigableSet<VersionedEntity> result = new TreeSet<>();
    try {
      JSONArray items = json.getJSONArray("items");
      for (int i = 0; i < items.size(); i++) {
        VersionedEntity.Builder builder = VersionedEntity.builder();
        JSONObject item = items.getJSONObject(i);
        builder.setId(item.getString("id"));
        builder.setName(item.getString("name"));
        builder.setCategory(VersionedEntityType.fromApiString(
            item.getString("category")));
        JSONArray versions = item.getJSONArray("versions");
        for (int j = 0; j < versions.size(); j++) {
          JSONObject versionObject = versions.getJSONObject(j);
          builder.addVersion(new BranchedVersion(
              versionObject.getString("version"),
              versionObject.getString("branch")));
        }
        result.add(builder.build());
      }
      return result;
    } catch (JSONException e) {
      LOGGER.warning("JSONException while parsing entities: " + e);
      return null;
    }
  }

  /**
   * Attempts to parse the input into a set of ID strings per the expected format
   * of an API response to the list-dependencies query.
   * @return the parsed set, or {@code null} if the input JSON is invalid.
   */
  @VisibleForTesting
  static NavigableSet<String> parseDependencyIds(JSONObject json) {
    if (json == null) {
      LOGGER.warning("Cannot parse dependency IDs from null JSON.");
      return null;
    }

    NavigableSet<String> result = new TreeSet<>();
    try {
      JSONArray items = json.getJSONArray("items");
      for (int i = 0; i < items.size(); i++) {
        result.add(items.getString(i));
      }
      return result;
    } catch (JSONException e) {
      LOGGER.warning("JSONException while parsing dependency IDs: " + e);
      return null;
    }
  }

  /**
   * Attempts to parse the input into a set of ID strings per the expected format
   * of an API response to the list-flavors query.
   * @return the parsed set, or {@code null} if the input JSON is invalid.
   */
  @VisibleForTesting
  static NavigableSet<String> parseFlavorIds(JSONObject json) {
    if (json == null) {
      LOGGER.warning("Cannot parse dependency IDs from null JSON.");
      return null;
    }

    NavigableSet<String> result = new TreeSet<>();
    try {
      JSONArray items = json.getJSONArray("items");
      for (int i = 0; i < items.size(); i++) {
        JSONObject item = items.getJSONObject(i);
        result.add(item.getString("id"));
      }
      return result;
    } catch (JSONException e) {
      LOGGER.warning("JSONException while parsing flavor IDs: " + e);
      return null;
    }
  }
}
