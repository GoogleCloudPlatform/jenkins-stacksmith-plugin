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

/**
 * Encapsulates a reference to a Stacksmith Stack.
 */
@AutoValue
@Immutable
public abstract class StackReference {
  private static final String DOCKERFILE_URL_POSTFIX = ".dockerfile";
  StackReference() {}

  /**
   * @return the Stack ID string as used in the Stacksmith API.
   */
  public abstract String getId();
  /**
   * @return the Stacksmith API URL for this stack.
   */
  public abstract String getStackUrl();

  /**
   * @return the Stacksmith API URL for the Dockerfile associated with this
   * stack.
   */
  public String getDockerfileUrl() {
    return getStackUrl() + DOCKERFILE_URL_POSTFIX;
  }

  public static Builder builder() {
    return new AutoValue_StackReference.Builder();
  }

  /**
   * Builder class for {@link StackReference}.
   */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setId(String id);
    public abstract Builder setStackUrl(String stackUrl);
    public abstract StackReference build();
  }
}
