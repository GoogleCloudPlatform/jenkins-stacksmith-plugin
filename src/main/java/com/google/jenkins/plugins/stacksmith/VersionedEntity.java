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

import java.util.Iterator;
import java.util.NavigableSet;
import java.util.TreeSet;

import javax.annotation.concurrent.Immutable;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ComparisonChain;

/**
 * Encapsulates the common notion of a versioned Stacksmith entity, as used
 * by both components and OSes.
 */
@AutoValue
@Immutable
public abstract class VersionedEntity implements Comparable<VersionedEntity> {
  VersionedEntity() {}

  /**
   * @return the ID string as used in the Stacksmith API.
   */
  public abstract String getId();
  /**
   * @return the user-visible name of the entity.
   */
  public abstract String getName();
  /**
   * @return the type of entity - component or OS.
   */
  public abstract VersionedEntityType getCategory();
  /**
   * @return the set of known versions for this entity.
   */
  public abstract NavigableSet<BranchedVersion> getVersions();

  /**
   * @return a simple displayable string representation of this entity
   * and its possible versions.
   */
  public String toShortString() {
    StringBuilder builder = new StringBuilder();
    builder.append(getName()).append(" ");
    if (!getVersions().isEmpty()) {
      builder.append("{");
      for (BranchedVersion version : getVersions()) {
        builder.append(version.toShortString());
        builder.append(",");
      }
      builder.append("}");
    }

    return builder.toString();
  }

  /**
   * Provides an ordering over entities, to allow sorting. Sorting order is:
   * first by type, then by user-visible entity name, then by version sets.
   */
  @Override
  public int compareTo(VersionedEntity that) {
    ComparisonChain chain = ComparisonChain.start()
        .compare(this.getCategory(), that.getCategory())
        .compare(this.getName(), that.getName());
    Iterator<BranchedVersion> thisVersionIter = this.getVersions().iterator();
    Iterator<BranchedVersion> thatVersionIter = that.getVersions().iterator();
    while (thisVersionIter.hasNext() && thatVersionIter.hasNext()) {
      chain = chain.compare(thisVersionIter.next(), thatVersionIter.next());
    }
    chain = chain.compare(this.getVersions().size(), that.getVersions().size());
    return chain.result();
  }

  public static Builder builder() {
    return new AutoValue_VersionedEntity.Builder();
  }

  /**
   * Builder class for {@link VersionedEntity}.
   */
  @AutoValue.Builder
  public abstract static class Builder {
    private final TreeSet<BranchedVersion> versions = new TreeSet<>();
    public abstract Builder setId(String id);
    public abstract Builder setName(String name);
    public abstract Builder setCategory(VersionedEntityType category);

    public Builder addVersion(BranchedVersion version) {
      if (version == null) {
        throw new NullPointerException();
      }
      versions.add(version);
      return this;
    }

    /**
     * Necessary for {@link AutoValue} framework, but should not be invoked by
     * external code. Therefore, it is set to protected.
     */
    protected abstract Builder setVersions(
        NavigableSet<BranchedVersion> versions);

    /**
     * Necessary for {@link AutoValue} framework, but should not be invoked by
     * external code. Therefore, it is set to protected.
     */
    protected abstract VersionedEntity autoBuild();

    public VersionedEntity build() {
      setVersions(versions);
      return autoBuild();
    }
  }
}
