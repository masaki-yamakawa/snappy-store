/*
 * Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package com.gemstone.gemfire.cache.client;


/**
 * Indicates that the connection pool is at its maximum size and
 * all connections are in use.
 * @author dsmith
 * @since 5.7
 */
public class AllConnectionsInUseException extends ServerConnectivityException {

  private static final long serialVersionUID = 7304243507881787071L;

  /**
   * Create a new instance of AllConnectionsInUseException without a detail message or cause.
   */
  public AllConnectionsInUseException() {
  }

  /**
   * Create a new instance of AllConnectionsInUseException with a detail message
   * @param message the detail message
   */
  public AllConnectionsInUseException(String message) {
    super(message);
  }
  
  /**
   * Create a new instance of AllConnectionsInUseException with a cause
   * @param cause the cause
   */
  public AllConnectionsInUseException(Throwable cause) {
    super(cause);
  }
  
  /**
   * Create a new instance of AllConnectionsInUseException with a detail message and cause
   * @param message the detail message
   * @param cause the cause
   */
  public AllConnectionsInUseException(String message, Throwable cause) {
    super(message, cause);
  }

}
