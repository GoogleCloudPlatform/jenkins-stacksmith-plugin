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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;

/**
 * Dockerfile builder that uses the Bitnami Stacksmith service.
 */
public class StacksmithBuilder extends Builder {
  /** Used for default options in dropdowns. */
  protected static final String NONE_ID = "NONE";
  /** Used for default options in dropdowns. */
  protected static final String NONE_DISPLAY_TEXT
      = Messages.StacksmithBuilder_NoneDisplayText();
  private static final Logger logger = Logger.getLogger(
      StacksmithBuilder.class.getName());
  private static final CombinedLogger combinedLogger = new CombinedLogger(logger);

  protected final VersionedEntityRequirement componentRequirement;
  protected final VersionedEntityRequirement osRequirement;
  protected final String flavor;
  protected final boolean writeDockerfile;
  protected final String dockerfileDiskDirectory;
  protected final transient BitnamiApiManager apiManager;

  /**
   * This constructor accepts an API manager, allowing callers - including tests - to use
   * a custom or mock API manager.
   */
  public StacksmithBuilder(
      BitnamiApiManager apiManager,
      String componentId,
      String componentVersionOperator,
      String componentVersion,
      String operatingSystemId,
      String operatingSystemVersionOperator,
      String operatingSystemVersion,
      String flavor,
      boolean writeDockerfile,
      String dockerfileDiskDirectory) {
    this.apiManager = apiManager;
    this.componentRequirement = requirementFrom(componentId,
        componentVersionOperator, componentVersion);
    this.osRequirement = requirementFrom(operatingSystemId,
        operatingSystemVersionOperator, operatingSystemVersion);
    this.flavor = flavor;
    this.writeDockerfile = writeDockerfile;
    this.dockerfileDiskDirectory = dockerfileDiskDirectory;
  }

  /**
   * This constructor uses the default implementation of {@link BitnamiApiManager}.
   */
  @DataBoundConstructor
  public StacksmithBuilder(
      String componentId,
      String componentVersionOperator,
      String componentVersion,
      String operatingSystemId,
      String operatingSystemVersionOperator,
      String operatingSystemVersion,
      String flavor,
      boolean writeDockerfile,
      String dockerfileDiskDirectory) {
    this(BitnamiApiManager.get(), componentId, componentVersionOperator, componentVersion,
        operatingSystemId, operatingSystemVersionOperator, operatingSystemVersion, flavor,
        writeDockerfile, dockerfileDiskDirectory);
  }

  public VersionedEntityRequirement getOperatingSystemRequirement() {
    return osRequirement;
  }

  public VersionedEntityRequirement getComponentRequirement() {
    return componentRequirement;
  }

  public String getFlavor() {
    return flavor;
  }

  public boolean getWriteDockerfile() {
    return writeDockerfile;
  }

  public String getDockerfileDiskDirectory() {
    return dockerfileDiskDirectory;
  }

  @Override
  public boolean perform(AbstractBuild build, Launcher launcher,
      BuildListener listener) {
    PrintStream buildLogger = listener == null ? null : listener.getLogger();

    List<VersionedEntityRequirement> components = new ArrayList<>();
    components.add(componentRequirement);

    StackReference stack = apiManager.makeStack(components, osRequirement, flavor, buildLogger);

    if (stack == null) {
      combinedLogger.logWarning(buildLogger,
          "Stacksmith API did not produce a valid stack reference.");
      return false;
    }

    String dockerfileContents = apiManager.fetchDockerfile(stack, buildLogger);
    if (dockerfileContents == null || dockerfileContents.isEmpty()) {
      combinedLogger.logWarning(buildLogger,
          "Stacksmith API produced an empty or missing dockerfile.");
      return false;
    }
    if (writeDockerfile) {
      combinedLogger.logFine(buildLogger,
          "Writing dockerfile to: " + dockerfileDiskDirectory);
      try {
        String fullFolder = build.getEnvironment(listener).expand(
            dockerfileDiskDirectory);
        FilePath folder = new FilePath(new File(fullFolder));
        if (!folder.exists()) {
          folder.mkdirs();
        }
        FilePath dockerfile = folder.child("Dockerfile");
        dockerfile.write(dockerfileContents, null);
      } catch (IOException e) {
        combinedLogger.logWarning(buildLogger, "IOException while writing Dockerfile: " + e);
        return false;
      } catch (InterruptedException e) {
        combinedLogger.logWarning(buildLogger,
            "InterruptedException while writing Dockerfile: " + e);
        return false;
      }
    }
    return true;
  }

