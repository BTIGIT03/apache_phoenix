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
package org.apache.phoenix.end2end.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;
import org.apache.phoenix.end2end.NeedsOwnMiniClusterTest;
import org.apache.phoenix.end2end.ParallelStatsDisabledIT;
import org.apache.phoenix.util.PhoenixRuntime;
import org.apache.phoenix.util.QueryUtil;
import org.apache.phoenix.util.SchemaUtil;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(NeedsOwnMiniClusterTest.class)
public class GlobalIndexOptimizationIT extends ParallelStatsDisabledIT {

  private void createBaseTable(String tableName, Integer saltBuckets, String splits,
    boolean multiTenant) throws SQLException {
    Connection conn = DriverManager.getConnection(getUrl());
    String ddl = "CREATE TABLE " + tableName + " (t_id VARCHAR NOT NULL,\n"
      + "k1 INTEGER NOT NULL,\n" + "k2 INTEGER NOT NULL,\n" + "k3 INTEGER,\n" + "v1 VARCHAR,\n"
      + "CONSTRAINT pk PRIMARY KEY (t_id, k1, k2))\n" + (multiTenant ? "MULTI_TENANT = true\n" : "")
      + (saltBuckets != null && splits == null
        ? (",salt_buckets=" + saltBuckets)
        : "" + (saltBuckets == null && splits != null ? (" split on " + splits) : ""));
    conn.createStatement().execute(ddl);
    conn.close();
  }

  private void createIndex(String indexName, String tableName, String columns, String includes,
    boolean local) throws SQLException {
    Connection conn = DriverManager.getConnection(getUrl());
    String ddl = "CREATE " + (local ? "LOCAL " : "") + "INDEX " + indexName + " ON " + tableName
      + " (" + columns + ")" + (includes != null ? " INCLUDE (" + includes + ")" : "");
    conn.createStatement().execute(ddl);
    conn.close();
  }

  @Test
  public void testIndexDeleteOptimizationWithLocalIndex() throws Exception {
    String dataTableName = generateUniqueName();
    String indexTableName = generateUniqueName();
    createBaseTable(dataTableName, null, null, false);
    // create a local index that only covers k3
    createIndex(indexTableName + "L", dataTableName, "k3", null, true);
    // create a gloval index covering v1, and k3
    createIndex(indexTableName + "G", dataTableName, "v1", "k3", false);

    String query = "DELETE FROM " + dataTableName + " where k3 < 100";
    try (Connection conn1 = DriverManager.getConnection(getUrl())) {
      conn1.createStatement().execute("UPSERT INTO " + dataTableName
        + " values(TO_CHAR(rand()*100),rand()*10000,rand()*10000,rand()*10000,TO_CHAR(rand()*100))");
      for (int i = 0; i < 16; i++) {
        conn1.createStatement().execute("UPSERT INTO " + dataTableName
          + " SELECT TO_CHAR(rand()*100),rand()*10000,rand()*10000,rand()*10000,TO_CHAR(rand()*100) FROM "
          + dataTableName);
      }
      ResultSet rs = conn1.createStatement().executeQuery("EXPLAIN " + query);
      String expected = "DELETE ROWS CLIENT SELECT\n" + "CLIENT PARALLEL 1-WAY RANGE SCAN OVER "
        + indexTableName + "L" + "(" + dataTableName + ") [1,*] - [1,100]\n"
        + "    SERVER FILTER BY FIRST KEY ONLY\n" + "CLIENT MERGE SORT";
      String actual = QueryUtil.getExplainPlan(rs);
      assertEquals(expected, actual);
      rs = conn1.createStatement().executeQuery("SELECT COUNT(*) FROM " + dataTableName);
      rs.next();
      int count = rs.getInt(1);
      int deleted = conn1.createStatement().executeUpdate(query);
      int expectedCount = count - deleted;

      rs = conn1.createStatement().executeQuery("SELECT COUNT(*) FROM " + dataTableName);
      rs.next();
      count = rs.getInt(1);
      assertEquals(expectedCount, count);

      rs = conn1.createStatement().executeQuery("SELECT COUNT(*) FROM " + indexTableName + "L");
      rs.next();
      count = rs.getInt(1);
      assertEquals(expectedCount, count);

      rs = conn1.createStatement().executeQuery("SELECT COUNT(*) FROM " + indexTableName + "G");
      rs.next();
      count = rs.getInt(1);
      assertEquals(expectedCount, count);
    }
  }

