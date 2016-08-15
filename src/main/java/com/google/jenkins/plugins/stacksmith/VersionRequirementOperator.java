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

import javax.annotation.Nullable;

/**
 * Encapsulates the Stacksmith concept of version operators - that is,
 * requiring a version number greater than a given number, less than, etc.
 * Two special version operators request the latest and dev versions.
 */
public enum VersionRequirementOperator {
  LATEST("latest"),
  DEV("dev"),
  EQ("="),
  GT(">"),
  GE(">="),
  LT("<"),
  LE("<="),
  PESSIMISTIC("~>");

  private static final Map<String, VersionRequirementOperator> apiStringMap
      = new HashMap<>();

  static {
    for (VersionRequirementOperator op : VersionRequirementOperator.values()) {
      apiStringMap.put(op.getApiString(), op);
    }
  }

  private final String apiString;

  private VersionRequirementOperator(String apiString) {
    this.apiString = apiString;
  }

  /**
   * Returns the API string fragment corresponding to this version operator.
   */
  public String getApiString() {
    return apiString;
  }

  /**
   * Returns a complete API string constructed from this operator and the
   * given version number.
   */
  public String makeFullApiString(String versionNumber) {
    if (this == LATEST || this == DEV) {
      return getApiString();
    }
    return getApiString() + " " + versionNumber;
  }

  /**
   * Returns the requirement operator corresponding to this API string,
   * or {@link LATEST} if no requirement operator has such an API string.
   */
  public static VersionRequirementOperator get(@Nullable String apiString) {
    VersionRequirementOperator result = getNullable(apiString);
    return result == null ? LATEST : result;
  }

  /**
   * As {@link #get(String)}, but returns {@code null} if no matching
   * requirement operator is found.
   */
  @Nullable public static VersionRequirementOperator getNullable(
      @Nullable String apiString) {
    return apiStringMap.get(apiString);
  }
}
