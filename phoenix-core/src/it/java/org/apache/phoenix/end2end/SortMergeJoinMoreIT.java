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

import static org.apache.phoenix.util.TestUtil.TEST_PROPERTIES;
import static org.apache.phoenix.util.TestUtil.assertResultSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import org.apache.phoenix.util.PropertiesUtil;
import org.apache.phoenix.util.QueryUtil;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(ParallelStatsDisabledTest.class)
public class SortMergeJoinMoreIT extends ParallelStatsDisabledIT {

  @Test
  public void testJoinOverSaltedTables() throws Exception {
    String tempTableNoSalting = "TEMP_TABLE_NO_SALTING" + generateUniqueName();
    String tempTableWithSalting = "TEMP_TABLE_WITH_SALTING" + generateUniqueName();
    Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
    Connection conn = DriverManager.getConnection(getUrl(), props);
    try {
      conn.createStatement().execute("CREATE TABLE " + tempTableNoSalting
        + "   (mypk INTEGER NOT NULL PRIMARY KEY, " + "    col1 INTEGER)");
      conn.createStatement().execute("CREATE TABLE " + tempTableWithSalting
        + "   (mypk INTEGER NOT NULL PRIMARY KEY, " + "    col1 INTEGER) SALT_BUCKETS=4");

      PreparedStatement upsertStmt = conn
        .prepareStatement("upsert into " + tempTableNoSalting + "(mypk, col1) " + "values (?, ?)");
      for (int i = 0; i < 3; i++) {
        upsertStmt.setInt(1, i + 1);
        upsertStmt.setInt(2, 3 - i);
        upsertStmt.execute();
      }
      conn.commit();

      upsertStmt = conn.prepareStatement(
        "upsert into " + tempTableWithSalting + "(mypk, col1) " + "values (?, ?)");
      for (int i = 0; i < 6; i++) {
        upsertStmt.setInt(1, i + 1);
        upsertStmt.setInt(2, 3 - (i % 3));
        upsertStmt.execute();
      }
      conn.commit();

      // LHS=unsalted JOIN RHS=salted
      String query = "SELECT /*+ USE_SORT_MERGE_JOIN*/ lhs.mypk, lhs.col1, rhs.mypk, rhs.col1 FROM "
        + tempTableNoSalting + " lhs JOIN " + tempTableWithSalting
        + " rhs ON rhs.mypk = lhs.col1 ORDER BY lhs.mypk";
      PreparedStatement statement = conn.prepareStatement(query);
      ResultSet rs = statement.executeQuery();
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 1);
      assertEquals(rs.getInt(2), 3);
      assertEquals(rs.getInt(3), 3);
      assertEquals(rs.getInt(4), 1);
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 2);
      assertEquals(rs.getInt(2), 2);
      assertEquals(rs.getInt(3), 2);
      assertEquals(rs.getInt(4), 2);
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 3);
      assertEquals(rs.getInt(2), 1);
      assertEquals(rs.getInt(3), 1);
      assertEquals(rs.getInt(4), 3);

      assertFalse(rs.next());

      // LHS=salted JOIN RHS=salted
      query = "SELECT /*+ USE_SORT_MERGE_JOIN*/ lhs.mypk, lhs.col1, rhs.mypk, rhs.col1 FROM "
        + tempTableWithSalting + " lhs JOIN " + tempTableNoSalting
        + " rhs ON rhs.mypk = lhs.col1 ORDER BY lhs.mypk";
      statement = conn.prepareStatement(query);
      rs = statement.executeQuery();
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 1);
      assertEquals(rs.getInt(2), 3);
      assertEquals(rs.getInt(3), 3);
      assertEquals(rs.getInt(4), 1);
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 2);
      assertEquals(rs.getInt(2), 2);
      assertEquals(rs.getInt(3), 2);
      assertEquals(rs.getInt(4), 2);
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 3);
      assertEquals(rs.getInt(2), 1);
      assertEquals(rs.getInt(3), 1);
      assertEquals(rs.getInt(4), 3);
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 4);
      assertEquals(rs.getInt(2), 3);
      assertEquals(rs.getInt(3), 3);
      assertEquals(rs.getInt(4), 1);
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 5);
      assertEquals(rs.getInt(2), 2);
      assertEquals(rs.getInt(3), 2);
      assertEquals(rs.getInt(4), 2);
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 6);
      assertEquals(rs.getInt(2), 1);
      assertEquals(rs.getInt(3), 1);
      assertEquals(rs.getInt(4), 3);

      assertFalse(rs.next());

      // LHS=salted JOIN RHS=salted
      query = "SELECT /*+ USE_SORT_MERGE_JOIN*/ lhs.mypk, lhs.col1, rhs.mypk, rhs.col1 FROM "
        + tempTableWithSalting + " lhs JOIN " + tempTableWithSalting
        + " rhs ON rhs.mypk = (lhs.col1 + 3) ORDER BY lhs.mypk";
      statement = conn.prepareStatement(query);
      rs = statement.executeQuery();
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 1);
      assertEquals(rs.getInt(2), 3);
      assertEquals(rs.getInt(3), 6);
      assertEquals(rs.getInt(4), 1);
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 2);
      assertEquals(rs.getInt(2), 2);
      assertEquals(rs.getInt(3), 5);
      assertEquals(rs.getInt(4), 2);
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 3);
      assertEquals(rs.getInt(2), 1);
      assertEquals(rs.getInt(3), 4);
      assertEquals(rs.getInt(4), 3);
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 4);
      assertEquals(rs.getInt(2), 3);
      assertEquals(rs.getInt(3), 6);
      assertEquals(rs.getInt(4), 1);
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 5);
      assertEquals(rs.getInt(2), 2);
      assertEquals(rs.getInt(3), 5);
      assertEquals(rs.getInt(4), 2);
      assertTrue(rs.next());
      assertEquals(rs.getInt(1), 6);
      assertEquals(rs.getInt(2), 1);
      assertEquals(rs.getInt(3), 4);
      assertEquals(rs.getInt(4), 3);

      assertFalse(rs.next());
    } finally {
      conn.close();
    }
  }

  @Test
  public void testJoinOnDynamicColumns() throws Exception {
    String tableA = generateUniqueName();
    String tableB = generateUniqueName();
    Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
    Connection conn = null;
    PreparedStatement stmt = null;
    try {
      conn = DriverManager.getConnection(getUrl(), props);
      String ddlA = "CREATE TABLE " + tableA + "   (pkA INTEGER NOT NULL, " + "    colA1 INTEGER, "
        + "        colA2 VARCHAR " + "CONSTRAINT PK PRIMARY KEY" + "(pkA)" + ")";

      String ddlB =
        "CREATE TABLE " + tableB + "   (pkB INTEGER NOT NULL PRIMARY KEY, " + "    colB INTEGER)";
      conn.createStatement().execute(ddlA);
      conn.createStatement().execute(ddlB);

      String upsertA = "UPSERT INTO " + tableA + " (pkA, colA1, colA2) VALUES(?, ?, ?)";
      stmt = conn.prepareStatement(upsertA);
      int i = 0;
      for (i = 0; i < 5; i++) {
        stmt.setInt(1, i);
        stmt.setInt(2, i + 10);
        stmt.setString(3, "00" + i);
        stmt.executeUpdate();
      }
      conn.commit();
      stmt.close();
      String seqBName = generateUniqueName();

      // upsert select dynamic columns in tableB
      conn.createStatement().execute("CREATE SEQUENCE " + seqBName);
      String upsertBSelectA = "UPSERT INTO " + tableB + " (pkB, pkA INTEGER)"
        + "SELECT NEXT VALUE FOR " + seqBName + ", pkA FROM " + tableA;
      stmt = conn.prepareStatement(upsertBSelectA);
      stmt.executeUpdate();
      stmt.close();
      conn.commit();
      conn.createStatement().execute("DROP SEQUENCE " + seqBName);

      // perform a join between tableB and tableA by joining on the dynamic column that we upserted
      // in
      // tableB. This join should return all the rows from table A.
      String joinSql = "SELECT /*+ USE_SORT_MERGE_JOIN*/ A.pkA, A.COLA1, A.colA2 FROM " + tableB
        + " B(pkA INTEGER) JOIN " + tableA + " A ON a.pkA = b.pkA";
      stmt = conn.prepareStatement(joinSql);
      ResultSet rs = stmt.executeQuery();
      i = 0;
      while (rs.next()) {
        // check that we get back all the rows that we upserted for tableA above.
        assertEquals(rs.getInt(1), i);
        assertEquals(rs.getInt(2), i + 10);
        assertEquals(rs.getString(3), "00" + i);
        i++;
      }
      assertEquals(5, i);
    } finally {
      if (stmt != null) {
        stmt.close();
      }
      if (conn != null) {
        conn.close();
      }

    }

  }

  @Test
  public void testSubqueryWithoutData() throws Exception {
    Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
    Connection conn = DriverManager.getConnection(getUrl(), props);
    conn.setAutoCommit(false);
    String gTableName = generateUniqueName();
    String lTableName = generateUniqueName();
    String slTableName = generateUniqueName();

    try {
      String GRAMMAR_TABLE = "CREATE TABLE IF NOT EXISTS " + gTableName
        + "  (ID INTEGER PRIMARY KEY, "
        + "unsig_id UNSIGNED_INT, big_id BIGINT, unsig_long_id UNSIGNED_LONG, tiny_id TINYINT,"
        + "unsig_tiny_id UNSIGNED_TINYINT, small_id SMALLINT, unsig_small_id UNSIGNED_SMALLINT,"
        + "float_id FLOAT, unsig_float_id UNSIGNED_FLOAT, double_id DOUBLE, unsig_double_id UNSIGNED_DOUBLE,"
        + "decimal_id DECIMAL, boolean_id BOOLEAN, time_id TIME, date_id DATE, timestamp_id TIMESTAMP,"
        + "unsig_time_id TIME, unsig_date_id DATE, unsig_timestamp_id TIMESTAMP, varchar_id VARCHAR (30),"
        + "char_id CHAR (30), binary_id BINARY (100), varbinary_id VARBINARY (100))";

      String LARGE_TABLE = "CREATE TABLE IF NOT EXISTS " + lTableName + " (ID INTEGER PRIMARY KEY, "
        + "unsig_id UNSIGNED_INT, big_id BIGINT, unsig_long_id UNSIGNED_LONG, tiny_id TINYINT,"
        + "unsig_tiny_id UNSIGNED_TINYINT, small_id SMALLINT, unsig_small_id UNSIGNED_SMALLINT,"
        + "float_id FLOAT, unsig_float_id UNSIGNED_FLOAT, double_id DOUBLE, unsig_double_id UNSIGNED_DOUBLE,"
        + "decimal_id DECIMAL, boolean_id BOOLEAN, time_id TIME, date_id DATE, timestamp_id TIMESTAMP,"
        + "unsig_time_id TIME, unsig_date_id DATE, unsig_timestamp_id TIMESTAMP, varchar_id VARCHAR (30),"
        + "char_id CHAR (30), binary_id BINARY (100), varbinary_id VARBINARY (100))";

      String SECONDARY_LARGE_TABLE = "CREATE TABLE IF NOT EXISTS " + slTableName
        + " (SEC_ID INTEGER PRIMARY KEY,"
        + "sec_unsig_id UNSIGNED_INT, sec_big_id BIGINT, sec_usnig_long_id UNSIGNED_LONG, sec_tiny_id TINYINT,"
        + "sec_unsig_tiny_id UNSIGNED_TINYINT, sec_small_id SMALLINT, sec_unsig_small_id UNSIGNED_SMALLINT,"
        + "sec_float_id FLOAT, sec_unsig_float_id UNSIGNED_FLOAT, sec_double_id DOUBLE, sec_unsig_double_id UNSIGNED_DOUBLE,"
        + "sec_decimal_id DECIMAL, sec_boolean_id BOOLEAN, sec_time_id TIME, sec_date_id DATE,"
        + "sec_timestamp_id TIMESTAMP, sec_unsig_time_id TIME, sec_unsig_date_id DATE, sec_unsig_timestamp_id TIMESTAMP,"
        + "sec_varchar_id VARCHAR (30), sec_char_id CHAR (30), sec_binary_id BINARY (100), sec_varbinary_id VARBINARY (100))";
      createTestTable(getUrl(), GRAMMAR_TABLE);
      createTestTable(getUrl(), LARGE_TABLE);
      createTestTable(getUrl(), SECONDARY_LARGE_TABLE);

      String ddl = "SELECT /*+USE_SORT_MERGE_JOIN*/ * FROM (SELECT ID, BIG_ID, DATE_ID FROM "
        + lTableName + " AS A WHERE (A.ID % 5) = 0) AS A "
        + "INNER JOIN (SELECT SEC_ID, SEC_TINY_ID, SEC_UNSIG_FLOAT_ID FROM " + slTableName
        + " AS B WHERE (B.SEC_ID % 5) = 0) AS B "
        + "ON A.ID=B.SEC_ID WHERE A.DATE_ID > ALL (SELECT SEC_DATE_ID FROM " + slTableName
        + " LIMIT 100) " + "AND B.SEC_UNSIG_FLOAT_ID = ANY (SELECT sec_unsig_float_id FROM "
        + slTableName + " WHERE SEC_ID > ALL (SELECT MIN (ID) FROM " + gTableName
        + "  WHERE UNSIG_ID IS NULL) AND " + "SEC_UNSIG_ID < ANY (SELECT DISTINCT(UNSIG_ID) FROM "
        + lTableName + " WHERE UNSIG_ID<2500) LIMIT 1000) " + "AND A.ID < 10000";
      ResultSet rs = conn.createStatement().executeQuery(ddl);
      assertFalse(rs.next());
    } finally {
      Statement statement = conn.createStatement();
      String query = "drop table " + gTableName;
      statement.executeUpdate(query);
      query = "drop table " + lTableName;
      statement.executeUpdate(query);
      query = "drop table " + slTableName;
      statement.executeUpdate(query);
      conn.close();
    }
  }

  @Test
  public void testBug2894() throws Exception {
    Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
    Connection conn = DriverManager.getConnection(getUrl(), props);
    conn.setAutoCommit(true);
    String eventCountTableName = generateUniqueName();
    try {
      conn.createStatement()
        .execute("CREATE TABLE IF NOT EXISTS " + eventCountTableName + " (\n"
          + "        BUCKET VARCHAR,\n" + "        TIMESTAMP_DATE TIMESTAMP,\n"
          + "        \"TIMESTAMP\" UNSIGNED_LONG NOT NULL,\n" + "        LOCATION VARCHAR,\n"
          + "        A VARCHAR,\n" + "        B VARCHAR,\n" + "        C VARCHAR,\n"
          + "        D UNSIGNED_LONG,\n" + "        E FLOAT\n"
          + "    CONSTRAINT pk PRIMARY KEY (BUCKET, \"TIMESTAMP\" DESC, LOCATION, A, B, C)\n"
          + ") SALT_BUCKETS=2, COMPRESSION='GZ', TTL=31622400");
      PreparedStatement stmt = conn.prepareStatement("UPSERT INTO " + eventCountTableName
        + "(BUCKET, \"TIMESTAMP\", LOCATION, A, B, C) VALUES(?,?,?,?,?,?)");
      stmt.setString(1, "5SEC");
      stmt.setString(3, "Tr/Bal");
      stmt.setString(4, "A1");
      stmt.setString(5, "B1");
      stmt.setString(6, "C1");
      stmt.setLong(2, 1462993520000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993515000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993510000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993505000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993500000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993495000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993490000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993485000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993480000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993475000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993470000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993465000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993460000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993455000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993450000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993445000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993440000000000L);
      stmt.execute();
      stmt.setLong(2, 1462993430000000000L);
      stmt.execute();

      // We'll test the original version of the user table as well as a slightly modified
      // version, in order to verify that sort-merge join works for columns both having
      // DESC sort order as well as one having ASC order and the other having DESC order.
      String[] t = new String[] { "EVENT_LATENCY" + generateUniqueName(),
        "EVENT_LATENCY_2" + generateUniqueName() };
      for (int i = 0; i < 2; i++) {
        conn.createStatement()
          .execute("CREATE TABLE IF NOT EXISTS " + t[i] + " (\n" + "        BUCKET VARCHAR,\n"
            + "        TIMESTAMP_DATE TIMESTAMP,\n"
            + "        \"TIMESTAMP\" UNSIGNED_LONG NOT NULL,\n" + "        SRC_LOCATION VARCHAR,\n"
            + "        DST_LOCATION VARCHAR,\n" + "        B VARCHAR,\n" + "        C VARCHAR,\n"
            + "        F UNSIGNED_LONG,\n" + "        G UNSIGNED_LONG,\n"
            + "        H UNSIGNED_LONG,\n" + "        I UNSIGNED_LONG\n"
            + "    CONSTRAINT pk PRIMARY KEY (BUCKET, \"TIMESTAMP\"" + (i == 0 ? " DESC" : "")
            + ", SRC_LOCATION, DST_LOCATION, B, C)\n"
            + ") SALT_BUCKETS=2, COMPRESSION='GZ', TTL=31622400");
        stmt = conn.prepareStatement("UPSERT INTO " + t[i]
          + "(BUCKET, \"TIMESTAMP\", SRC_LOCATION, DST_LOCATION, B, C) VALUES(?,?,?,?,?,?)");
        stmt.setString(1, "5SEC");
        stmt.setString(3, "Tr/Bal");
        stmt.setString(4, "Tr/Bal");
        stmt.setString(5, "B1");
        stmt.setString(6, "C1");
        stmt.setLong(2, 1462993520000000000L);
        stmt.execute();
        stmt.setLong(2, 1462993515000000000L);
        stmt.execute();
        stmt.setLong(2, 1462993510000000000L);
        stmt.execute();
        stmt.setLong(2, 1462993505000000000L);
        stmt.execute();
        stmt.setLong(2, 1462993490000000000L);
        stmt.execute();
        stmt.setLong(2, 1462993485000000000L);
        stmt.execute();
        stmt.setLong(2, 1462993480000000000L);
        stmt.execute();
        stmt.setLong(2, 1462993475000000000L);
        stmt.execute();
        stmt.setLong(2, 1462993470000000000L);
        stmt.execute();
        stmt.setLong(2, 1462993430000000000L);
        stmt.execute();
        // add order by to make the query result stable
        String q = "SELECT C.BUCKET, C.TIMESTAMP FROM (\n"
          + "     SELECT E.BUCKET as BUCKET, L.BUCKET as LBUCKET, E.TIMESTAMP as TIMESTAMP, L.TIMESTAMP as LTIMESTAMP FROM\n"
          + "        (SELECT BUCKET, TIMESTAMP FROM " + eventCountTableName + "\n"
          + "             WHERE BUCKET = '5SEC' AND LOCATION = 'Tr/Bal'\n"
          + "                 AND TIMESTAMP <= 1462993520000000000 AND TIMESTAMP > 1462993420000000000\n"
          + "             GROUP BY BUCKET, TIMESTAMP, LOCATION\n" + "        ) E\n"
          + "        JOIN\n" + "         (SELECT BUCKET, \"TIMESTAMP\" FROM " + t[i] + "\n"
          + "             WHERE BUCKET = '5SEC' AND SRC_LOCATION = 'Tr/Bal' AND SRC_LOCATION = DST_LOCATION\n"
          + "                 AND \"TIMESTAMP\" <= 1462993520000000000 AND \"TIMESTAMP\" > 1462993420000000000\n"
          + "             GROUP BY BUCKET, \"TIMESTAMP\", SRC_LOCATION, DST_LOCATION\n"
          + "         ) L\n" + "     ON L.BUCKET = E.BUCKET AND L.TIMESTAMP = E.TIMESTAMP\n"
          + " ) C\n" + " GROUP BY C.BUCKET, C.TIMESTAMP ORDER BY C.BUCKET, C.TIMESTAMP";

        String p = i == 0
          ? "SORT-MERGE-JOIN (INNER) TABLES\n"
            + "    CLIENT PARALLEL 2-WAY SKIP SCAN ON 2 RANGES OVER " + eventCountTableName
            + " [X'00','5SEC',~1462993520000000000,'Tr/Bal'] - [X'01','5SEC',~1462993420000000000,'Tr/Bal']\n"
            + "        SERVER FILTER BY FIRST KEY ONLY\n"
            + "        SERVER DISTINCT PREFIX FILTER OVER [BUCKET, TIMESTAMP, LOCATION]\n"
            + "        SERVER AGGREGATE INTO ORDERED DISTINCT ROWS BY [BUCKET, TIMESTAMP, LOCATION]\n"
            + "    CLIENT MERGE SORT\n" + "    CLIENT SORTED BY [BUCKET, TIMESTAMP]\n"
            + "AND (SKIP MERGE)\n" + "    CLIENT PARALLEL 2-WAY SKIP SCAN ON 2 RANGES OVER " + t[i]
            + " [X'00','5SEC',~1462993520000000000,'Tr/Bal'] - [X'01','5SEC',~1462993420000000000,'Tr/Bal']\n"
            + "        SERVER FILTER BY FIRST KEY ONLY AND SRC_LOCATION = DST_LOCATION\n"
            + "        SERVER DISTINCT PREFIX FILTER OVER [BUCKET, \"TIMESTAMP\", SRC_LOCATION, DST_LOCATION]\n"
            + "        SERVER AGGREGATE INTO ORDERED DISTINCT ROWS BY [BUCKET, \"TIMESTAMP\", SRC_LOCATION, DST_LOCATION]\n"
            + "    CLIENT MERGE SORT\n" + "    CLIENT SORTED BY [BUCKET, \"TIMESTAMP\"]\n"
            + "CLIENT AGGREGATE INTO ORDERED DISTINCT ROWS BY [E.BUCKET, E.TIMESTAMP]"
          : "SORT-MERGE-JOIN (INNER) TABLES\n"
            + "    CLIENT PARALLEL 2-WAY SKIP SCAN ON 2 RANGES OVER " + eventCountTableName
            + " [X'00','5SEC',~1462993520000000000,'Tr/Bal'] - [X'01','5SEC',~1462993420000000000,'Tr/Bal']\n"
            + "        SERVER FILTER BY FIRST KEY ONLY\n"
            + "        SERVER DISTINCT PREFIX FILTER OVER [BUCKET, TIMESTAMP, LOCATION]\n"
            + "        SERVER AGGREGATE INTO ORDERED DISTINCT ROWS BY [BUCKET, TIMESTAMP, LOCATION]\n"
            + "    CLIENT MERGE SORT\n" + "    CLIENT SORTED BY [BUCKET, TIMESTAMP]\n"
            + "AND (SKIP MERGE)\n" + "    CLIENT PARALLEL 2-WAY SKIP SCAN ON 2 RANGES OVER " + t[i]
            + " [X'00','5SEC',1462993420000000001,'Tr/Bal'] - [X'01','5SEC',1462993520000000000,'Tr/Bal']\n"
            + "        SERVER FILTER BY FIRST KEY ONLY AND SRC_LOCATION = DST_LOCATION\n"
            + "        SERVER DISTINCT PREFIX FILTER OVER [BUCKET, \"TIMESTAMP\", SRC_LOCATION, DST_LOCATION]\n"
            + "        SERVER AGGREGATE INTO ORDERED DISTINCT ROWS BY [BUCKET, \"TIMESTAMP\", SRC_LOCATION, DST_LOCATION]\n"
            + "    CLIENT MERGE SORT\n"
            + "CLIENT AGGREGATE INTO ORDERED DISTINCT ROWS BY [E.BUCKET, E.TIMESTAMP]";

        ResultSet rs = conn.createStatement().executeQuery("explain " + q);
        assertEquals(p, QueryUtil.getExplainPlan(rs));

        rs = conn.createStatement().executeQuery(q);
        assertTrue(rs.next());
        assertEquals("5SEC", rs.getString(1));
        assertEquals(1462993430000000000L, rs.getLong(2));
        assertTrue(rs.next());
        assertEquals("5SEC", rs.getString(1));
        assertEquals(1462993470000000000L, rs.getLong(2));
        assertTrue(rs.next());
        assertEquals("5SEC", rs.getString(1));
        assertEquals(1462993475000000000L, rs.getLong(2));
        assertTrue(rs.next());
        assertEquals("5SEC", rs.getString(1));
        assertEquals(1462993480000000000L, rs.getLong(2));
        assertTrue(rs.next());
        assertEquals("5SEC", rs.getString(1));
        assertEquals(1462993485000000000L, rs.getLong(2));
        assertTrue(rs.next());
        assertEquals("5SEC", rs.getString(1));
        assertEquals(1462993490000000000L, rs.getLong(2));
        assertTrue(rs.next());
        assertEquals("5SEC", rs.getString(1));
        assertEquals(1462993505000000000L, rs.getLong(2));
        assertTrue(rs.next());
        assertEquals("5SEC", rs.getString(1));
        assertEquals(1462993510000000000L, rs.getLong(2));
        assertTrue(rs.next());
        assertEquals("5SEC", rs.getString(1));
        assertEquals(1462993515000000000L, rs.getLong(2));
        assertTrue(rs.next());
        assertEquals("5SEC", rs.getString(1));
        assertEquals(1462993520000000000L, rs.getLong(2));
        assertFalse(rs.next());
      }
    } finally {
      conn.close();
    }
  }

  @Test
  public void testSubQueryOrderByOverrideBug3745() throws Exception {
    Connection conn = null;
    try {
      Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
      conn = DriverManager.getConnection(getUrl(), props);

      String tableName1 = generateUniqueName();
      String tableName2 = generateUniqueName();

      conn.createStatement().execute("DROP TABLE if exists " + tableName1);

      String sql = "CREATE TABLE IF NOT EXISTS " + tableName1 + " ( " + "AID INTEGER PRIMARY KEY,"
        + "AGE INTEGER" + ")";
      conn.createStatement().execute(sql);

      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (1,11)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (2,22)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (3,33)");
      conn.commit();

      conn.createStatement().execute("DROP TABLE if exists " + tableName2);
      sql = "CREATE TABLE IF NOT EXISTS " + tableName2 + " ( " + "BID INTEGER PRIMARY KEY,"
        + "CODE INTEGER" + ")";
      conn.createStatement().execute(sql);

      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (1,66)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (2,55)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (3,44)");
      conn.commit();

      // test for simple scan
      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid,b.code from (select aid,age from " + tableName1
        + " where age >=11 and age<=33) a inner join " + "(select bid,code from " + tableName2
        + " order by code limit 1) b on a.aid=b.bid ";

      ResultSet rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 3);
      assertTrue(rs.getInt(2) == 44);
      assertTrue(!rs.next());

      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid,b.code from (select aid,age from " + tableName1
        + " where age >=11 and age<=33) a inner join " + "(select bid,code from " + tableName2
        + " order by code limit 2) b on a.aid=b.bid ";
      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 2);
      assertTrue(rs.getInt(2) == 55);
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 3);
      assertTrue(rs.getInt(2) == 44);
      assertTrue(!rs.next());

      // test for aggregate
      sql =
        "select /*+ USE_SORT_MERGE_JOIN */ a.aid,b.codesum from (select aid,sum(age) agesum from "
          + tableName1
          + " where age >=11 and age<=33 group by aid order by agesum limit 3) a inner join "
          + "(select bid,sum(code) codesum from " + tableName2
          + " group by bid order by codesum limit 2) b on a.aid=b.bid ";
      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 2);
      assertTrue(rs.getInt(2) == 55);
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 3);
      assertTrue(rs.getInt(2) == 44);
      assertTrue(!rs.next());

      String tableName3 = generateUniqueName();
      ;
      conn.createStatement().execute("DROP TABLE if exists " + tableName3);
      sql = "CREATE TABLE IF NOT EXISTS " + tableName3 + " ( " + "CID INTEGER PRIMARY KEY,"
        + "REGION INTEGER" + ")";
      conn.createStatement().execute(sql);

      conn.createStatement().execute("UPSERT INTO " + tableName3 + "(CID,REGION) VALUES (1,77)");
      conn.createStatement().execute("UPSERT INTO " + tableName3 + "(CID,REGION) VALUES (2,88)");
      conn.createStatement().execute("UPSERT INTO " + tableName3 + "(CID,REGION) VALUES (3,99)");
      conn.commit();

      // test for join
      sql = "select t1.aid,t1.code,t2.region from " + "(select a.aid,b.code from " + tableName1
        + " a inner join " + tableName2
        + " b on a.aid=b.bid where b.code >=44 and b.code<=66 order by b.code limit 3) t1 inner join "
        + "(select a.aid,c.region from " + tableName1 + " a inner join " + tableName3
        + " c on a.aid=c.cid where c.region>=77 and c.region<=99 order by c.region desc limit 1) t2 on t1.aid=t2.aid";

      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 3);
      assertTrue(rs.getInt(2) == 44);
      assertTrue(rs.getInt(3) == 99);
      assertTrue(!rs.next());

      // test for join and aggregate
      sql = "select t1.aid,t1.codesum,t2.regionsum from "
        + "(select a.aid,sum(b.code) codesum from " + tableName1 + " a inner join " + tableName2
        + " b on a.aid=b.bid where b.code >=44 and b.code<=66 group by a.aid order by codesum limit 3) t1 inner join "
        + "(select a.aid,sum(c.region) regionsum from " + tableName1 + " a inner join " + tableName3
        + " c on a.aid=c.cid where c.region>=77 and c.region<=99 group by a.aid order by regionsum desc limit 2) t2 on t1.aid=t2.aid";

      rs = conn.prepareStatement(sql).executeQuery();

      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 2);
      assertTrue(rs.getInt(2) == 55);
      assertTrue(rs.getInt(3) == 88);

      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 3);
      assertTrue(rs.getInt(2) == 44);
      assertTrue(rs.getInt(3) == 99);
      assertTrue(!rs.next());

      // test for if SubselectRewriter.isOrderByPrefix had take effect
      sql = "select t1.aid,t1.codesum,t2.regionsum from "
        + "(select a.aid,sum(b.code) codesum from " + tableName1 + " a inner join " + tableName2
        + " b on a.aid=b.bid where b.code >=44 and b.code<=66 group by a.aid order by a.aid,codesum limit 3) t1 inner join "
        + "(select a.aid,sum(c.region) regionsum from " + tableName1 + " a inner join " + tableName3
        + " c on a.aid=c.cid where c.region>=77 and c.region<=99 group by a.aid order by a.aid desc,regionsum desc limit 2) t2 on t1.aid=t2.aid "
        + "order by t1.aid desc";

      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 3);
      assertTrue(rs.getInt(2) == 44);
      assertTrue(rs.getInt(3) == 99);

      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 2);
      assertTrue(rs.getInt(2) == 55);
      assertTrue(rs.getInt(3) == 88);
      assertTrue(!rs.next());
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }

  @Test
  public void testBug4508() throws Exception {
    Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
    Connection conn = DriverManager.getConnection(getUrl(), props);
    props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
    props.setProperty("TenantId", "010");
    Connection conn010 = DriverManager.getConnection(getUrl(), props);
    try {
      // Salted tables
      String peopleTable = generateUniqueName();
      String myTable = generateUniqueName();
      conn.createStatement()
        .execute("CREATE TABLE " + peopleTable + " (\n" + "PERSON_ID VARCHAR NOT NULL,\n"
          + "NAME VARCHAR\n"
          + "CONSTRAINT PK_TEST_PEOPLE PRIMARY KEY (PERSON_ID)) SALT_BUCKETS = 3");
      conn.createStatement()
        .execute("CREATE TABLE " + myTable + " (\n" + "LOCALID VARCHAR NOT NULL,\n"
          + "DSID VARCHAR(255) NOT NULL, \n" + "EID CHAR(40),\n" + "HAS_CANDIDATES BOOLEAN\n"
          + "CONSTRAINT PK_MYTABLE PRIMARY KEY (LOCALID, DSID)) SALT_BUCKETS = 3");
      verifyQueryPlanAndResultForBug4508(conn, peopleTable, myTable);

      // Salted multi-tenant tables
      String peopleTable2 = generateUniqueName();
      String myTable2 = generateUniqueName();
      conn.createStatement()
        .execute("CREATE TABLE " + peopleTable2 + " (\n" + "TENANT_ID VARCHAR NOT NULL,\n"
          + "PERSON_ID VARCHAR NOT NULL,\n" + "NAME VARCHAR\n"
          + "CONSTRAINT PK_TEST_PEOPLE PRIMARY KEY (TENANT_ID, PERSON_ID))\n"
          + "SALT_BUCKETS = 3, MULTI_TENANT=true");
      conn.createStatement()
        .execute("CREATE TABLE " + myTable2 + " (\n" + "TENANT_ID VARCHAR NOT NULL,\n"
          + "LOCALID VARCHAR NOT NULL,\n" + "DSID VARCHAR(255) NOT NULL, \n" + "EID CHAR(40),\n"
          + "HAS_CANDIDATES BOOLEAN\n"
          + "CONSTRAINT PK_MYTABLE PRIMARY KEY (TENANT_ID, LOCALID, DSID))\n"
          + "SALT_BUCKETS = 3, MULTI_TENANT=true");
      verifyQueryPlanAndResultForBug4508(conn010, peopleTable2, myTable2);
    } finally {
      conn.close();
      conn010.close();
    }
  }

  private static void verifyQueryPlanAndResultForBug4508(Connection conn, String peopleTable,
    String myTable) throws Exception {
    PreparedStatement peopleTableUpsertStmt =
      conn.prepareStatement("UPSERT INTO " + peopleTable + " VALUES(?, ?)");
    peopleTableUpsertStmt.setString(1, "X001");
    peopleTableUpsertStmt.setString(2, "Marcus");
    peopleTableUpsertStmt.execute();
    peopleTableUpsertStmt.setString(1, "X002");
    peopleTableUpsertStmt.setString(2, "Jenny");
    peopleTableUpsertStmt.execute();
    peopleTableUpsertStmt.setString(1, "X003");
    peopleTableUpsertStmt.setString(2, "Seymour");
    peopleTableUpsertStmt.execute();
    conn.commit();

    PreparedStatement myTableUpsertStmt =
      conn.prepareStatement("UPSERT INTO " + myTable + " VALUES(?, ?, ?, ?)");
    myTableUpsertStmt.setString(1, "X001");
    myTableUpsertStmt.setString(2, "GROUP");
    myTableUpsertStmt.setString(3, null);
    myTableUpsertStmt.setBoolean(4, false);
    myTableUpsertStmt.execute();
    myTableUpsertStmt.setString(1, "X001");
    myTableUpsertStmt.setString(2, "PEOPLE");
    myTableUpsertStmt.setString(3, null);
    myTableUpsertStmt.setBoolean(4, false);
    myTableUpsertStmt.execute();
    myTableUpsertStmt.setString(1, "X003");
    myTableUpsertStmt.setString(2, "PEOPLE");
    myTableUpsertStmt.setString(3, null);
    myTableUpsertStmt.setBoolean(4, false);
    myTableUpsertStmt.execute();
    myTableUpsertStmt.setString(1, "X002");
    myTableUpsertStmt.setString(2, "PEOPLE");
    myTableUpsertStmt.setString(3, "Z990");
    myTableUpsertStmt.setBoolean(4, false);
    myTableUpsertStmt.execute();
    conn.commit();

    String query1 = "SELECT /*+ USE_SORT_MERGE_JOIN*/ COUNT(*)\n" + "FROM " + peopleTable
      + " ds JOIN " + myTable + " l\n" + "ON ds.PERSON_ID = l.LOCALID\n"
      + "WHERE l.EID IS NULL AND l.DSID = 'PEOPLE' AND l.HAS_CANDIDATES = FALSE";
    String query2 = "SELECT /*+ USE_SORT_MERGE_JOIN */ COUNT(*)\n" + "FROM (SELECT LOCALID FROM "
      + myTable + "\n" + "WHERE EID IS NULL AND DSID = 'PEOPLE' AND HAS_CANDIDATES = FALSE) l\n"
      + "JOIN " + peopleTable + " ds ON ds.PERSON_ID = l.LOCALID";

    for (String q : new String[] { query1, query2 }) {
      ResultSet rs = conn.createStatement().executeQuery("explain " + q);
      String plan = QueryUtil.getExplainPlan(rs);
      assertFalse("Tables should not be sorted over their PKs:\n" + plan,
        plan.contains("SERVER SORTED BY"));

      rs = conn.createStatement().executeQuery(q);
      assertTrue(rs.next());
      assertEquals(2, rs.getInt(1));
      assertFalse(rs.next());
    }
  }

  @Test
  public void testSortMergeJoinPushFilterThroughSortBug5105() throws Exception {
    Connection conn = null;
    try {
      Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
      conn = DriverManager.getConnection(getUrl(), props);

      String tableName1 = generateUniqueName();
      String tableName2 = generateUniqueName();

      String sql = "CREATE TABLE IF NOT EXISTS " + tableName1 + " ( " + "AID INTEGER PRIMARY KEY,"
        + "AGE INTEGER" + ")";
      conn.createStatement().execute(sql);

      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (1,11)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (2,22)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (3,33)");
      conn.commit();

      sql = "CREATE TABLE IF NOT EXISTS " + tableName2 + " ( " + "BID INTEGER PRIMARY KEY,"
        + "CODE INTEGER" + ")";
      conn.createStatement().execute(sql);

      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (1,66)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (2,55)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (3,44)");
      conn.commit();

      // test for simple scan

      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid,b.code from (select aid,age from " + tableName1
        + " where age >=11 and age<=33) a inner join " + "(select bid,code from " + tableName2
        + " order by code limit 2) b on a.aid=b.bid where b.code > 50";
      ResultSet rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 2);
      assertTrue(rs.getInt(2) == 55);
      assertTrue(!rs.next());

      // test for aggregate
      sql =
        "select /*+ USE_SORT_MERGE_JOIN */ a.aid,b.codesum from (select aid,sum(age) agesum from "
          + tableName1
          + " where age >=11 and age<=33 group by aid order by agesum limit 3) a inner join "
          + "(select bid,sum(code) codesum from " + tableName2
          + " group by bid order by codesum limit 2) b on a.aid=b.bid where b.codesum > 50";
      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 2);
      assertTrue(rs.getInt(2) == 55);
      assertTrue(!rs.next());

      String tableName3 = generateUniqueName();
      sql = "CREATE TABLE IF NOT EXISTS " + tableName3 + " ( " + "CID INTEGER PRIMARY KEY,"
        + "REGION INTEGER" + ")";
      conn.createStatement().execute(sql);

      conn.createStatement().execute("UPSERT INTO " + tableName3 + "(CID,REGION) VALUES (1,77)");
      conn.createStatement().execute("UPSERT INTO " + tableName3 + "(CID,REGION) VALUES (2,88)");
      conn.createStatement().execute("UPSERT INTO " + tableName3 + "(CID,REGION) VALUES (3,99)");
      conn.commit();

      // test for join
      sql = "select t1.aid,t1.code,t2.region from " + "(select a.aid,b.code from " + tableName1
        + " a inner join " + tableName2
        + " b on a.aid=b.bid where b.code >=44 and b.code<=66 order by b.code limit 3) t1 inner join "
        + "(select a.aid,c.region from " + tableName1 + " a inner join " + tableName3
        + " c on a.aid=c.cid where c.region>=77 and c.region<=99 order by c.region desc limit 1) t2 on t1.aid=t2.aid "
        + "where t1.code > 50";

      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(!rs.next());

      // test for join and aggregate
      sql = "select t1.aid,t1.codesum,t2.regionsum from "
        + "(select a.aid,sum(b.code) codesum from " + tableName1 + " a inner join " + tableName2
        + " b on a.aid=b.bid where b.code >=44 and b.code<=66 group by a.aid order by codesum limit 3) t1 inner join "
        + "(select a.aid,sum(c.region) regionsum from " + tableName1 + " a inner join " + tableName3
        + " c on a.aid=c.cid where c.region>=77 and c.region<=99 group by a.aid order by regionsum desc limit 2) t2 on t1.aid=t2.aid "
        + "where t1.codesum >=40 and t2.regionsum >= 90";

      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 3);
      assertTrue(rs.getInt(2) == 44);
      assertTrue(rs.getInt(3) == 99);
      assertTrue(!rs.next());

      // test for if SubselectRewriter.isOuterOrderByNodesPrefixOfInner had take effect
      sql = "select t1.aid,t1.codesum,t2.regionsum from "
        + "(select a.aid,sum(b.code) codesum from " + tableName1 + " a inner join " + tableName2
        + " b on a.aid=b.bid where b.code >=44 and b.code<=66 group by a.aid order by a.aid,codesum limit 3) t1 inner join "
        + "(select a.aid,sum(c.region) regionsum from " + tableName1 + " a inner join " + tableName3
        + " c on a.aid=c.cid where c.region>=77 and c.region<=99 group by a.aid order by a.aid desc,regionsum desc limit 2) t2 on t1.aid=t2.aid "
        + "where t1.codesum >=40 and t2.regionsum >= 80 order by t1.aid desc";

      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 3);
      assertTrue(rs.getInt(2) == 44);
      assertTrue(rs.getInt(3) == 99);

      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 2);
      assertTrue(rs.getInt(2) == 55);
      assertTrue(rs.getInt(3) == 88);
      assertTrue(!rs.next());
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }

  @Test
  public void testOrderPreservingForSortMergeJoinBug5148() throws Exception {
    doTestOrderPreservingForSortMergeJoinBug5148(false, false);
    doTestOrderPreservingForSortMergeJoinBug5148(false, true);
    doTestOrderPreservingForSortMergeJoinBug5148(true, false);
    doTestOrderPreservingForSortMergeJoinBug5148(true, true);
  }

  private void doTestOrderPreservingForSortMergeJoinBug5148(boolean desc, boolean salted)
    throws Exception {
    Connection conn = null;
    try {
      Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
      conn = DriverManager.getConnection(getUrl(), props);

      String tableName1 = generateUniqueName();
      String tableName2 = generateUniqueName();

      String sql = "CREATE TABLE IF NOT EXISTS " + tableName1 + " ( " + "AID INTEGER PRIMARY KEY "
        + (desc ? "desc" : "") + "," + "AGE INTEGER" + ") " + (salted ? "SALT_BUCKETS =4" : "");
      conn.createStatement().execute(sql);
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (1,11)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (2,22)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (3,33)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (4,44)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (5,55)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (6,66)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (7,77)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (8,88)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (9,99)");
      conn.commit();

      sql = "CREATE TABLE IF NOT EXISTS " + tableName2 + " ( " + "BID INTEGER PRIMARY KEY "
        + (desc ? "desc" : "") + "," + "CODE INTEGER" + ")" + (salted ? "SALT_BUCKETS =4" : "");
      conn.createStatement().execute(sql);
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (1,22)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (2,22)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (3,44)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (4,44)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (5,66)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (6,66)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (7,88)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (8,88)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (9,00)");
      conn.commit();

      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid,b.code from (select aid,age from " + tableName1
        + " where age >=11 and age<=99 order by age limit 10) a inner join "
        + "(select bid,code from " + tableName2
        + " order by code limit 10) b on a.aid=b.bid and a.age = b.code order by a.aid ,a.age";
      ResultSet rs = conn.prepareStatement(sql).executeQuery();
      assertResultSet(rs, new Object[][] { { 2, 22 }, { 4, 44 }, { 6, 66 }, { 8, 88 } });

      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid,b.code from (select aid,age from " + tableName1
        + " where age >=11 and age<=99 order by age limit 10) a inner join "
        + "(select bid,code from " + tableName2
        + " order by code limit 10) b on a.aid=b.bid and a.age = b.code order by a.aid desc,a.age desc";
      rs = conn.prepareStatement(sql).executeQuery();
      assertResultSet(rs, new Object[][] { { 8, 88 }, { 6, 66 }, { 4, 44 }, { 2, 22 } });

      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid,a.age from (select aid,age from " + tableName1
        + " where age >=11 and age<=99 order by age limit 10) a inner join "
        + "(select bid,code from " + tableName2
        + " order by code limit 10) b on a.aid=b.bid and a.age = b.code group by a.aid,a.age order by a.aid ,a.age";
      rs = conn.prepareStatement(sql).executeQuery();
      assertResultSet(rs, new Object[][] { { 2, 22 }, { 4, 44 }, { 6, 66 }, { 8, 88 } });

      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid,a.age from (select aid,age from " + tableName1
        + " where age >=11 and age<=99 order by age limit 10) a inner join "
        + "(select bid,code from " + tableName2
        + " order by code limit 10) b on a.aid=b.bid and a.age = b.code group by a.aid,a.age order by a.aid desc,a.age desc";
      rs = conn.prepareStatement(sql).executeQuery();
      assertResultSet(rs, new Object[][] { { 8, 88 }, { 6, 66 }, { 4, 44 }, { 2, 22 } });

      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid,b.code from (select aid,age from " + tableName1
        + " where age >=11 and age<=99 order by age limit 10) a inner join "
        + "(select bid,code from " + tableName2
        + " order by code limit 10) b on a.aid=b.bid and a.age = b.code order by b.bid ,b.code";
      rs = conn.prepareStatement(sql).executeQuery();
      assertResultSet(rs, new Object[][] { { 2, 22 }, { 4, 44 }, { 6, 66 }, { 8, 88 } });

      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid,b.code from (select aid,age from " + tableName1
        + " where age >=11 and age<=99 order by age limit 10) a inner join "
        + "(select bid,code from " + tableName2
        + " order by code limit 10) b on a.aid=b.bid and a.age = b.code order by b.bid desc ,b.code desc";
      rs = conn.prepareStatement(sql).executeQuery();
      assertResultSet(rs, new Object[][] { { 8, 88 }, { 6, 66 }, { 4, 44 }, { 2, 22 } });

      sql = "select /*+ USE_SORT_MERGE_JOIN */ b.bid,b.code from (select aid,age from " + tableName1
        + " where age >=11 and age<=99 order by age limit 10) a inner join "
        + "(select bid,code from " + tableName2
        + " order by code limit 10) b on a.aid=b.bid and a.age = b.code group by b.bid, b.code order by b.bid ,b.code";
      rs = conn.prepareStatement(sql).executeQuery();
      assertResultSet(rs, new Object[][] { { 2, 22 }, { 4, 44 }, { 6, 66 }, { 8, 88 } });

      sql = "select /*+ USE_SORT_MERGE_JOIN */ b.bid,b.code from (select aid,age from " + tableName1
        + " where age >=11 and age<=99 order by age limit 10) a inner join "
        + "(select bid,code from " + tableName2
        + " order by code limit 10) b on a.aid=b.bid and a.age = b.code group by b.bid, b.code order by b.bid desc,b.code desc";
      rs = conn.prepareStatement(sql).executeQuery();
      assertResultSet(rs, new Object[][] { { 8, 88 }, { 6, 66 }, { 4, 44 }, { 2, 22 } });
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }

  @Test
  public void testOptimizeLeftSemiJoinForSortMergeJoinBug5956() throws Exception {
    Connection conn = null;
    try {
      Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
      conn = DriverManager.getConnection(getUrl(), props);

      String tableName1 = generateUniqueName();
      String tableName2 = generateUniqueName();

      String sql = "CREATE TABLE IF NOT EXISTS " + tableName1 + " ( " + "AID INTEGER PRIMARY KEY,"
        + "AGE INTEGER" + ")";
      conn.createStatement().execute(sql);

      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (1,11)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (2,22)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (3,33)");
      conn.commit();

      sql = "CREATE TABLE IF NOT EXISTS " + tableName2 + " ( " + "BID INTEGER PRIMARY KEY,"
        + "CODE INTEGER" + ")";
      conn.createStatement().execute(sql);

      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (1,66)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (2,55)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (3,44)");
      conn.commit();

      // test left semi join, rhs is null
      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid from (select aid,age from " + tableName1
        + " where age >=11 and age<=33) a where a.aid in  " + "(select bid from " + tableName2
        + " where code > 70 limit 2)";
      ResultSet rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(!rs.next());

      // test left semi join,lhs is null
      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid from (select aid,age from " + tableName1
        + " where age > 40) a where a.aid in  " + "(select bid from " + tableName2
        + " where code > 50 limit 2)";
      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(!rs.next());

      // test left anti join,lhs is null
      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid from (select aid,age from " + tableName1
        + " where age > 40) a where a.aid not in  " + "(select bid from " + tableName2
        + " where code > 50 limit 2)";
      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(!rs.next());

      // test left anti join,rhs is null
      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid from (select aid,age from " + tableName1
        + " where age >=11 and age<=33) a where a.aid not in  " + "(select bid from " + tableName2
        + " where code > 70 limit 2)";
      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 1);
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 2);
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 3);
      assertTrue(!rs.next());
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }

  @Test
  public void testSortMergeFastReturnNullBug5793() throws Exception {
    Connection conn = null;
    try {
      Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
      conn = DriverManager.getConnection(getUrl(), props);

      String tableName1 = generateUniqueName();
      String tableName2 = generateUniqueName();

      String sql = "CREATE TABLE IF NOT EXISTS " + tableName1 + " ( " + "AID INTEGER PRIMARY KEY,"
        + "AGE INTEGER" + ")";
      conn.createStatement().execute(sql);

      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (1,11)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (2,22)");
      conn.createStatement().execute("UPSERT INTO " + tableName1 + "(AID,AGE) VALUES (3,33)");
      conn.commit();

      sql = "CREATE TABLE IF NOT EXISTS " + tableName2 + " ( " + "BID INTEGER PRIMARY KEY,"
        + "CODE INTEGER" + ")";
      conn.createStatement().execute(sql);

      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (1,66)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (2,55)");
      conn.createStatement().execute("UPSERT INTO " + tableName2 + "(BID,CODE) VALUES (3,44)");
      conn.commit();

      // test inner join, rhs is null
      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid from (select aid,age from " + tableName1
        + " where age >=11 and age<=33) a inner join  " + "(select bid,code from " + tableName2
        + " where code > 70 limit 2) b on a.aid = b.bid";
      ResultSet rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(!rs.next());

      // test inner join,lhs is null
      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid from (select aid,age from " + tableName1
        + " where age > 40) a inner join  " + "(select bid from " + tableName2
        + " where code > 50 limit 2) b on a.aid = b.bid";
      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(!rs.next());

      // test left join,lhs is null
      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid from (select aid,age from " + tableName1
        + " where age > 40) a left join  " + "(select bid from " + tableName2
        + " where code > 50 limit 2) b on a.aid = b.bid";
      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(!rs.next());

      // test left join,rhs is null
      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid from (select aid,age from " + tableName1
        + " where age >=11 and age<=33) a left join  " + "(select bid from " + tableName2
        + " where code > 70 limit 2) b on a.aid = b.bid";
      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 1);
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 2);
      assertTrue(rs.next());
      assertTrue(rs.getInt(1) == 3);
      assertTrue(!rs.next());

      // test full join, lhs return null and rhs return null.
      sql = "select /*+ USE_SORT_MERGE_JOIN */ a.aid from (select aid,age from " + tableName1
        + " where age > 40) a left join  " + "(select bid from " + tableName2
        + " where code > 70 limit 2) b on a.aid = b.bid";
      rs = conn.prepareStatement(sql).executeQuery();
      assertTrue(!rs.next());

    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }
}
