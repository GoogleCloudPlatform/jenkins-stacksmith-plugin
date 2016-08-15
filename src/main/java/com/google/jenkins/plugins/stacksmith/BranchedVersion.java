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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import com.google.common.collect.ComparisonChain;

/**
 * Encapsulates the concept of a version with a branch annotation.
 * <p>
 * For sorting, comparisons are made first on the version string, then on the
 * branch string. Null version or branch strings are stored as empty strings.
 */
@Immutable
public class BranchedVersion implements Comparable<BranchedVersion> {
  private final String version;
  private final String branch;
  private final String shortString;

  /**
   * @param version the version string, e.g. &quot;1.2.1&quot;. Null input is
   * treated as the empty string.
   * @param branch the branch annotation, e.g. &quot;stable&quot;. Null input is
   * treated as the empty string.
   */
  public BranchedVersion(String version, String branch) {
    this.version = version == null ? "" : version;
    this.branch = branch == null ? "" : branch;
    this.shortString = toShortString(this.version, this.branch);
  }

  /**
   * @return the version string, e.g. &quot;1.2.1&quot;. May be empty.
   */
  public String getVersion() {
    return version;
  }

  /**
   * @return the branch annotation, e.g. &quot;stable&quot;. May be empty.
   */
  public String getBranch() {
    return branch;
  }

  public int compareTo(BranchedVersion that) {
    return ComparisonChain.start()
        .compare(this.version, that.version, VersionComparator.get())
        .compare(this.branch, that.branch)
        .result();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof BranchedVersion)) {
      return false;
    }
    BranchedVersion that = (BranchedVersion) obj;
    return Objects.equals(this.version, that.version)
        && Objects.equals(this.branch, that.branch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, branch);
  }

  @Override
  public String toString() {
    return "{version=" + version + ", branch=" + branch + "}";
  }

  /**
   * @return a user-displayable {@link String} representation of the combined
   * version and branch annotation.
   */
  public String toShortString() {
    return shortString;
  }

  private static String toShortString(String version, String branch) {
    StringBuilder builder = new StringBuilder();
    if (!version.isEmpty()) {
      builder.append(version);
    }
    if (!version.isEmpty() && !branch.isEmpty()) {
      builder.append(" ");
    }
    if (!branch.isEmpty()) {
      builder.append("(").append(branch).append(")");
    }
    return builder.toString();
  }
}
