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
package org.apache.phoenix.pherf.result;

import java.util.ArrayList;
import java.util.List;
import org.apache.phoenix.pherf.configuration.Scenario;

public class ScenarioResult extends Scenario {

  private List<QuerySetResult> querySetResult = new ArrayList<>();

  public List<QuerySetResult> getQuerySetResult() {
    return querySetResult;
  }

  @SuppressWarnings("unused")
  public void setQuerySetResult(List<QuerySetResult> querySetResult) {
    this.querySetResult = querySetResult;
  }

  public ScenarioResult() {
  }

  public ScenarioResult(Scenario scenario) {
    this.setDataOverride(scenario.getDataOverride());
    this.setPhoenixProperties(scenario.getPhoenixProperties());
    this.setRowCount(scenario.getRowCount());
    this.setTableName(scenario.getTableName());
    this.setName(scenario.getName());
  }
}
