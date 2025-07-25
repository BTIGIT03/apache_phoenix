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
package org.apache.phoenix.coprocessorclient;

import java.util.Arrays;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.phoenix.util.SchemaUtil;

public class TableInfo {

  private final byte[] tenantId;
  private final byte[] schema;
  private final byte[] name;

  public TableInfo(byte[] tenantId, byte[] schema, byte[] name) {
    this.tenantId = tenantId;
    this.schema = schema;
    this.name = name;
  }

  public byte[] getRowKeyPrefix() {
    return SchemaUtil.getTableKey(tenantId, schema, name);
  }

  @Override
  public String toString() {
    return Bytes.toStringBinary(getRowKeyPrefix());
  }

  public byte[] getTenantId() {
    return tenantId;
  }

  public byte[] getSchemaName() {
    return schema;
  }

  public byte[] getTableName() {
    return name;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(name);
    result = prime * result + Arrays.hashCode(schema);
    result = prime * result + Arrays.hashCode(tenantId);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    TableInfo other = (TableInfo) obj;
    if (!Arrays.equals(name, other.name)) return false;
    if (!Arrays.equals(schema, other.schema)) return false;
    if (!Arrays.equals(tenantId, other.tenantId)) return false;
    return true;
  }
}
