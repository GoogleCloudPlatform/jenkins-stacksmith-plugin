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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

/**
 * Tests for {@link StacksmithBuilder}.
 */
@RunWith(MockitoJUnitRunner.class)
public class StacksmithBuilderTest {
  private static final String COMPONENT_ID_1 = "componentId1";
  private static final String OS_ID_1 = "osId1";
  private static final String VERSION_1_0 = "1.0";
  private static final String VERSION_2_0 = "2.0";
  private static final String VERSION_3_0 = "3.0";
  private static final String DOCKERFILE_LOCATION = "/tmp/dockerdir/";
  private static final String DOCKERFILE_CONTENTS = "#This is an example dockerfile.";
  private static final String STACK_ID_1 = "stackid1";
  private static final String FLAVOR_1 = "flavor1";
  private static final String STACK_URL_1 = "https://example.com/stackurl1";
  @Mock private BitnamiApiManager mockApiManager;
  @Mock private AbstractBuild mockBuild;
  @Mock private Launcher mockLauncher;
  @Mock private BuildListener mockListener;

  @Test
  public void gettersShouldWorkAsExpected() {
    StacksmithBuilder underTest = new StacksmithBuilder(
        COMPONENT_ID_1, ">", VERSION_1_0,
        OS_ID_1, "<", VERSION_3_0,
        FLAVOR_1,
        true, DOCKERFILE_LOCATION);

    VersionedEntityRequirement req = underTest.getComponentRequirement();
    assertEquals("Component requirement ID should match constructor parameter.",
        COMPONENT_ID_1, req.getId());
    assertEquals("Component version operator should match constructor parameter.",
        VersionRequirementOperator.GT, req.getVersionOperator());
    assertEquals("Component version number should match constructor parameter.",
        VERSION_1_0, req.getVersionNumber());

    req = underTest.getOperatingSystemRequirement();
    assertEquals("OS requirement ID should match constructor parameter.",
        OS_ID_1, req.getId());
    assertEquals("OS version operator should match constructor parameter.",
        VersionRequirementOperator.LT, req.getVersionOperator());
    assertEquals("OS version number should match constructor parameter.",
        VERSION_3_0, req.getVersionNumber());

    assertEquals("Flavor should match constructor parameter.",
        FLAVOR_1, underTest.getFlavor());

    assertTrue("Write-dockerfile flag should match constructor parameter.",
        underTest.getWriteDockerfile());
    assertEquals("Dockerfile location should match constructor parameter.",
        DOCKERFILE_LOCATION, underTest.getDockerfileDiskDirectory());
  }

  @Test
  public void requirementFromShouldWorkAsExpected() {
    VersionedEntityRequirement req = StacksmithBuilder.requirementFrom(OS_ID_1, "<", "2.5");
    assertNotNull("requirementFrom should never return null.", req);
    assertEquals("requirementFrom should preserve input ID.", OS_ID_1, req.getId());
    assertEquals("requirementFrom should parse version requirement operator.",
        VersionRequirementOperator.LT, req.getVersionOperator());
    assertEquals("requirementFrom should parse version number.", "2.5", req.getVersionNumber());

    req = StacksmithBuilder.requirementFrom(OS_ID_1, "latest", "");
    assertNotNull("requirementFrom should never return null.", req);
    assertEquals("requirementFrom should preserve input ID.", OS_ID_1, req.getId());
    assertEquals("requirementFrom should parse version requirement operator.",
        VersionRequirementOperator.LATEST, req.getVersionOperator());
    assertEquals("requirementFrom should preserve empty version number.", "",
        req.getVersionNumber());
  }

  @Test
  public void requirementFromShouldReturnNoRequirementForEmptyInput() {
    VersionedEntityRequirement req = StacksmithBuilder.requirementFrom("", "", "");
    assertNotNull("requirementFrom should never return null.", req);
    assertTrue("requirementFrom should produce NONE requirement for empty input.", req.isNone());
  }

  @Test
  public void requirementFromShouldReturnNoRequirementForBadInput() {
    VersionedEntityRequirement req = StacksmithBuilder.requirementFrom(OS_ID_1, null, null);
    assertNotNull("requirementFrom should never return null.", req);
    assertTrue("requirementFrom should produce NONE requirement for invalid input.", req.isNone());
  }