  @Test
  public void testGlobalIndexOptimization() throws Exception {
    String dataTableName = generateUniqueName();
    String indexTableName = generateUniqueName();
    String dataTableFullName = SchemaUtil.getTableName("", dataTableName);
    testOptimization(dataTableName, dataTableFullName, indexTableName, 4);
  }

  @Test
  public void testGlobalIndexOptimizationWithSalting() throws Exception {
    String dataTableName = generateUniqueName();
    String indexTableName = generateUniqueName();
    String dataTableFullName = SchemaUtil.getTableName("", dataTableName);
    testOptimization(dataTableName, dataTableFullName, indexTableName, 4);

  }

  @Test
  public void testGlobalIndexOptimizationTenantSpecific() throws Exception {
    String dataTableName = generateUniqueName();
    String indexTableName = generateUniqueName();
    testOptimizationTenantSpecific(dataTableName, indexTableName, null);
  }

  @Test
  public void testGlobalIndexOptimizationWithSaltingTenantSpecific() throws Exception {
    String dataTableName = generateUniqueName();
    String indexTableName = generateUniqueName();
    testOptimizationTenantSpecific(dataTableName, indexTableName, 4);
  }

  private void testOptimization(String dataTableName, String dataTableFullName,
    String indexTableName, Integer saltBuckets) throws Exception {

    createBaseTable(dataTableName, saltBuckets, "('e','i','o')", false);
    Connection conn1 = DriverManager.getConnection(getUrl());
    try {
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values('b',1,2,4,'z')");
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values('f',1,2,3,'a')");
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values('j',2,4,2,'a')");
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values('q',3,1,1,'c')");
      conn1.commit();
      createIndex(indexTableName, dataTableName, "v1", null, false);

      String query = "SELECT /*+ INDEX(" + dataTableName + " " + indexTableName + ")*/ * FROM "
        + dataTableName + " where v1='a'";
      ResultSet rs = conn1.createStatement().executeQuery("EXPLAIN " + query);

      String expected = "CLIENT PARALLEL 1-WAY RANGE SCAN OVER " + indexTableName + " ['a']\n"
        + "    SERVER MERGE [0.K3]\n" + "    SERVER FILTER BY FIRST KEY ONLY";
      String actual = QueryUtil.getExplainPlan(rs);
      assertTrue("Expected:\n" + expected + "\nbut got\n" + actual, actual.equals(expected));

      rs = conn1.createStatement().executeQuery(query);
      assertTrue(rs.next());
      assertEquals("f", rs.getString("t_id"));
      assertEquals(1, rs.getInt("k1"));
      assertEquals(2, rs.getInt("k2"));
      assertEquals(3, rs.getInt("k3"));
      assertTrue(rs.next());
      assertEquals("j", rs.getString("t_id"));
      assertEquals(2, rs.getInt("k1"));
      assertEquals(4, rs.getInt("k2"));
      assertEquals(2, rs.getInt("k3"));
      assertFalse(rs.next());

      query = "SELECT /*+ INDEX(" + dataTableName + " " + indexTableName + ")*/ * FROM "
        + dataTableName + " where v1='a'";
      rs = conn1.createStatement().executeQuery("EXPLAIN " + query);

      expected = "CLIENT PARALLEL 1-WAY RANGE SCAN OVER " + indexTableName + " ['a']\n"
        + "    SERVER MERGE [0.K3]\n" + "    SERVER FILTER BY FIRST KEY ONLY";
      actual = QueryUtil.getExplainPlan(rs);
      assertTrue("Expected:\n" + expected + "\nbut got\n" + actual, actual.equals(expected));

      rs = conn1.createStatement().executeQuery(query);
      assertTrue(rs.next());
      assertEquals("f", rs.getString("t_id"));
      assertEquals(1, rs.getInt("k1"));
      assertEquals(2, rs.getInt("k2"));
      assertEquals(3, rs.getInt("k3"));
      assertEquals("a", rs.getString("v1"));
      assertTrue(rs.next());
      assertEquals("j", rs.getString("t_id"));
      assertEquals(2, rs.getInt("k1"));
      assertEquals(4, rs.getInt("k2"));
      assertEquals(2, rs.getInt("k3"));
      assertEquals("a", rs.getString("v1"));
      assertFalse(rs.next());

      query = "SELECT /*+ INDEX(" + dataTableName + " " + indexTableName + ")*/ * FROM "
        + dataTableName + " where v1='a' limit 1";
      rs = conn1.createStatement().executeQuery("EXPLAIN " + query);

      expected = "CLIENT SERIAL 1-WAY RANGE SCAN OVER " + indexTableName + " ['a']\n"
        + "    SERVER MERGE [0.K3]\n" + "    SERVER FILTER BY FIRST KEY ONLY\n"
        + "    SERVER 1 ROW LIMIT\n" + "CLIENT 1 ROW LIMIT";
      actual = QueryUtil.getExplainPlan(rs);
      assertTrue("Expected:\n" + expected + "\nbut got\n" + actual, actual.equals(expected));

      rs = conn1.createStatement().executeQuery(query);
      assertTrue(rs.next());
      assertEquals("f", rs.getString("t_id"));
      assertEquals(1, rs.getInt("k1"));
      assertEquals(2, rs.getInt("k2"));
      assertEquals(3, rs.getInt("k3"));
      assertEquals("a", rs.getString("v1"));
      assertFalse(rs.next());

      query = "SELECT /*+ INDEX(" + dataTableName + " " + indexTableName
        + ")*/ t_id, k1, k2, k3, V1 from " + dataTableFullName
        + "  where v1<='z' and k3 > 1 order by V1,t_id";
      rs = conn1.createStatement().executeQuery("EXPLAIN " + query);

      expected = "CLIENT PARALLEL 1-WAY RANGE SCAN OVER " + indexTableName + " [*] - ['z']\n"
        + "    SERVER MERGE [0.K3]\n" + "    SERVER FILTER BY FIRST KEY ONLY AND \"K3\" > 1";
      actual = QueryUtil.getExplainPlan(rs);
      assertTrue("Expected:\n" + expected + "\nbut got\n" + actual, actual.equals(expected));

      rs = conn1.createStatement().executeQuery(query);
      assertTrue(rs.next());
      assertEquals("f", rs.getString("t_id"));
      assertEquals(1, rs.getInt("k1"));
      assertEquals(2, rs.getInt("k2"));
      assertEquals(3, rs.getInt("k3"));
      assertEquals("a", rs.getString("V1"));
      assertTrue(rs.next());
      assertEquals("j", rs.getString("t_id"));
      assertEquals(2, rs.getInt("k1"));
      assertEquals(4, rs.getInt("k2"));
      assertEquals(2, rs.getInt("k3"));
      assertEquals("a", rs.getString("V1"));
      assertTrue(rs.next());
      assertEquals("b", rs.getString("t_id"));
      assertEquals(1, rs.getInt("k1"));
      assertEquals(2, rs.getInt("k2"));
      assertEquals(4, rs.getInt("k3"));
      assertEquals("z", rs.getString("V1"));
      assertFalse(rs.next());

      // Same as above, but with SKIP-SCAN-JOIN hint
      query = "SELECT /*+ INDEX(" + dataTableName + " " + indexTableName
        + "), NO_INDEX_SERVER_MERGE */ t_id, k1, k2, k3, V1 from " + dataTableFullName
        + "  where v1<='z' and k3 > 1 order by V1,t_id";
      rs = conn1.createStatement().executeQuery("EXPLAIN " + query);
      expected = "CLIENT PARALLEL 1-WAY FULL SCAN OVER " + dataTableName + "\n"
        + "    SERVER FILTER BY K3 > 1\n" + "    SERVER SORTED BY \\[" + dataTableName + "\\.V1, "
        + dataTableName + "\\.T_ID\\]\n" + "CLIENT MERGE SORT\n" + "    SKIP-SCAN-JOIN TABLE 0\n"
        + "        CLIENT PARALLEL 1-WAY RANGE SCAN OVER " + indexTableName
        + " \\[\\*\\] - \\['z'\\]\n" + "            SERVER FILTER BY FIRST KEY ONLY\n"
        + "    DYNAMIC SERVER FILTER BY \\(\"" + dataTableName + "\\.T_ID\", \"" + dataTableName
        + "\\.K1\", \"" + dataTableName
        + "\\.K2\"\\) IN \\(\\(\\$\\d+\\.\\$\\d+, \\$\\d+\\.\\$\\d+, \\$\\d+\\.\\$\\d+\\)\\)";
      actual = QueryUtil.getExplainPlan(rs);
      assertTrue("Expected:\n" + expected + "\nbut got\n" + actual,
        Pattern.matches(expected, actual));

      rs = conn1.createStatement().executeQuery(query);
      assertTrue(rs.next());
      assertEquals("f", rs.getString("t_id"));
      assertEquals(1, rs.getInt("k1"));
      assertEquals(2, rs.getInt("k2"));
      assertEquals(3, rs.getInt("k3"));
      assertEquals("a", rs.getString("V1"));
      assertTrue(rs.next());
      assertEquals("j", rs.getString("t_id"));
      assertEquals(2, rs.getInt("k1"));
      assertEquals(4, rs.getInt("k2"));
      assertEquals(2, rs.getInt("k3"));
      assertEquals("a", rs.getString("V1"));
      assertTrue(rs.next());
      assertEquals("b", rs.getString("t_id"));
      assertEquals(1, rs.getInt("k1"));
      assertEquals(2, rs.getInt("k2"));
      assertEquals(4, rs.getInt("k3"));
      assertEquals("z", rs.getString("V1"));
      assertFalse(rs.next());

      query = "SELECT /*+ INDEX(" + dataTableName + " " + indexTableName + ")*/ t_id, V1, k3 from "
        + dataTableFullName + "  where v1 <='z' group by v1,t_id, k3";
      rs = conn1.createStatement().executeQuery("EXPLAIN " + query);
      expected = "CLIENT PARALLEL 1-WAY RANGE SCAN OVER " + indexTableName + " [*] - ['z']\n"
        + "    SERVER MERGE [0.K3]\n" + "    SERVER FILTER BY FIRST KEY ONLY\n"
        + "    SERVER AGGREGATE INTO DISTINCT ROWS BY [\"V1\", \"T_ID\", \"K3\"]\n"
        + "CLIENT MERGE SORT";

      actual = QueryUtil.getExplainPlan(rs);
      assertTrue("Expected:\n" + expected + "\nbut got\n" + actual, actual.equals(expected));

      rs = conn1.createStatement().executeQuery(query);
      assertTrue(rs.next());
      assertEquals("f", rs.getString("t_id"));
      assertEquals(3, rs.getInt("k3"));
      assertEquals("a", rs.getString("V1"));
      assertTrue(rs.next());
      assertEquals("j", rs.getString("t_id"));
      assertEquals(2, rs.getInt("k3"));
      assertEquals("a", rs.getString("V1"));
      assertTrue(rs.next());
      assertEquals("q", rs.getString("t_id"));
      assertEquals(1, rs.getInt("k3"));
      assertEquals("c", rs.getString("V1"));
      assertTrue(rs.next());
      assertEquals("b", rs.getString("t_id"));
      ;
      assertEquals(4, rs.getInt("k3"));
      assertEquals("z", rs.getString("V1"));
      assertFalse(rs.next());

      query = "SELECT /*+ INDEX(" + dataTableName + " " + indexTableName + ")*/ v1,sum(k3) from "
        + dataTableFullName + " where v1 <='z'  group by v1 order by v1";

      rs = conn1.createStatement().executeQuery("EXPLAIN " + query);
      expected = "CLIENT PARALLEL 1-WAY RANGE SCAN OVER " + indexTableName + " [*] - ['z']\n"
        + "    SERVER MERGE [0.K3]\n" + "    SERVER FILTER BY FIRST KEY ONLY\n"
        + "    SERVER AGGREGATE INTO ORDERED DISTINCT ROWS BY [\"V1\"]";
      actual = QueryUtil.getExplainPlan(rs);
      assertTrue("Expected:\n" + expected + "\nbut got\n" + actual, actual.equals(expected));

      rs = conn1.createStatement().executeQuery(query);
      assertTrue(rs.next());
      assertEquals("a", rs.getString(1));
      assertEquals(5, rs.getInt(2));
      assertTrue(rs.next());
      assertEquals("c", rs.getString(1));
      assertEquals(1, rs.getInt(2));
      assertTrue(rs.next());
      assertEquals("z", rs.getString(1));
      assertEquals(4, rs.getInt(2));
    } finally {
      conn1.close();
    }
  }

