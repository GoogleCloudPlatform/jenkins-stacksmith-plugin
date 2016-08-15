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

import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;

/**
 * Logger class that wraps a {@link Logger} object and an optional
 * {@link PrintStream} object, allowing logs to be written both to standard
 * Java logging and to a Jenkins build log.
 */
public class CombinedLogger {

  private final Logger logger;

  /**
   * @param logger Standard Java logger to wrap. Must not be null.
   */
  public CombinedLogger(Logger logger) {
    this.logger = Preconditions.checkNotNull(logger);
  }

  /**
   * Logs the given message to the provided {@link PrintStream} if it is not
   * null; also always logs to the standard Java log, at the specified level.
   * @param buildLogger if not null, a print stream that will receive the
   * specified message.
   * @param message log message to write. Must not be null.
   * @param logLevel Java logging level to use. Must not be null.
   */
  public void log(@Nullable PrintStream buildLogger, String message, Level logLevel) {
    Preconditions.checkNotNull(message);
    Preconditions.checkNotNull(logLevel);
    logger.log(logLevel, message);
    if (buildLogger != null) {
      buildLogger.println(message);
    }
  }

  /**
   * Convenience method equivalent to {@link #log(PrintStream, String, Level)}
   * using the {@link Level.FINEST} level.
   */
  public void logFinest(@Nullable PrintStream buildLogger,
      String message) {
    log(buildLogger, message, Level.FINEST);
  }

  /**
   * Convenience method equivalent to {@link #log(PrintStream, String, Level)}
   * using the {@link Level.FINER} level.
   */
  public void logFiner(@Nullable PrintStream buildLogger,
      String message) {
    log(buildLogger, message, Level.FINER);
  }

  /**
   * Convenience method equivalent to {@link #log(PrintStream, String, Level)}
   * using the {@link Level.FINE} level.
   */
  public void logFine(@Nullable PrintStream buildLogger,
      String message) {
    log(buildLogger, message, Level.FINE);
  }

  /**
   * Convenience method equivalent to {@link #log(PrintStream, String, Level)}
   * using the {@link Level.CONFIG} level.
   */
  public void logConfig(@Nullable PrintStream buildLogger,
      String message) {
    log(buildLogger, message, Level.CONFIG);
  }

  /**
   * Convenience method equivalent to {@link #log(PrintStream, String, Level)}
   * using the {@link Level.INFO} level.
   */
  public void logInfo(@Nullable PrintStream buildLogger,
      String message) {
    log(buildLogger, message, Level.INFO);
  }

  /**
   * Convenience method equivalent to {@link #log(PrintStream, String, Level)}
   * using the {@link Level.WARNING} level.
   */
  public void logWarning(@Nullable PrintStream buildLogger,
      String message) {
    log(buildLogger, message, Level.WARNING);
  }

  /**
   * Convenience method equivalent to {@link #log(PrintStream, String, Level)}
   * using the {@link Level.SEVERE} level.
   */
  public void logSevere(@Nullable PrintStream buildLogger,
      String message) {
    log(buildLogger, message, Level.SEVERE);
  }
}
