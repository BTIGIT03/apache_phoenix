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
package org.apache.phoenix.filter;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.phoenix.expression.Expression;
import org.apache.phoenix.expression.KeyValueColumnExpression;
import org.apache.phoenix.expression.visitor.ExpressionVisitor;
import org.apache.phoenix.expression.visitor.StatelessTraverseAllExpressionVisitor;
import org.apache.phoenix.schema.tuple.BaseTuple;
import org.apache.phoenix.util.ByteUtil;
import org.apache.phoenix.util.ClientUtil;

/**
 * Modeled after {@link org.apache.hadoop.hbase.filter.SingleColumnValueFilter}, but for general
 * expression evaluation in the case where multiple KeyValue columns are referenced in the
 * expression.
 */
public abstract class MultiKeyValueComparisonFilter extends BooleanExpressionFilter {
  private static final byte[] UNITIALIZED_KEY_BUFFER = new byte[0];

  private Boolean matchedColumn;
  protected final IncrementalResultTuple inputTuple = new IncrementalResultTuple();
  protected TreeSet<byte[]> cfSet;
  private byte[] essentialCF = ByteUtil.EMPTY_BYTE_ARRAY;
  private boolean allCFs;

  public MultiKeyValueComparisonFilter() {
  }

  public MultiKeyValueComparisonFilter(Expression expression, boolean allCFs, byte[] essentialCF) {
    super(expression);
    this.allCFs = allCFs;
    this.essentialCF = essentialCF == null ? ByteUtil.EMPTY_BYTE_ARRAY : essentialCF;
    init();
  }

  private static final class CellRef {
    public Cell cell;

    @Override
    public String toString() {
      if (cell != null) {
        return cell.toString() + " value = " + Bytes.toStringBinary(cell.getValueArray(),
          cell.getValueOffset(), cell.getValueLength());
      } else {
        return super.toString();
      }
    }
  }

  protected abstract Object setColumnKey(byte[] cf, int cfOffset, int cfLength, byte[] cq,
    int cqOffset, int cqLength);

  protected abstract Object newColumnKey(byte[] cf, int cfOffset, int cfLength, byte[] cq,
    int cqOffset, int cqLength);

  private final class IncrementalResultTuple extends BaseTuple {
    private int refCount;
    private final ImmutableBytesWritable keyPtr =
      new ImmutableBytesWritable(UNITIALIZED_KEY_BUFFER);
    private final Map<Object, CellRef> foundColumns = new HashMap<Object, CellRef>(5);

    public void reset() {
      refCount = 0;
      keyPtr.set(UNITIALIZED_KEY_BUFFER);
      for (CellRef ref : foundColumns.values()) {
        ref.cell = null;
      }
    }

    @Override
    public boolean isImmutable() {
      return refCount == foundColumns.size();
    }

    public void setImmutable() {
      refCount = foundColumns.size();
    }

    private ReturnCode resolveColumn(Cell value) {
      // Always set key, in case we never find a key value column of interest,
      // and our expression uses row key columns.
      setKey(value);
      Object ptr =
        setColumnKey(value.getFamilyArray(), value.getFamilyOffset(), value.getFamilyLength(),
          value.getQualifierArray(), value.getQualifierOffset(), value.getQualifierLength());
      CellRef ref = foundColumns.get(ptr);
      if (ref == null) {
        // Return INCLUDE_AND_NEXT_COL here. Although this filter doesn't need this KV
        // it should still be projected into the Result
        return ReturnCode.INCLUDE_AND_NEXT_COL;
      }
      // Since we only look at the latest key value for a given column,
      // we are not interested in older versions
      // TODO: test with older versions to confirm this doesn't get tripped
      // This shouldn't be necessary, because a scan only looks at the latest
      // version
      if (ref.cell != null) {
        // Can't do NEXT_ROW, because then we don't match the other columns
        // SKIP, INCLUDE, and NEXT_COL seem to all act the same
        return ReturnCode.NEXT_COL;
      }
      ref.cell = value;
      refCount++;
      return null;
    }

    public void addColumn(byte[] cf, byte[] cq) {
      Object ptr =
        MultiKeyValueComparisonFilter.this.newColumnKey(cf, 0, cf.length, cq, 0, cq.length);
      foundColumns.put(ptr, new CellRef());
    }

    public void setKey(Cell value) {
      keyPtr.set(value.getRowArray(), value.getRowOffset(), value.getRowLength());
    }

    @Override
    public void getKey(ImmutableBytesWritable ptr) {
      ptr.set(keyPtr.get(), keyPtr.getOffset(), keyPtr.getLength());
    }

    @Override
    public Cell getValue(byte[] cf, byte[] cq) {
      Object ptr = setColumnKey(cf, 0, cf.length, cq, 0, cq.length);
      CellRef ref = foundColumns.get(ptr);
      return ref == null ? null : ref.cell;
    }