  private void testOptimizationTenantSpecific(String dataTableName, String indexTableName,
    Integer saltBuckets) throws Exception {
    createBaseTable(dataTableName, saltBuckets, "('e','i','o')", true);
    Connection conn1 =
      DriverManager.getConnection(getUrl() + ';' + PhoenixRuntime.TENANT_ID_ATTRIB + "=tid1");
    try {
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values(1,2,4,'z')");
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values(1,2,3,'a')");
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values(2,4,2,'a')");
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values(3,1,1,'c')");
      conn1.commit();
      createIndex(indexTableName, dataTableName, "v1", null, false);

      String query = "SELECT /*+ INDEX(" + dataTableName + " " + indexTableName
        + ")*/ k1,k2,k3,v1 FROM " + dataTableName + " where v1='a'";
      ResultSet rs = conn1.createStatement().executeQuery("EXPLAIN " + query);

      String actual = QueryUtil.getExplainPlan(rs);
      String expected = "CLIENT PARALLEL 1-WAY RANGE SCAN OVER " + indexTableName
        + " ['tid1','a']\n" + "    SERVER MERGE [0.K3]\n" + "    SERVER FILTER BY FIRST KEY ONLY";
      assertTrue("Expected:\n" + expected + "\nbut got\n" + actual, actual.equals(expected));

      rs = conn1.createStatement().executeQuery(query);
      assertTrue(rs.next());
      assertEquals(1, rs.getInt("k1"));
      assertEquals(2, rs.getInt("k2"));
      assertEquals(3, rs.getInt("k3"));
      assertEquals("a", rs.getString("v1"));
      assertTrue(rs.next());
      assertEquals(2, rs.getInt("k1"));
      assertEquals(4, rs.getInt("k2"));
      assertEquals(2, rs.getInt("k3"));
      assertEquals("a", rs.getString("v1"));
      assertFalse(rs.next());
    } finally {
      conn1.close();
    }
  }