  /**
   * Creates a {@link VersionedEntityRequirement} from the given input,
   * with special handling for a null/empty ID and with logging for errors.
   *
   * @return an object that encodes the specified requirements, or a non-requirement placeholder as
   * per {@link VersionedEntityRequirement#getNoRequirement()} if the input cannot be converted
   * into a valid requirement.
   */
  protected static VersionedEntityRequirement requirementFrom(String id,
      String versionOperator, String version) {
    if (id == null || id.isEmpty() || id.equals(NONE_ID)) {
      return VersionedEntityRequirement.getNoRequirement();
    }
    try {
      return VersionedEntityRequirement.builder()
          .setId(id)
          .setVersionOperator(VersionRequirementOperator.get(versionOperator))
          .setVersionNumber(version)
          .build();
    } catch (IllegalStateException e) {
      // If we can't build an instance, return the NONE instance.
      StringBuilder warning = new StringBuilder();
      warning.append("Cannot construct stack component requirement. ID: ");
      warning.append(id);
      warning.append(", version operator: ");
      warning.append(versionOperator == null ? "null" : versionOperator);
      warning.append(", version number: ");
      warning.append(version == null ? "null" : version);
      logger.warning(warning.toString());
      return VersionedEntityRequirement.getNoRequirement();
    }
  }

  /**
   * Descriptor for Stacksmith API Stack Builder step.
   */
  @Extension
  public static class Descriptor extends BuildStepDescriptor<Builder> {
    private static final BitnamiApiManager manager = BitnamiApiManager.get();

    private final Map<String, NavigableSet<VersionedEntity>> dependencies = new HashMap<>();
    private final Map<String, NavigableSet<String>> flavors = new HashMap<>();
    private final Map<String, VersionedEntity> componentsById = new HashMap<>();
    private NavigableSet<VersionedEntity> components = manager.fetchComponents();
    private NavigableSet<VersionedEntity> oses = manager.fetchOperatingSystems();

    /**
     * This constructor is expected to be automatically invoked by the Jenkins framework.
     * When the {@code Descriptor} is created, it immediately attempts to fetch a list of all
     * components from the Bitnami Stacksmith API. This is cached and reused for the lifetime
     * of the {@code Descriptor} - from the user perspective, until the page is re-rendered
     * (usually by navigation or a form action).
     */
    public Descriptor() {
      super();
      if (components != null) {
        for (VersionedEntity entity : components) {
          componentsById.put(entity.getId(), entity);
        }
      }
    }

    /**
     * Always returns true.
     */
    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

    @Override
    public String getDisplayName() {
      return "Build Dockerfile using Stacksmith";
    }

    /**
     * Checks the cache of dependency sets per ID and either retrieves the corresponding set from
     * the cache or fetches the set from the API, caches and returns it. If a component ID
     * is not provided, or if the API call fails, this returns an empty set.
     *
     * TODO(v1.0): hook this up to the UI; this requires a variable number of dynamically filled
     * component fields.
     */
    protected NavigableSet<VersionedEntity> getDependenciesForComponent(String id) {
      // If the input is null or empty, return an empty set.
      // Otherwise, check for already-cached dependencies for this component ID
      // If none exist, try to fetch dependencies for this component ID and
      // cross-reference the IDs with the set of components we know about.
      if (id == null || id.isEmpty() || id.equals(NONE_ID)) {
        return new TreeSet<>();
      } else if (dependencies.containsKey(id)) {
        return dependencies.get(id);
      } else {
        NavigableSet<String> dependencyIds = manager.fetchDependenciesForEntity(id);
        if (dependencyIds == null || dependencyIds.isEmpty()) {
          return new TreeSet<>();
        }
        NavigableSet<VersionedEntity> result = new TreeSet<>();
        for (String dependencyId : dependencyIds) {
          if (componentsById.containsKey(dependencyId)) {
            result.add(componentsById.get(dependencyId));
          }
        }
        dependencies.put(id, result);
        return result;
      }
    }

    /**
     * Checks the cache of flavor strings per ID and either retrieves the corresponding set from
     * the cache or fetches the set from the API, caches and returns it. If a component ID
     * is not provided, or if the API call fails, this returns an empty set.
     */
    protected NavigableSet<String> getFlavorsForComponent(String id) {
      // If the input is null or empty, return an empty set.
      // Otherwise, check for already-cached dependencies for this component ID
      // If none exist, try to fetch dependencies for this component ID and
      // cross-reference the IDs with the set of components we know about.
      if (id == null || id.isEmpty() || id.equals(NONE_ID)) {
        return new TreeSet<>();
      } else if (flavors.containsKey(id)) {
        return flavors.get(id);
      } else {
        NavigableSet<String> result = manager.fetchFlavorsForEntity(id);
        if (result == null) {
          result = new TreeSet<>();
        }
        return result;
      }
    }

