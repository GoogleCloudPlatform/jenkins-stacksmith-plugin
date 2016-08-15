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

import java.util.HashMap;
import java.util.Map;

/**
 * Enum of the different categories of versioned entities used in Stacksmith.
 */
public enum VersionedEntityType {
  COMPONENT("components"),
  OPERATING_SYSTEM("oses"),
  UNKNOWN("");

  private static final Map<String, VersionedEntityType> apiStringMap
      = new HashMap<>();

  static {
    apiStringMap.put("component", COMPONENT);
    apiStringMap.put("service", COMPONENT);
    apiStringMap.put("runtime", COMPONENT);
    apiStringMap.put("os", OPERATING_SYSTEM);
  }

  private final String listUrl;

  private VersionedEntityType(String listUrlFragment) {
    this.listUrl = BitnamiApiManager.API_BASE_URL + listUrlFragment;
  }

  /**
   * Returns the Discovery API URL for listing objects of this category.
   */
  public String getListUrl() {
    return listUrl;
  }

  /**
   * Returns the corresponding entity category, or {@link #UNKNOWN} if no
   * corresponding category exists.
   */
  public static VersionedEntityType fromApiString(String apiString) {
    VersionedEntityType result = apiStringMap.get(apiString);
    if (result == null) {
      result = VersionedEntityType.UNKNOWN;
    }
    return result;
  }
}