  @Test
  public void testGlobalIndexOptimizationOnSharedIndex() throws Exception {
    String dataTableName = generateUniqueName();
    createBaseTable(dataTableName, null, "('e','i','o')", false);
    Connection conn1 = DriverManager.getConnection(getUrl());
    String viewName = generateUniqueName();
    String indexOnDataTable = generateUniqueName();
    try {
      conn1.createStatement().execute(
        "CREATE INDEX " + indexOnDataTable + " ON " + dataTableName + "(k2,k1) INCLUDE (v1)");
      conn1.createStatement().execute(
        "CREATE VIEW " + viewName + " AS SELECT * FROM " + dataTableName + " WHERE v1 = 'a'");
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values('b',1,2,4,'z')");
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values('f',1,2,3,'a')");
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values('j',2,4,2,'a')");
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values('q',3,1,1,'c')");
      conn1.commit();
      ResultSet rs = conn1.createStatement().executeQuery("SELECT COUNT(*) FROM " + viewName);
      assertTrue(rs.next());
      assertEquals(2, rs.getInt(1));
      assertFalse(rs.next());
      String viewIndex = generateUniqueName();
      conn1.createStatement().execute("CREATE INDEX " + viewIndex + " ON " + viewName + " (k1)");

      String query = "SELECT /*+ INDEX(" + viewName + " " + viewIndex + ")*/ t_id,k1,k2,k3,v1 FROM "
        + viewName + " where k1 IN (1,2) and k2 IN (3,4)";
      rs = conn1.createStatement().executeQuery("EXPLAIN " + query);

      /**
       * This inner "_IDX_" + dataTableName use skipScan, and all the whereExpressions are already
       * in SkipScanFilter, so there is no other RowKeyComparisonFilter needed.
       */

      String actual = QueryUtil.getExplainPlan(rs);
      String expected = "CLIENT PARALLEL 1-WAY SKIP SCAN ON 2 KEYS OVER _IDX_" + dataTableName
        + " [" + Short.MIN_VALUE + ",1] - [" + Short.MIN_VALUE + ",2]\n"
        + "    SERVER MERGE [0.K3]\n" + "    SERVER FILTER BY FIRST KEY ONLY";

      assertEquals(expected, actual);

      rs = conn1.createStatement().executeQuery(query);
      assertTrue(rs.next());
      assertEquals("j", rs.getString("t_id"));
      assertEquals(2, rs.getInt("k1"));
      assertEquals(4, rs.getInt("k2"));
      assertEquals(2, rs.getInt("k3"));
      assertEquals("a", rs.getString(5)); // TODO use name v1 instead of position 5, see
                                          // PHOENIX-6644
      assertFalse(rs.next());
    } finally {
      conn1.close();
    }
  }

