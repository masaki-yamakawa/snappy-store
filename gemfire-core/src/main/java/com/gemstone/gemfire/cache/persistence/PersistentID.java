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
package com.gemstone.gemfire.cache.persistence;

import java.net.InetAddress;
import java.util.UUID;

import com.gemstone.gemfire.DataSerializable;
import com.gemstone.gemfire.admin.AdminDistributedSystem;
import com.gemstone.gemfire.cache.DataPolicy;

/**
 * A pattern describing a single member's a set of persistent files for a region.
 * When a member has a region defined with the a data policy of
 * {@link DataPolicy#PERSISTENT_REPLICATE}, that members persistent files are
 * assigned a unique ID. After a failure of all members, during recovery, the
 * persistent members will wait for all persistent copies of the region to be
 * recovered before completing region initialization.
 * 
 * This pattern describes what unique ids the currently recovering persistent
 * members are waiting for. See
 * {@link AdminDistributedSystem#getMissingPersistentMembers()}
 * 
 * @author dsmith
 * @since 6.5
 *
 */
public interface PersistentID extends DataSerializable {

  /**
   * The host on which the persistent data was last residing
   */
  public abstract InetAddress getHost();

  /**
   * The directory which the persistent data was last residing in.
   */
  public abstract String getDirectory();
  
  /**
   * The unique identifier for the persistent data.
   * @since 7.0
   */
  public abstract UUID getUUID();
}