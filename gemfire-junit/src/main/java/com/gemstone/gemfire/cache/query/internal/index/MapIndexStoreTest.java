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
package com.gemstone.gemfire.cache.query.internal.index;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import junit.framework.TestCase;

import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.query.CacheUtils;
import com.gemstone.gemfire.cache.query.data.Portfolio;
import com.gemstone.gemfire.cache.query.internal.index.IndexStore.IndexStoreEntry;
import com.gemstone.gemfire.internal.cache.LocalRegion;
import com.gemstone.gemfire.internal.cache.RegionEntry;
import com.gemstone.gemfire.internal.cache.RegionEntryContext;
import com.gemstone.gemfire.internal.cache.VMThinRegionEntryHeap;
import com.gemstone.gemfire.internal.CloseableIterator;

/**
 * Test class that will be extended to provide the IndexStorage structure to test
 * Tests apis of the IndexStorage
 * @author jhuynh
 *
 */
public class MapIndexStoreTest extends TestCase {

  IndexStore indexDataStructure;
  List<IndexStoreEntry> entries;
  LocalRegion region;

  int numValues = 10;

  public MapIndexStoreTest(String testName) {
    super(testName);
  }
  
  public void setUp() throws Exception {
    super.setUp();
    entries = new ArrayList<IndexStoreEntry>();
    this.indexDataStructure = getIndexStorage();
  }
  
  public void tearDown() throws Exception {
    if (region != null) {
      region.destroyRegion();
    }
    CacheUtils.closeCache();
    super.tearDown();
  }

  protected IndexStore getIndexStorage() {
    CacheUtils.startCache();
    Cache cache = CacheUtils.getCache();
    AttributesFactory attributesFactory = new AttributesFactory();
    attributesFactory.setDataPolicy(DataPolicy.NORMAL);
    attributesFactory.setIndexMaintenanceSynchronous(true);
    RegionAttributes regionAttributes = attributesFactory.create();
    region = (LocalRegion) cache.createRegion("portfolios",
        regionAttributes);

    IndexStore indexStorage = new MapIndexStore(region.getIndexMap(
        "testIndex", "p.ID", "/portfolios p"), region);
    return indexStorage;
  }

  // ******** HELPERS ********/
  /**
   * adds values to the index storage and to a list for validation
   */
  private void addValues(Region region, int numValues) throws IMQException {
    for (int i = 0; i < numValues; i++) {
      String regionKey = "" + i;
      RegionEntry re = VMThinRegionEntryHeap.getEntryFactory().createEntry((RegionEntryContext)
          region, regionKey, new Portfolio(i));
      entries.add(i, new IndexRegionTestEntry(re));
      indexDataStructure.addMapping(regionKey, re);
    }
  }