  @Test
  public void testNoGlobalIndexOptimization() throws Exception {
    String dataTableName = generateUniqueName();
    String indexTableName = generateUniqueName();
    String dataTableFullName = SchemaUtil.getTableName("", dataTableName);
    createBaseTable(dataTableName, null, "('e','i','o')", false);
    Connection conn1 = DriverManager.getConnection(getUrl());
    try {
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values('b',1,2,4,'z')");
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values('f',1,2,3,'a')");
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values('j',2,4,2,'a')");
      conn1.createStatement().execute("UPSERT INTO " + dataTableName + " values('q',3,1,1,'c')");
      conn1.commit();
      conn1.createStatement()
        .execute("CREATE INDEX " + indexTableName + " ON " + dataTableName + "(v1)");

      // All columns available in index
      String query = "SELECT /*+ INDEX(" + dataTableName + " " + indexTableName
        + ")*/ t_id, k1, k2, V1 FROM " + dataTableName + " where v1='a'";
      ResultSet rs = conn1.createStatement().executeQuery("EXPLAIN " + query);

      assertEquals("CLIENT PARALLEL 1-WAY RANGE SCAN OVER " + indexTableName + " ['a']\n"
        + "    SERVER FILTER BY FIRST KEY ONLY", QueryUtil.getExplainPlan(rs));

      rs = conn1.createStatement().executeQuery(query);
      assertTrue(rs.next());
      assertEquals("f", rs.getString("t_id"));
      assertEquals(1, rs.getInt("k1"));
      assertEquals(2, rs.getInt("k2"));
      assertTrue(rs.next());
      assertEquals("j", rs.getString("t_id"));
      assertEquals(2, rs.getInt("k1"));
      assertEquals(4, rs.getInt("k2"));
      assertFalse(rs.next());

      // No INDEX hint specified
      query = "SELECT t_id, k1, k2, k3, V1 FROM " + dataTableName + " where v1='a'";
      rs = conn1.createStatement().executeQuery("EXPLAIN " + query);

      assertEquals("CLIENT PARALLEL 1-WAY FULL SCAN OVER " + dataTableName + "\n"
        + "    SERVER FILTER BY V1 = 'a'", QueryUtil.getExplainPlan(rs));

      rs = conn1.createStatement().executeQuery(query);
      assertTrue(rs.next());
      assertEquals("f", rs.getString("t_id"));
      assertEquals(1, rs.getInt("k1"));
      assertEquals(2, rs.getInt("k2"));
      assertEquals(3, rs.getInt("k3"));
      assertTrue(rs.next());
      assertEquals("j", rs.getString("t_id"));
      assertEquals(2, rs.getInt("k1"));
      assertEquals(4, rs.getInt("k2"));
      assertEquals(2, rs.getInt("k3"));
      assertFalse(rs.next());

      // No where clause
      query = "SELECT  t_id, k1, k2, k3, V1 from " + dataTableFullName + " order by V1,t_id";
      rs = conn1.createStatement().executeQuery("EXPLAIN " + query);

      assertEquals(
        "CLIENT PARALLEL 4-WAY FULL SCAN OVER " + dataTableName + "\n"
          + "    SERVER SORTED BY [V1, T_ID]\n" + "CLIENT MERGE SORT",
        QueryUtil.getExplainPlan(rs));

      rs = conn1.createStatement().executeQuery(query);
      assertTrue(rs.next());
      assertEquals("f", rs.getString("t_id"));
      assertEquals(1, rs.getInt("k1"));
      assertEquals(2, rs.getInt("k2"));
      assertEquals(3, rs.getInt("k3"));
      assertEquals("a", rs.getString("V1"));
      assertTrue(rs.next());
      assertEquals("j", rs.getString("t_id"));
      assertEquals(2, rs.getInt("k1"));
      assertEquals(4, rs.getInt("k2"));
      assertEquals(2, rs.getInt("k3"));
      assertEquals("a", rs.getString("V1"));
      assertTrue(rs.next());
      assertEquals("q", rs.getString("t_id"));
      assertEquals(3, rs.getInt("k1"));
      assertEquals(1, rs.getInt("k2"));
      assertEquals(1, rs.getInt("k3"));
      assertEquals("c", rs.getString("V1"));
      assertTrue(rs.next());
      assertEquals("b", rs.getString("t_id"));
      assertEquals(1, rs.getInt("k1"));
      assertEquals(2, rs.getInt("k2"));
      assertEquals(4, rs.getInt("k3"));
      assertEquals("z", rs.getString("V1"));
      assertFalse(rs.next());

      // No where clause in index scan
      query =
        "SELECT t_id, k1, k2, k3, V1 from " + dataTableFullName + "  where k3 > 1 order by V1,t_id";
      rs = conn1.createStatement().executeQuery("EXPLAIN " + query);

      assertEquals("CLIENT PARALLEL 4-WAY FULL SCAN OVER " + dataTableName + "\n"
        + "    SERVER FILTER BY K3 > 1\n" + "    SERVER SORTED BY [V1, T_ID]\n"
        + "CLIENT MERGE SORT", QueryUtil.getExplainPlan(rs));

      rs = conn1.createStatement().executeQuery(query);
      assertTrue(rs.next());
      assertEquals("f", rs.getString("t_id"));
      assertEquals(1, rs.getInt("k1"));
      assertEquals(2, rs.getInt("k2"));
      assertEquals(3, rs.getInt("k3"));
      assertEquals("a", rs.getString("V1"));
      assertTrue(rs.next());
      assertEquals("j", rs.getString("t_id"));
      assertEquals(2, rs.getInt("k1"));
      assertEquals(4, rs.getInt("k2"));
      assertEquals(2, rs.getInt("k3"));
      assertEquals("a", rs.getString("V1"));
      assertTrue(rs.next());
      assertEquals("b", rs.getString("t_id"));
      assertEquals(1, rs.getInt("k1"));
      assertEquals(2, rs.getInt("k2"));
      assertEquals(4, rs.getInt("k3"));
      assertEquals("z", rs.getString("V1"));
      assertFalse(rs.next());
    } finally {
      conn1.close();
    }
  }

}
