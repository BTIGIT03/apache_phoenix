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
package org.apache.phoenix.monitoring;

public class CombinableMetricImpl implements CombinableMetric, Cloneable {

  private final Metric metric;

  public CombinableMetricImpl(MetricType type) {
    metric = new NonAtomicMetric(type);
  }

  private CombinableMetricImpl(Metric metric) {
    this.metric = metric;
  }

  @Override
  public MetricType getMetricType() {
    return metric.getMetricType();
  }

  @Override
  public long getValue() {
    return metric.getValue();
  }

  @Override
  public void change(long delta) {
    metric.change(delta);
  }

  @Override
  public void increment() {
    metric.increment();
  }

  @Override
  public String getCurrentMetricState() {
    return metric.getCurrentMetricState();
  }

  @Override
  public void reset() {
    metric.reset();
  }

  /**
   * Set the Metric value as current value
   */
  @Override
  public void set(long value) {
    metric.set(value);
  }

  @Override
  public String getPublishString() {
    return getCurrentMetricState();
  }

  @Override
  public CombinableMetric combine(CombinableMetric metric) {
    this.metric.change(metric.getValue());
    return this;
  }

  @Override
  public void decrement() {
    metric.decrement();
  }

  @Override
  public CombinableMetric clone() {
    NonAtomicMetric metric = new NonAtomicMetric(this.metric.getMetricType());
    metric.change(this.metric.getValue());
    return new CombinableMetricImpl(metric);
  }

}
