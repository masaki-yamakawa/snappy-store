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
package com.gemstone.gemfire.cache30;

import java.util.Properties;

import com.gemstone.gemfire.distributed.internal.DistributionConfig;
import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;
import com.gemstone.gemfire.internal.cache.control.InternalResourceManager.ResourceType;
import com.gemstone.gemfire.internal.cache.lru.HeapEvictor;

/**
 * Tests the basic functionality of the lru eviction 
 * controller and its statistics using off-heap regions.
 * 
 * @author rholmes
 * @since 7.5
 */
public class OffHeapLRUEvictionControllerDUnitTest extends
    LRUEvictionControllerDUnitTest {

  public OffHeapLRUEvictionControllerDUnitTest(String name) {
    super(name);
  }

  @Override
  public Properties getDistributedSystemProperties() {
    Properties properties = super.getDistributedSystemProperties();    
    properties.setProperty(DistributionConfig.OFF_HEAP_MEMORY_SIZE_NAME, "100m");    
    
    return properties;
  }  
  
  @Override
  protected boolean isOffHeapEnabled() {
    return true;
  }
  
  @Override
  protected HeapEvictor getEvictor() {
    return ((GemFireCacheImpl) getCache()).getOffHeapEvictor();
  }
  
  @Override
  protected ResourceType getResourceType() {
    return ResourceType.OFFHEAP_MEMORY;
  }
}
