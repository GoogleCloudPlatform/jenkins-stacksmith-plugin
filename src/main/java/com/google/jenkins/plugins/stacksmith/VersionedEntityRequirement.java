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

import javax.annotation.concurrent.Immutable;

import com.google.auto.value.AutoValue;

import net.sf.json.JSONObject;

/**
 * Encapsulates a requirement to be used in the Stacksmith API. The API will attempt to provide an
 * entity in the resulting Stack that best matches this requirement. Requirements are composed of
 * an ID, a version operator, and a version number. The version number is optional when the
 * version operator is {@link VersionRequirementOperator#LATEST} or
 * {@link VersionRequirementOperator#DEV}.
 * <p>
 * For example, requiring a Java version equal to 1.7 or higher is expressed as a
 * {@code VersionedEntityRequirement} with an ID of {@code "java"}, a requirement operator of
 * {@link VersionRequirementOperator#GE}, and a version number of {@code "1.7"}.
 */
@AutoValue
@Immutable
public abstract class VersionedEntityRequirement {
  public static final VersionedEntityRequirement NO_REQUIREMENT
      = builder().setId(StacksmithBuilder.NONE_ID).build();

  /**
   * Required for the {@link AutoValue} framework.
   */
  VersionedEntityRequirement() {}

  /**
   * @return the component ID string as used in the Stacksmith API.
   */
  public abstract String getId();

  /**
   * @return the version operator. This is used to specify version ranges, such
   * as &quot;at least version 1.0&quot;, or to specify special version matches
   * such as &quot;latest&quot;.
   */
  public abstract VersionRequirementOperator getVersionOperator();

  /**
   * @return the version number requested, if any; note that this may be
   * modified by the version requirement operator as specified by
   * {@link #getVersionOperator()}.
   */
  public abstract String getVersionNumber();

  /**
   * @return true iff this requirement is actually a non-requirement placeholder
   * - that is, if the id is the &quot;NONE&quot; id.
   */
  public boolean isNone() {
    return getId().equals(StacksmithBuilder.NONE_ID);
  }

  /**
   * Returns the full version string, formatted for the API syntax. This is a combination of
   * the requirement operator and the version number.
   */
  public String getVersionString() {
    return getVersionOperator().makeFullApiString(getVersionNumber());
  }

  /**
   * Produces a {@link JSONObject} that encapsulates this requirement in JSON,
   * formatted for the API syntax.
   */
  public JSONObject toJSON() {
    JSONObject object = new JSONObject();
    object.element("id", getId());
    object.element("version", getVersionString());
    return object;
  }

  public static Builder builder() {
    return new AutoValue_VersionedEntityRequirement.Builder()
        .setVersionOperator(VersionRequirementOperator.LATEST)
        .setVersionNumber("");
  }

  /**
   * @return a non-requirement placeholder object, which the API should interpret
   * as a lack of requirements.
   */
  public static VersionedEntityRequirement getNoRequirement() {
    return NO_REQUIREMENT;
  }

  /**
   * Builder class for {@link VersionedEntityRequirement}.
   */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setId(String id);
    /**
     * Defaults to {@link VersionRequirementOperator#LATEST}.
     */
    public abstract Builder setVersionOperator(
        VersionRequirementOperator versionOperator);
    /**
     * Defaults to the empty string. Must be set to a non-empty value
     * unless the version operator is {@link VersionRequirementOperator#LATEST}
     * or {@link VersionRequirementOperator#DEV}.
     */
    public abstract Builder setVersionNumber(String version);

    /**
     * Necessary for {@link AutoValue} framework, but should not be invoked by
     * external code. Therefore, it is set to protected.
     */
    protected abstract VersionedEntityRequirement autoBuild();

    /**
     * Constructs the object and verifies that it is properly configured.
     * @throws IllegalStateException if a version operator is specified without a version number.
     */
    public VersionedEntityRequirement build() throws IllegalStateException {
      VersionedEntityRequirement result = autoBuild();
      VersionRequirementOperator op = result.getVersionOperator();
      if (result.getVersionNumber().isEmpty()
          && op != VersionRequirementOperator.LATEST
          && op != VersionRequirementOperator.DEV) {
        throw new IllegalStateException("Version number must be specified when "
            + "version operator is not LATEST or DEV.");
      }
      return result;
    }
  }
}
