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
package org.apache.phoenix.compile;

import org.apache.phoenix.compile.GroupByCompiler.GroupBy;
import org.apache.phoenix.expression.Expression;
import org.apache.phoenix.schema.PColumn;

/**
 * A ExpressionCompiler which does not pollute {@link StatementContext}
 */
public class StatelessExpressionCompiler extends ExpressionCompiler {

  public StatelessExpressionCompiler(StatementContext context, boolean resolveViewConstants) {
    super(context, resolveViewConstants);
  }

  public StatelessExpressionCompiler(StatementContext context, GroupBy groupBy,
    boolean resolveViewConstants) {
    super(context, groupBy, resolveViewConstants);
  }

  public StatelessExpressionCompiler(StatementContext context, GroupBy groupBy) {
    super(context, groupBy);
  }

  public StatelessExpressionCompiler(StatementContext context) {
    super(context);
  }

  @Override
  protected Expression addExpression(Expression expression) {
    return expression;
  }

  @Override
  protected void addColumn(PColumn column) {

  }
}
