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

package com.gemstone.gemfire.internal.util;

import com.gemstone.gemfire.i18n.LogWriterI18n;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 *
 * @author Eric Zoerner
 *
 */
public abstract class DebuggerSupport  {
  
  /** Creates a new instance of DebuggerSupport */
  private DebuggerSupport() {
  }
  
  /** Debugger support */
  public static void waitForJavaDebugger(LogWriterI18n logger) {
    waitForJavaDebugger(logger, null);
  }
  
  @SuppressFBWarnings(value="IL_INFINITE_LOOP", justification="Endless loop is for debugging purposes.") 
  public static void waitForJavaDebugger(LogWriterI18n logger, String extraLogMsg) {
    boolean cont = false;
    String msg = ":";
    if (extraLogMsg != null)
      msg += extraLogMsg;
    logger.severe(LocalizedStrings.DebuggerSupport_WAITING_FOR_DEBUGGER_TO_ATTACH_0, msg);
    boolean interrupted = false;
    while (!cont) { // set cont to true in debugger when ready to continue
      try {
        // SET BREAKPOINT HERE
        Thread.sleep(1000);
      }
      catch (InterruptedException e) {
        interrupted = true;
        // ...but keep going, waiting for debugger
      }
    } // while
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
    logger.info(LocalizedStrings.DebuggerSupport_DEBUGGER_CONTINUING);
  }
}
