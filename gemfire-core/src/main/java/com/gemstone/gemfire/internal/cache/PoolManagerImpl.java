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

package com.gemstone.gemfire.internal.cache;

import java.util.Collections;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.gemstone.gemfire.DataSerializer;
import com.gemstone.gemfire.Instantiator;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.client.Pool;
import com.gemstone.gemfire.cache.client.PoolFactory;
import com.gemstone.gemfire.cache.client.PoolManager;
import com.gemstone.gemfire.cache.client.internal.BridgePoolImpl;
import com.gemstone.gemfire.cache.client.internal.PoolImpl;
import com.gemstone.gemfire.cache.client.internal.RegisterDataSerializersOp;
import com.gemstone.gemfire.cache.client.internal.RegisterInstantiatorsOp;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.internal.InternalDataSerializer;
import com.gemstone.gemfire.internal.InternalDataSerializer.SerializerAttributesHolder;
import com.gemstone.gemfire.internal.InternalInstantiator;
import com.gemstone.gemfire.internal.InternalInstantiator.InstantiatorAttributesHolder;
import com.gemstone.gemfire.internal.cache.xmlcache.CacheCreation;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;

/**
 * Implementation used by PoolManager.
 *
 * @author darrel
 * @since 5.7
 *
 */
public class PoolManagerImpl {
  private final static PoolManagerImpl impl = new PoolManagerImpl(true);
  
  public static PoolManagerImpl getPMI() {
    PoolManagerImpl result = CacheCreation.getCurrentPoolManager();
    if (result == null) {
      result = impl;
    }
    return result;
  }
  
  private volatile Map<String,Pool> pools = Collections.emptyMap();
  private volatile Iterator<Map.Entry<String,Pool>> itrForEmergencyClose = null;
  private final Object poolLock = new Object();
  /**
   * True if this manager is a normal one owned by the PoolManager.
   * False if this is a special one owned by a xml CacheCreation.
   */
  private final boolean normalManager;

  /**
   * @param addListener will be true if the is a real manager that needs to
   *                    register a connect listener. False if it is a fake
   *                    manager used internally by the XML code.
   */
  public PoolManagerImpl(boolean addListener) {
    this.normalManager = addListener;
  }

  /**
   * Returns true if this is a normal manager; false if it is a fake one used
   * for xml parsing.
   */
  public boolean isNormal() {
    return this.normalManager;
  }
  
  /**
   * Creates a new {@link PoolFactory pool factory},
   * which is used to configure and create new {@link Pool}s.
   * @return the new pool factory
   */
  public PoolFactory createFactory() {
    return new PoolFactoryImpl(this);
  }
  /**
   * Find by name an existing connection pool returning
   * the existing pool or <code>null</code> if it does not exist.
   * @param name the name of the connection pool
   * @return the existing connection pool or <code>null</code> if it does not exist.
   */
  public Pool find(String name) {
    return this.pools.get(name);
  }
  
  /**
   * Set the keep alive flag before closing. Only for use with the deprecated
   * BridgeWriter/Loader code. A BridgeWriter is automatically
   * closed then the last region is disconnected from it,
   * so we need to mark the connections as keep alive
   * before we close the regions that use the bridge writer/loader
   * 
   * @param keepAlive
   */
  public static void setKeepAlive(boolean keepAlive) {
    for(Iterator<Pool> itr = PoolManager.getAll().values().iterator(); itr.hasNext(); ) {
      Pool nextPool = itr.next();
      if(nextPool instanceof BridgePoolImpl) {
        BridgePoolImpl bridgePool = (BridgePoolImpl) nextPool;
        bridgePool.setKeepAlive(keepAlive);
      }
    }
  }
  /**
   * Destroys all created pool in this manager.
   */
  public void close(boolean keepAlive) {
    // destroying connection pools
    synchronized(poolLock) {
      for (Iterator<Map.Entry<String,Pool>> itr = pools.entrySet().iterator(); itr.hasNext(); ) {
        Map.Entry<String,Pool> entry = itr.next();
        PoolImpl pool = (PoolImpl)entry.getValue();
        pool.basicDestroy(keepAlive);
      }
      pools = Collections.emptyMap();
      itrForEmergencyClose = null;
    }
  }
  
  /**
   * @return a copy of the Pools Map
   */
  public Map<String,Pool> getMap() {
    //debugStack("getMap: " + this.pools);
    return new HashMap<String,Pool>(this.pools);
  }
  /**
   * This is called by {@link PoolImpl#create}
   * @throws IllegalStateException if a pool with same name is already registered.
   */
  public void register(Pool pool) {
    synchronized (this.poolLock) {
      Map<String,Pool> copy = new HashMap<String,Pool>(pools);
      String name = pool.getName();
      //debugStack("register pool=" + name);
      Object old = copy.put(name, pool);
      if (old != null) {
        throw new IllegalStateException("A pool named \"" + name
                                        + "\" already exists.");
      }
      this.pools = Collections.unmodifiableMap(copy);
      this.itrForEmergencyClose = copy.entrySet().iterator();
    }
  }

  /**
   * This is called by {@link Pool#destroy(boolean)}
   * @return true if pool unregistered from cache; false if someone else already did it
   */
  public boolean unregister(Pool pool) {
    synchronized(this.poolLock) {
      Map<String,Pool> copy = new HashMap<String,Pool>(pools);
      String name = pool.getName();
      //debugStack("unregister pool=" + name);
      Object rmPool = copy.remove(name);
      if (rmPool == null || rmPool != pool) {
        return false;
      } else {
        this.pools = Collections.unmodifiableMap(copy);
        this.itrForEmergencyClose = copy.entrySet().iterator();
        return true;
      }
    }
  }
  
