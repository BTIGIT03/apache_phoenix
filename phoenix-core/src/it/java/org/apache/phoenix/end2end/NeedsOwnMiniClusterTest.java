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

/**
 * Marker class to denote that the test needs to run its own mini cluster that is separate from the
 * clusters used by tests annotated as {@link ParallelStatsDisabledTest} or
 * {@link ParallelStatsEnabledTest}. As much as possible, tests should be able to run in one of the
 * mini clusters used by {@link ParallelStatsDisabledTest} or {@link ParallelStatsEnabledTest}. In
 * the *rare* case it can't you would need to annotate the test as {@link NeedsOwnMiniClusterTest}
 * otherwise the test won't be executed when you run mvn verify or mvn install.
 * @since 4.1
 */
public class NeedsOwnMiniClusterTest {

}