    @Override
    public String toString() {
      return foundColumns.toString();
    }

    @Override
    public int size() {
      return refCount;
    }

    @Override
    public Cell getValue(int index) {
      // This won't perform very well, but it's not
      // currently used anyway
      for (CellRef ref : foundColumns.values()) {
        if (ref.cell == null) {
          continue;
        }
        if (index == 0) {
          return ref.cell;
        }
        index--;
      }
      throw new IndexOutOfBoundsException(Integer.toString(index));
    }

    @Override
    public boolean getValue(byte[] family, byte[] qualifier, ImmutableBytesWritable ptr) {
      Cell cell = getValue(family, qualifier);
      if (cell == null) return false;
      ptr.set(cell.getValueArray(), cell.getValueOffset(), cell.getValueLength());
      return true;
    }

    @Override
    public long getSerializedSize() {
      if (foundColumns.isEmpty()) {
        return 0;
      }
      long totalSize = 0;
      for (CellRef ref : foundColumns.values()) {
        if (ref != null && ref.cell != null) {
          totalSize += ref.cell.getSerializedSize();
        }
      }
      return totalSize;
    }
  }

  protected void init() {
    cfSet = new TreeSet<byte[]>(Bytes.BYTES_COMPARATOR);
    ExpressionVisitor<Void> visitor = new StatelessTraverseAllExpressionVisitor<Void>() {
      @Override
      public Void visit(KeyValueColumnExpression expression) {
        inputTuple.addColumn(expression.getColumnFamily(), expression.getColumnQualifier());
        return null;
      }
    };
    expression.accept(visitor);
  }

  // No @Override for Hbase 3 compatibility
  public ReturnCode filterKeyValue(Cell cell) {
    return filterCell(cell);
  }

  @Override
  public ReturnCode filterCell(Cell cell) {
    if (Boolean.TRUE.equals(this.matchedColumn)) {
      // We already found and matched the single column, all keys now pass
      return ReturnCode.INCLUDE_AND_NEXT_COL;
    }
    if (Boolean.FALSE.equals(this.matchedColumn)) {
      // We found all the columns, but did not match the expression, so skip to next row
      return ReturnCode.NEXT_ROW;
    }
    // This is a key value we're not interested in (TODO: why INCLUDE here instead of NEXT_COL?)
    ReturnCode code = inputTuple.resolveColumn(cell);
    if (code != null) {
      return code;
    }

    // We found a new column, so we can re-evaluate
    // TODO: if we have row key columns in our expression, should
    // we always evaluate or just wait until the end?
    this.matchedColumn = this.evaluate(inputTuple);
    if (this.matchedColumn == null) {
      if (inputTuple.isImmutable()) {
        this.matchedColumn = Boolean.FALSE;
      } else {
        return ReturnCode.INCLUDE_AND_NEXT_COL;
      }
    }
    return this.matchedColumn ? ReturnCode.INCLUDE_AND_NEXT_COL : ReturnCode.NEXT_ROW;
  }

  @Override
  public boolean filterRow() {
    if (
      this.matchedColumn == null && !inputTuple.isImmutable()
        && expression.requiresFinalEvaluation()
    ) {
      inputTuple.setImmutable();
      this.matchedColumn = this.evaluate(inputTuple);
    }

    return !(Boolean.TRUE.equals(this.matchedColumn));
  }

  @Override
  public void reset() {
    matchedColumn = null;
    inputTuple.reset();
    super.reset();
  }

  @Override
  public boolean isFamilyEssential(byte[] name) {
    // Typically only the column families involved in the expression are essential.
    // The others are for columns projected in the select expression. However, depending
    // on the expression (i.e. IS NULL), we may need to include the column family
    // containing the empty key value or all column families in the case of a mapped
    // view (where we don't have an empty key value).
    return allCFs || Bytes.compareTo(name, essentialCF) == 0 || cfSet.contains(name);
  }

  @Override
  public void readFields(DataInput input) throws IOException {
    super.readFields(input);
    try {
      allCFs = input.readBoolean();
      if (!allCFs) {
        essentialCF = Bytes.readByteArray(input);
      }
    } catch (EOFException e) { // Ignore as this will occur when a 4.10 client is used
    }
    init();
  }

  @Override
  public void write(DataOutput output) throws IOException {
    super.write(output);
    try {
      output.writeBoolean(allCFs);
      if (!allCFs) {
        Bytes.writeByteArray(output, essentialCF);
      }
    } catch (Throwable t) { // Catches incompatibilities during reading/writing and doesn't retry
      ClientUtil.throwIOException("MultiKeyValueComparisonFilter failed during writing", t);
    }
  }

}