  @Override
  public String toString() {
    StringBuffer result = new StringBuffer();
    result.append(super.toString())
      .append("-")
      .append(this.normalManager? "normal":"xml");
    return result.toString();
  }

  /**
   * @param xmlPoolsOnly if true then only call readyForEvents on pools declared in XML.
   */
  public static void readyForEvents(InternalDistributedSystem system, boolean xmlPoolsOnly) {
    boolean foundDurablePool = false;
    Map<String,Pool> pools = PoolManager.getAll();
    for(Iterator<Pool> itr = pools.values().iterator(); itr.hasNext(); ) {
      PoolImpl p = (PoolImpl) itr.next();
      if (p.isDurableClient()) {
        //TODO - handle an exception and attempt on all pools?
        foundDurablePool = true;
        if (!xmlPoolsOnly || p.getDeclaredInXML()) {
          p.readyForEvents(system);
        }
      }
    }
    if (pools.size() > 0 && !foundDurablePool) {
      throw new IllegalStateException(LocalizedStrings.PoolManagerImpl_ONLY_DURABLE_CLIENTS_SHOULD_CALL_READYFOREVENTS.toLocalizedString());
    }
  }

  /**
   * @param instantiator
   */
  public static void allPoolsRegisterInstantiator(Instantiator instantiator) {
    Instantiator[] instantiators = new Instantiator[1];
    instantiators[0] = instantiator;
    for(Iterator<Pool> itr = PoolManager.getAll().values().iterator(); itr.hasNext(); ) {
      PoolImpl next = (PoolImpl) itr.next();
      try {
        EventID eventId = InternalInstantiator.generateEventId();
        if(eventId == null) {
          //cache must not exist, do nothing
        } else {
          RegisterInstantiatorsOp.execute(next, instantiators, InternalInstantiator.generateEventId());
        }
      } catch(RuntimeException e) {
        next.getLoggerI18n().warning(LocalizedStrings.PoolmanagerImpl_ERROR_REGISTERING_INSTANTIATOR_ON_POOL, e);
      } finally {
        next.releaseThreadLocalConnection();
      }
    }
  }
  
  public static void allPoolsRegisterInstantiator(InstantiatorAttributesHolder holder) {
    InstantiatorAttributesHolder[] holders = new InstantiatorAttributesHolder[1];
    holders[0] = holder;
    for(Iterator<Pool> itr = PoolManager.getAll().values().iterator(); itr.hasNext(); ) {
      PoolImpl next = (PoolImpl) itr.next();
      try {
        EventID eventId = InternalInstantiator.generateEventId();
        if(eventId == null) {
          //cache must not exist, do nothing
        } else {
          RegisterInstantiatorsOp.execute(next, holders, InternalInstantiator.generateEventId());
        }
      } catch(RuntimeException e) {
        next.getLoggerI18n().warning(LocalizedStrings.PoolmanagerImpl_ERROR_REGISTERING_INSTANTIATOR_ON_POOL, e);
      } finally {
        next.releaseThreadLocalConnection();
      }
    }
  }
  
  public static void allPoolsRegisterDataSerializers(
      DataSerializer dataSerializer) {
    DataSerializer[] dataSerializers = new DataSerializer[1];
    dataSerializers[0] = dataSerializer;
    for (Iterator<Pool> itr = PoolManager.getAll().values().iterator(); itr
        .hasNext();) {
      PoolImpl next = (PoolImpl)itr.next();
      try {
        EventID eventId = (EventID)dataSerializer.getEventId();
        if (eventId == null) {
          eventId = InternalDataSerializer.generateEventId();
        }
        if (eventId == null) {
          // cache must not exist, do nothing
        }
        else {
          RegisterDataSerializersOp.execute(next, dataSerializers, eventId);
        }
      }
      catch (RuntimeException e) {
        next
            .getLoggerI18n()
            .warning(
                LocalizedStrings.PoolmanagerImpl_ERROR_REGISTERING_INSTANTIATOR_ON_POOL,
                e);
      }
      finally {
        next.releaseThreadLocalConnection();
      }
    }
  }

  public static void allPoolsRegisterDataSerializers(
      SerializerAttributesHolder holder) {
    SerializerAttributesHolder[] holders = new SerializerAttributesHolder[1];
    holders[0] = holder;
    for (Iterator<Pool> itr = PoolManager.getAll().values().iterator(); itr
        .hasNext();) {
      PoolImpl next = (PoolImpl)itr.next();
      try {
        EventID eventId = (EventID)holder.getEventId();
        if (eventId == null) {
          eventId = InternalDataSerializer.generateEventId();
        }
        if (eventId == null) {
          // cache must not exist, do nothing
        } else {
          RegisterDataSerializersOp.execute(next, holders, eventId);
        }
      } catch (RuntimeException e) {
        next.getLoggerI18n().warning(
            LocalizedStrings.PoolmanagerImpl_ERROR_REGISTERING_INSTANTIATOR_ON_POOL,
            e);
      } finally {
        next.releaseThreadLocalConnection();
      }
    }
  }

  public static void emergencyClose() {
    if(impl == null) {
      return;
    }
    Iterator<Map.Entry<String,Pool>> itr= impl.itrForEmergencyClose;
    if(itr == null) {
      return;
    }
    while(itr.hasNext()) {
      Entry<String, Pool> next = itr.next();
      ((PoolImpl) next.getValue()).emergencyClose();
    }
  }

  public static void loadEmergencyClasses() {
    PoolImpl.loadEmergencyClasses();
  }

  public Pool find(Region<?,?> region) {
    return find(region.getAttributes().getPoolName());
  }
}