  /**
   * checks the list for an matching IndexEntry
   */
  private boolean entriesContains(IndexStoreEntry ie) {
    Iterator<IndexStoreEntry> iterator = entries.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().equals(ie)) {
        return true;
      }
    }
    return false;
  }

  /**
   * iterates through the index storage structure and compares size
   * with the test list
   */
  private void validateIndexStorage() {
    CloseableIterator<IndexStoreEntry> iterator = null;
    try {
      ArrayList structureList = new ArrayList();
      iterator = indexDataStructure.iterator(null);
      while (iterator.hasNext()) {
        IndexStoreEntry ie = iterator.next();
        if (entriesContains(ie)) {
          structureList.add(ie);
        } else {
          fail("IndexDataStructure returned an IndexEntry that should not be present:"
              + ie);
        }
      }
      assertEquals("Expected Number of entries did not match", entries.size(),
          structureList.size());
    } finally {
      if (iterator != null) {
        iterator.close();
      }
    }
  }

  /**
   * iterates through and checks against expected size
   */
  private void validateIteratorSize(CloseableIterator iterator, int expectedSize) {
    try {
      int actualSize = 0;
      while (iterator.hasNext()) {
        iterator.next();
        actualSize++;
      }
      assertEquals("Iterator provided differing number of values",
          expectedSize, actualSize);
    } finally {
      if (iterator != null) {
        iterator.close();
      }
    }
  }
  
  private void validateDescendingIterator(CloseableIterator iterator, int reverseStart, int reverseEnd) {
    for (int i = reverseStart; i > reverseEnd; i--) {
      IndexStoreEntry ise = (IndexStore.IndexStoreEntry)iterator.next();
      if (Integer.valueOf((String)ise.getDeserializedKey()) != i) {
        fail("descendingIterator did not return the expected reverse order");
      }
    }
  }

  /**
   * Helper method to test index storage iterators
   */ 
  public void helpTestStartAndEndIterator(Region region, Object startValue,
      boolean startInclusive, Object endValue, boolean endInclusive,
      int expectedSize) throws IMQException {
    addValues(region, numValues);
    CloseableIterator<IndexStoreEntry> iterator = indexDataStructure
        .iterator(startValue, startInclusive, endValue, endInclusive, null);
    validateIteratorSize(iterator, expectedSize);
  }

  // ******** TESTS ********/
  /**
   * this test adds values to the index storage and validates the index storage
   * against the test list
   */
  public void testAddMapping() throws IMQException {
    addValues(region, numValues);
    validateIndexStorage();
  }

  /**
   * this test adds values to the index storage and then removes an entry.
   * It validates the index storage against the test list and then removes and validates again
   */
  public void testRemoveMapping() throws IMQException {
    addValues(region, numValues);

    IndexRegionTestEntry ire = (IndexRegionTestEntry) entries.remove(8);
    indexDataStructure.removeMapping("" + 8, ire.regionEntry);
    validateIndexStorage();

    ire = (IndexRegionTestEntry) entries.remove(4);
    indexDataStructure.removeMapping("" + 4, ire.regionEntry);
    validateIndexStorage();
  }

  /**
   * This test will test the descending iterator by iterating the descending
   * iterator and comparing the results to the test entries in reverse order
   */
  public void testDescendingIterator() throws IMQException {
    addValues(region, numValues);
    validateDescendingIterator(indexDataStructure.descendingIterator(null), numValues - 1, 0);
  }

  /**
   * tests start inclusive iterator from beginning to end
   */
  public void testStartInclusiveIterator() throws IMQException {
    addValues(region, numValues);
    String startValue = "" + 0;
    CloseableIterator<IndexStoreEntry> iterator = indexDataStructure
        .iterator(startValue, true, null);
    validateIteratorSize(iterator, numValues);
  }

  /**
   * tests start exclusive iterator from beginning.  Exclusive should not 
   * include the first entry, so numValues - 1
   */
  public void testStartExclusiveIterator() throws IMQException  {
    addValues(region, numValues);
    String startValue = "" + 0;
    CloseableIterator<IndexStoreEntry> iterator = indexDataStructure
        .iterator(startValue, false, null);
    validateIteratorSize(iterator, numValues - 1);
  }

  public void testEndInclusiveIterator() throws IMQException {
    addValues(region, numValues);
    String endValue = "" + (numValues - 1);
    CloseableIterator<IndexStoreEntry> iterator = indexDataStructure
        .descendingIterator(endValue, true, null);
    validateIteratorSize(iterator, numValues);
  }

  public void testEndExclusiveIterator() throws IMQException {
    addValues(region, numValues);
    String endValue = "" + (numValues - 1);
    CloseableIterator<IndexStoreEntry> iterator = indexDataStructure
        .descendingIterator(endValue, false, null);
    validateIteratorSize(iterator, numValues - 1);
  }

  public void testStartInclusiveEndInclusive() throws IMQException {
    String startValue = "" + 0;
    String endValue = "" + 9;
    helpTestStartAndEndIterator(region, startValue, true, endValue, true, numValues);
  }

  public void testStartInclusiveEndExclusive() throws IMQException {
    String startValue = "" + 0;
    String endValue = "" + 9;
    helpTestStartAndEndIterator(region, startValue, true, endValue, false,
        numValues - 1);
  }

  public void testStartExclusiveEndExclusive() throws IMQException {
    String startValue = "" + 0;
    String endValue = "" + 9;
    helpTestStartAndEndIterator(region, startValue, false, endValue, false,
        numValues - 2);
  }

  public void testStartExclusiveEndInclusive() throws IMQException {
    String startValue = "" + 0;
    String endValue = "" + 9;
    helpTestStartAndEndIterator(region, startValue, true, endValue, false,
        numValues - 1);
  }
  
  
  
  private class IndexRegionTestEntry implements IndexStoreEntry {
    RegionEntry regionEntry;

    IndexRegionTestEntry(RegionEntry re) {
      this.regionEntry = re;
    }

    public boolean equals(Object object) {
      if (object instanceof IndexStoreEntry) {
        Object regionKey = ((IndexStoreEntry) object).getDeserializedRegionKey();
//        if (regionKey instanceof CachedDeserializable) {
//          regionKey = ((CachedDeserializable) regionKey)
//              .getDeserializedForReading();
//        }

        return regionEntry.getKey().equals(regionKey);
      }
      return false;
    }

    @Override
    public Object getDeserializedKey() {
      return regionEntry.getKey();
    }

    @Override
    public Object getDeserializedRegionKey() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Object getDeserializedValue() {
      // TODO Auto-generated method stub
      return null;
    }
    
    public boolean isUpdateInProgress() {
      return false;
    }
  }
}