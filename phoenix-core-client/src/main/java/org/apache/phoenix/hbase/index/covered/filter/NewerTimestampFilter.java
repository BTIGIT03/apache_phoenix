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
package org.apache.phoenix.hbase.index.covered.filter;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.filter.FilterBase;

/**
 * Server-side only class used in the indexer to filter out keyvalues newer than a given timestamp
 * (so allows anything {@code <= } timestamp through).
 * <p>
 */
public class NewerTimestampFilter extends FilterBase {

  private long timestamp;

  public NewerTimestampFilter(long timestamp) {
    this.timestamp = timestamp;
  }

  // No @Override for HBase 3 compatibility
  public ReturnCode filterKeyValue(Cell ignored) {
    return this.filterCell(ignored);
  }

  @Override
  public ReturnCode filterCell(Cell ignored) {
    return ignored.getTimestamp() > timestamp ? ReturnCode.SKIP : ReturnCode.INCLUDE;
  }

}