  @Test
  public void performShouldInvokeBitnamiAPI() {
    StacksmithBuilder underTest = new StacksmithBuilder(
        mockApiManager,
        COMPONENT_ID_1, ">", VERSION_1_0,
        OS_ID_1, "<", VERSION_3_0,
        FLAVOR_1,
        false, DOCKERFILE_LOCATION);

    VersionedEntityRequirement expectedComponent = VersionedEntityRequirement.builder()
        .setId(COMPONENT_ID_1).setVersionOperator(VersionRequirementOperator.GT)
        .setVersionNumber(VERSION_1_0).build();
    List<VersionedEntityRequirement> expectedComponents = new ArrayList<>();
    expectedComponents.add(expectedComponent);

    VersionedEntityRequirement expectedOS = VersionedEntityRequirement.builder()
        .setId(OS_ID_1).setVersionOperator(VersionRequirementOperator.LT)
        .setVersionNumber(VERSION_3_0).build();

    StackReference stackReference = StackReference.builder()
        .setId(STACK_ID_1).setStackUrl(STACK_URL_1).build();

    when(mockApiManager.makeStack(expectedComponents, expectedOS, FLAVOR_1, null))
        .thenReturn(stackReference);
    when(mockApiManager.fetchDockerfile(stackReference, null)).thenReturn(DOCKERFILE_CONTENTS);

    boolean result = underTest.perform(mockBuild, mockLauncher, mockListener);

    assertTrue("Correct API response should produce successful result.", result);

    verify(mockApiManager).makeStack(expectedComponents, expectedOS, FLAVOR_1, null);
    verify(mockApiManager).fetchDockerfile(stackReference, null);
  }

  @Test
  public void performShouldFailOnEmptyDockerfile() {
    StacksmithBuilder underTest = new StacksmithBuilder(
        mockApiManager,
        COMPONENT_ID_1, ">", VERSION_1_0,
        OS_ID_1, "<", VERSION_3_0,
        FLAVOR_1,
        false, DOCKERFILE_LOCATION);

    VersionedEntityRequirement expectedComponent = VersionedEntityRequirement.builder()
        .setId(COMPONENT_ID_1).setVersionOperator(VersionRequirementOperator.GT)
        .setVersionNumber(VERSION_1_0).build();
    List<VersionedEntityRequirement> expectedComponents = new ArrayList<>();
    expectedComponents.add(expectedComponent);

    VersionedEntityRequirement expectedOS = VersionedEntityRequirement.builder()
        .setId(OS_ID_1).setVersionOperator(VersionRequirementOperator.LT)
        .setVersionNumber(VERSION_3_0).build();

    StackReference stackReference = StackReference.builder()
        .setId(STACK_ID_1).setStackUrl(STACK_URL_1).build();

    when(mockApiManager.makeStack(expectedComponents, expectedOS, FLAVOR_1, null))
        .thenReturn(stackReference);
    when(mockApiManager.fetchDockerfile(stackReference, null)).thenReturn("");

    boolean result = underTest.perform(mockBuild, mockLauncher, mockListener);

    assertFalse("Empty Dockerfile should be considered a failure.", result);

    verify(mockApiManager).makeStack(expectedComponents, expectedOS, FLAVOR_1, null);
    verify(mockApiManager).fetchDockerfile(stackReference, null);
  }

  @Test
  public void performShouldFailOnAPIError() {
    StacksmithBuilder underTest = new StacksmithBuilder(
        mockApiManager,
        COMPONENT_ID_1, ">", VERSION_1_0,
        OS_ID_1, "<", VERSION_3_0,
        FLAVOR_1,
        false, DOCKERFILE_LOCATION);

    VersionedEntityRequirement expectedComponent = VersionedEntityRequirement.builder()
        .setId(COMPONENT_ID_1).setVersionOperator(VersionRequirementOperator.GT)
        .setVersionNumber(VERSION_1_0).build();
    List<VersionedEntityRequirement> expectedComponents = new ArrayList<>();
    expectedComponents.add(expectedComponent);

    VersionedEntityRequirement expectedOS = VersionedEntityRequirement.builder()
        .setId(OS_ID_1).setVersionOperator(VersionRequirementOperator.LT)
        .setVersionNumber(VERSION_3_0).build();

    StackReference stackReference = StackReference.builder()
        .setId(STACK_ID_1).setStackUrl(STACK_URL_1).build();

    when(mockApiManager.makeStack(expectedComponents, expectedOS, FLAVOR_1, null))
        .thenReturn(null);

    boolean result = underTest.perform(mockBuild, mockLauncher, mockListener);

    assertFalse("API error should be considered a failure.", result);

    verify(mockApiManager).makeStack(expectedComponents, expectedOS, FLAVOR_1, null);
  }
}