    protected ListBoxModel fillEntityItems(
        NavigableSet<VersionedEntity> entities, String currentId) {
      ListBoxModel result = new ListBoxModel();
      result.add(NONE_DISPLAY_TEXT, NONE_ID);
      if (entities == null) {
        return result;
      }
      for (VersionedEntity entity : entities) {
        String entityId = entity.getId();
        result.add(new ListBoxModel.Option(entityId, entityId,
            entityId.equals(currentId)));
      }
      return result;
    }

    /**
     * Helper method used by multiple {@code doFill} methods.
     * @param entities set of entities to display as listbox options.
     * @param entityId ID of the currently selected entity.
     * @param curVersion Version string of the currently selected entity.
     * @return the input data packaged in a {@link ListBoxModel} object.
     */
    protected ListBoxModel fillEntityVersions(
        NavigableSet<VersionedEntity> entities, String entityId,
        String curVersion) {
      ListBoxModel result = new ListBoxModel();
      if (entities == null) {
        return result;
      }
      for (VersionedEntity entity : entities) {
        if (entity.getId().equals(entityId)) {
          for (BranchedVersion version
              : entity.getVersions().descendingSet()) {
            result.add(new ListBoxModel.Option(version.toShortString(),
                version.getVersion(), version.getVersion().equals(curVersion)));
          }
        }
      }
      if (entities.isEmpty()) {
        result.add(NONE_DISPLAY_TEXT, NONE_ID);
      }
      return result;
    }

    /**
     * Helper method used by multiple {@code doFill} methods.
     * @param current currently selected version requirement operator.
     * @return A {@link ListBoxModel} object filled with all possible version requirement
     *   operators, with the specified current selection.
     */
    protected ListBoxModel fillVersionOperator(String current) {
      ListBoxModel result = new ListBoxModel();
      for (VersionRequirementOperator operator
          : VersionRequirementOperator.values()) {
        result.add(new ListBoxModel.Option(operator.getApiString(),
            operator.getApiString(), operator.getApiString().equals(current)));
      }
      return result;
    }

    /**
     * Automatically invoked by the Jenkins UI framework to fill a listbox.
     */
    public ListBoxModel doFillOperatingSystemIdItems(
        @QueryParameter String curOperatingSystemId) {
      return fillEntityItems(oses, curOperatingSystemId);
    }

    /**
     * Automatically invoked by the Jenkins UI framework to fill a listbox.
     */
    public ListBoxModel doFillComponentIdItems(
        @QueryParameter String curComponentId) {
      return fillEntityItems(components, curComponentId);
    }

    /**
     * Automatically invoked by the Jenkins UI framework to fill a listbox.
     */
    public ListBoxModel doFillOperatingSystemVersionItems(
        @QueryParameter String operatingSystemId,
        @QueryParameter String curOperatingSystemVersion) {
      return fillEntityVersions(oses, operatingSystemId,
          curOperatingSystemVersion);
    }

    /**
     * Automatically invoked by the Jenkins UI framework to fill a listbox.
     */
    public ListBoxModel doFillComponentVersionItems(
        @QueryParameter String componentId,
        @QueryParameter String curComponentVersion) {
      return fillEntityVersions(components, componentId, curComponentVersion);
    }

    /**
     * Automatically invoked by the Jenkins UI framework to fill a listbox.
     */
    public ListBoxModel doFillOperatingSystemVersionOperatorItems(
        @QueryParameter String curOperatingSystemVersionOperator) {
      return fillVersionOperator(curOperatingSystemVersionOperator);
    }

    /**
     * Automatically invoked by the Jenkins UI framework to fill a listbox.
     */
    public ListBoxModel doFillComponentVersionOperatorItems(
        @QueryParameter String curComponentVersionOperator) {
      return fillVersionOperator(curComponentVersionOperator);
    }

    /**
     * Automatically invoked by the Jenkins UI framework to fill a listbox.
     */
    public ListBoxModel doFillFlavorItems(
        @QueryParameter String curFlavor,
        @QueryParameter String componentId) {
      ListBoxModel result = new ListBoxModel();
      for (String flavor : getFlavorsForComponent(componentId)) {
        result.add(new ListBoxModel.Option(flavor, flavor, flavor.equals(curFlavor)));
      }
      return result;
    }
  }
}
