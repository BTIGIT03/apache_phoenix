/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.end2end;

import java.util.Map;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.phoenix.coprocessor.TaskRegionObserver;
import org.apache.phoenix.jdbc.PhoenixDatabaseMetaData;
import org.apache.phoenix.query.BaseTest;
import org.apache.phoenix.query.QueryServices;
import org.apache.phoenix.util.ReadOnlyProps;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import org.apache.phoenix.thirdparty.com.google.common.collect.Maps;

/**
 * Base class for tests whose methods run in parallel with statistics enabled. You must create
 * unique names using {@link #generateUniqueName()} for each table and sequence used to prevent
 * collisions.
 */
public abstract class ParallelStatsEnabledIT extends BaseTest {

  protected static RegionCoprocessorEnvironment TaskRegionEnvironment;

  @BeforeClass
  public static synchronized final void doSetup() throws Exception {
    Map<String, String> props = Maps.newHashMapWithExpectedSize(1);
    props.put(QueryServices.STATS_GUIDEPOST_WIDTH_BYTES_ATTRIB, Long.toString(20));
    props.put(QueryServices.STATS_UPDATE_FREQ_MS_ATTRIB, Long.toString(5));
    props.put(QueryServices.MAX_SERVER_METADATA_CACHE_TIME_TO_LIVE_MS_ATTRIB, Long.toString(5));
    props.put(QueryServices.USE_STATS_FOR_PARALLELIZATION, Boolean.toString(true));
    props.put(QueryServices.TASK_HANDLING_INITIAL_DELAY_MS_ATTRIB, Long.toString(Long.MAX_VALUE));
    setUpTestDriver(new ReadOnlyProps(props.entrySet().iterator()));

    TaskRegionEnvironment =
      getUtility().getRSForFirstRegionInTable(PhoenixDatabaseMetaData.SYSTEM_TASK_HBASE_TABLE_NAME)
        .getRegions(PhoenixDatabaseMetaData.SYSTEM_TASK_HBASE_TABLE_NAME).get(0)
        .getCoprocessorHost().findCoprocessorEnvironment(TaskRegionObserver.class.getName());
  }

  @AfterClass
  public static synchronized void freeResources() throws Exception {
    BaseTest.freeResourcesIfBeyondThreshold();
  }
}
