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

import java.util.Comparator;
import java.util.Iterator;

import com.google.common.base.Splitter;

/**
 * Utility class to compare version strings.
 * <p>
 * Attempts to split the version string into major/minor/etc. versions, and
 * compares in order of major version, then minor version, then sub-minor, etc.
 * Numeric version sections are compared as integers; non-numeric version
 * sections are compared lexicographically. Numeric version sections are always
 * considered smaller than non-numeric version sections. A string with fewer
 * versions specified is considered smaller than a string with more versions
 * specified if the two are otherwise equal.
 * <p>
 * Examples:
 * {@code 1.1 < 1.1.2 < 1.2.1 < 1.2.2 < 1.5 < 1.12 < 1.13 < 1.1-beta < 1.1beta }
 */
public class VersionComparator implements Comparator<String> {
  private static final VersionComparator COMPARATOR = new VersionComparator();
  private static final Splitter SPLITTER = Splitter.on('.');

  private VersionComparator() {}

  /**
   * Returns the singleton instance.
   */
  public static VersionComparator get() {
    return COMPARATOR;
  }

  public int compare(String o1, String o2) {
    if (o1 == null || o2 == null) {
      throw new NullPointerException();
    }
    Iterator<String> firstIter = SPLITTER.split(o1).iterator();
    Iterator<String> secondIter = SPLITTER.split(o2).iterator();
    // Step through version sections pairwise.
    while (firstIter.hasNext() && secondIter.hasNext()) {
      int comparisonResult = 0;
      String firstSection = firstIter.next();
      String secondSection = secondIter.next();
      Integer firstInteger = null;
      Integer secondInteger = null;
      try {
        firstInteger = Integer.valueOf(firstSection);
      } catch (NumberFormatException e) {
        // No need to do anything - a null value signals this state.
      }
      try {
        secondInteger = Integer.valueOf(secondSection);
      } catch (NumberFormatException e) {
        // No need to do anything - a null value signals this state.
      }
      if (firstInteger != null && secondInteger != null) {
        // Both sections are numeric.
        comparisonResult = firstInteger.compareTo(secondInteger);
      } else if (firstInteger != null) {
        // First section is numeric, second is not; numeric always smaller.
        comparisonResult = -1;
      } else if (secondInteger != null) {
        // Second section is numeric, first is not; numeric always smaller.
        comparisonResult = 1;
      } else {
        // Both sections are non-numeric, use string comparison.
        comparisonResult = firstSection.compareTo(secondSection);
      }

      // Terminate if a difference is found, otherwise proceed to the next
      // version section.
      if (comparisonResult != 0) {
        return comparisonResult;
      }
    }
    // At least one version string has terminated.
    if (firstIter.hasNext()) {
      // Second string has more sub-version information.
      return 1;
    }
    if (secondIter.hasNext()) {
      // First string has more sub-version information.
      return -1;
    }
    // Both version strings are identical.
    return 0;
  }
}
