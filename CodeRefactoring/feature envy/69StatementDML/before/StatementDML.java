/* Copyright (c) 2001-2010, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


 package org.hsqldb;

 import org.hsqldb.HsqlNameManager.HsqlName;
 import org.hsqldb.ParserDQL.CompileContext;
 import org.hsqldb.error.Error;
 import org.hsqldb.error.ErrorCode;
 import org.hsqldb.lib.ArrayUtil;
 import org.hsqldb.lib.HashSet;
 import org.hsqldb.lib.OrderedHashSet;
 import org.hsqldb.navigator.RangeIterator;
 import org.hsqldb.navigator.RowIterator;
 import org.hsqldb.navigator.RowSetNavigator;
 import org.hsqldb.navigator.RowSetNavigatorClient;
 import org.hsqldb.navigator.RowSetNavigatorDataChange;
 import org.hsqldb.persist.PersistentStore;
 import org.hsqldb.result.Result;
 import org.hsqldb.result.ResultConstants;
 import org.hsqldb.types.Type;
 
 /**
  * Implementation of Statement for DML statements.<p>
  *
  * @author Fred Toussi (fredt@users dot sourceforge.net)
  * @version 2.0.0
  * @since 1.9.0
  */

  // Feature Envy: performReferentialActions
 
 // support for MERGE statement originally contributed by Justin Spadea (jzs9783@users dot sourceforge.net)
 public class StatementDML extends StatementDMQL {
 
     Expression[]  targets;
     Expression    updatableTableCheck;
     RangeVariable checkRangeVariable;
     boolean       isTruncate;
 
     public StatementDML(int type, int group, HsqlName schemaName) {
         super(type, group, schemaName);
     }
 
     /**
      * Instantiate this as a DELETE statement
      */
     StatementDML(Session session, Table targetTable,
                  RangeVariable[] rangeVars, CompileContext compileContext,
                  boolean restartIdentity, int type) {
 
         super(StatementTypes.DELETE_WHERE, StatementTypes.X_SQL_DATA_CHANGE,
               session.getCurrentSchemaHsqlName());
 
         this.targetTable = targetTable;
         this.baseTable   = targetTable.getBaseTable() == null ? targetTable
                                                               : targetTable
                                                               .getBaseTable();
         this.targetRangeVariables = rangeVars;
         this.restartIdentity      = restartIdentity;
 
         setDatabseObjects(session, compileContext);
         checkAccessRights(session);
 
         if (type == StatementTypes.TRUNCATE) {
             isTruncate = true;
         }
 
         targetRangeVariables[0].addAllColumns();
     }
 
     /**
      * Instantiate this as an UPDATE statement.
      */
     StatementDML(Session session, Expression[] targets, Table targetTable,
                  RangeVariable rangeVars[], int[] updateColumnMap,
                  Expression[] colExpressions, boolean[] checkColumns,
                  CompileContext compileContext) {
 
         super(StatementTypes.UPDATE_WHERE, StatementTypes.X_SQL_DATA_CHANGE,
               session.getCurrentSchemaHsqlName());
 
         this.targets     = targets;
         this.targetTable = targetTable;
         this.baseTable   = targetTable.getBaseTable() == null ? targetTable
                                                               : targetTable
                                                               .getBaseTable();
         this.updateColumnMap      = updateColumnMap;
         this.updateExpressions    = colExpressions;
         this.updateCheckColumns   = checkColumns;
         this.targetRangeVariables = rangeVars;
 
         setDatabseObjects(session, compileContext);
         checkAccessRights(session);
         setupChecks();
         targetRangeVariables[0].addAllColumns();
     }
 
     /**
      * Instantiate this as a MERGE statement.
      */
     StatementDML(Session session, Expression[] targets,
                  RangeVariable[] targetRangeVars, int[] insertColMap,
                  int[] updateColMap, boolean[] checkColumns,
                  Expression mergeCondition, Expression insertExpr,
                  Expression[] updateExpr, CompileContext compileContext) {
 
         super(StatementTypes.MERGE, StatementTypes.X_SQL_DATA_CHANGE,
               session.getCurrentSchemaHsqlName());
 
         this.targets     = targets;
         this.sourceTable = targetRangeVars[0].rangeTable;
         this.targetTable = targetRangeVars[1].rangeTable;
         this.baseTable   = targetTable.getBaseTable() == null ? targetTable
                                                               : targetTable
                                                               .getBaseTable();
         this.insertCheckColumns   = checkColumns;
         this.insertColumnMap      = insertColMap;
         this.updateColumnMap      = updateColMap;
         this.insertExpression     = insertExpr;
         this.updateExpressions    = updateExpr;
         this.targetRangeVariables = targetRangeVars;
         this.condition            = mergeCondition;
 
         setDatabseObjects(session, compileContext);
         checkAccessRights(session);
         setupChecks();
     }
 
     /**
      * Instantiate this as a CURSOR operation statement.
      */
     StatementDML() {
         super(StatementTypes.UPDATE_CURSOR, StatementTypes.X_SQL_DATA_CHANGE,
               null);
     }
 
     void setupChecks() {
 
         if (targetTable != baseTable) {
             QuerySpecification select =
                 ((TableDerived) targetTable).getQueryExpression()
                     .getMainSelect();
 
             this.updatableTableCheck = select.checkQueryCondition;
             this.checkRangeVariable  = select.rangeVariables[0];
         }
     }
 
     Result getResult(Session session) {
 
         Result result = null;
 
         switch (type) {
 
             case StatementTypes.UPDATE_WHERE :
                 result = executeUpdateStatement(session);
                 break;
 
             case StatementTypes.MERGE :
                 result = executeMergeStatement(session);
                 break;
 
             case StatementTypes.DELETE_WHERE :
                 if (isTruncate) {
                     result = executeDeleteTruncateStatement(session);
                 } else {
                     result = executeDeleteStatement(session);
                 }
                 break;
 
             default :
                 throw Error.runtimeError(ErrorCode.U_S0500, "StatementDML");
         }
 
         return result;
     }
 
     // this fk references -> other  :  other read lock
     void collectTableNamesForRead(OrderedHashSet set) {
 
         if (baseTable.isView()) {
             getTriggerTableNames(set, false);
         } else if (!baseTable.isTemp()) {
             for (int i = 0; i < baseTable.fkConstraints.length; i++) {
                 Constraint constraint = baseTable.fkConstraints[i];
 
                 if (type == StatementTypes.UPDATE_WHERE
                         || type == StatementTypes.MERGE) {
                     if (ArrayUtil.haveCommonElement(constraint.getRefColumns(),
                                                     updateColumnMap)) {
                         set.add(
                             baseTable.fkConstraints[i].getMain().getName());
                     }
                 } else if (type == StatementTypes.INSERT) {
                     set.add(baseTable.fkConstraints[i].getMain().getName());
                 }
             }
 
             if (type == StatementTypes.UPDATE_WHERE
                     || type == StatementTypes.MERGE) {
                 baseTable.collectFKReadLocks(updateColumnMap, set);
             } else if (type == StatementTypes.DELETE_WHERE) {
                 baseTable.collectFKReadLocks(null, set);
             }
 
             getTriggerTableNames(set, false);
         }
 
         for (int i = 0; i < rangeVariables.length; i++) {
             Table    rangeTable = rangeVariables[i].rangeTable;
             HsqlName name       = rangeTable.getName();
 
             if (rangeTable.isReadOnly() || rangeTable.isTemp()) {
                 continue;
             }
 
             if (name.schema == SqlInvariants.SYSTEM_SCHEMA_HSQLNAME) {
                 continue;
             }
 
             set.add(name);
         }
 
         for (int i = 0; i < subqueries.length; i++) {
             if (subqueries[i].queryExpression != null) {
                 subqueries[i].queryExpression.getBaseTableNames(set);
             }
         }
 
         for (int i = 0; i < routines.length; i++) {
             set.addAll(routines[i].getTableNamesForRead());
         }
     }
 
     void collectTableNamesForWrite(OrderedHashSet set) {
 
         // other fk references this :  if constraint trigger action  : other write lock
         if (baseTable.isView()) {
             getTriggerTableNames(set, true);
         } else if (!baseTable.isTemp()) {
             set.add(baseTable.getName());
 
             if (type == StatementTypes.UPDATE_WHERE
                     || type == StatementTypes.MERGE) {
                 baseTable.collectFKWriteLocks(updateColumnMap, set);
             } else if (type == StatementTypes.DELETE_WHERE) {
                 baseTable.collectFKWriteLocks(null, set);
             }
 
             getTriggerTableNames(set, true);
         }
     }
 
     void getTriggerTableNames(OrderedHashSet set, boolean write) {
 
         for (int i = 0; i < baseTable.triggerList.length; i++) {
             TriggerDef td = baseTable.triggerList[i];
 
             switch (type) {
 
                 case StatementTypes.INSERT :
                     if (td.getStatementType() == StatementTypes.INSERT) {
                         break;
                     }
 
                     continue;
                 case StatementTypes.UPDATE_WHERE :
                     if (td.getStatementType() == StatementTypes.UPDATE_WHERE) {
                         break;
                     }
 
                     continue;
                 case StatementTypes.DELETE_WHERE :
                     if (td.getStatementType() == StatementTypes.DELETE_WHERE) {
                         break;
                     }
 
                     continue;
                 case StatementTypes.MERGE :
                     if (td.getStatementType() == StatementTypes.INSERT
                             || td.getStatementType()
                                == StatementTypes.UPDATE_WHERE) {
                         break;
                     }
 
                     continue;
                 default :
                     throw Error.runtimeError(ErrorCode.U_S0500,
                                              "StatementDML");
             }
 
             if (td.routine != null) {
                 if (write) {
                     set.addAll(td.routine.getTableNamesForWrite());
                 } else {
                     set.addAll(td.routine.getTableNamesForRead());
                 }
             }
         }
     }
 
     /**
      * Executes an UPDATE statement.
      *
      * @return Result object
      */
     Result executeUpdateStatement(Session session) {
 
         int                       count          = 0;
         Expression[]              colExpressions = updateExpressions;
         RowSetNavigatorDataChange rowset = new RowSetNavigatorDataChange();
         Type[]                    colTypes       = baseTable.getColumnTypes();
         RangeIterator it = RangeVariable.getIterator(session,
             targetRangeVariables);
 
         while (it.next()) {
             session.sessionData.startRowProcessing();
 
             Row      row  = it.getCurrentRow();
             Object[] data = row.getData();
             Object[] newData = getUpdatedData(session, targets, baseTable,
                                               updateColumnMap, colExpressions,
                                               colTypes, data);
 
             if (updatableTableCheck != null) {
                 it.setCurrent(newData);
 
                 boolean check = updatableTableCheck.testCondition(session);
 
                 if (!check) {
                     it.release();
 
                     throw Error.error(ErrorCode.X_44000);
                 }
             }
 
             rowset.addRow(session, row, newData, colTypes, updateColumnMap);
         }
 
         it.release();
 /* debug 190
         if (rowset.size() == 0) {
             System.out.println(targetTable.getName().name + " zero update: session "
                                + session.getId());
         } else if (rowset.size() >1) {
            System.out.println("multiple update: session "
                               + session.getId() + ", " + rowset.size());
        }
 
 //* debug 190 */
         rowset.beforeFirst();
 
         count = update(session, baseTable, rowset);
 
         if (count == 1) {
             return Result.updateOneResult;
         } else if (count == 0) {
             return Result.updateZeroResult;
         }
 
         return new Result(ResultConstants.UPDATECOUNT, count);
     }
 
     static Object[] getUpdatedData(Session session, Expression[] targets,
                                    Table targetTable, int[] columnMap,
                                    Expression[] colExpressions,
                                    Type[] colTypes, Object[] oldData) {
 
         Object[] data = targetTable.getEmptyRowData();
 
         System.arraycopy(oldData, 0, data, 0, data.length);
 
         for (int i = 0, ix = 0; i < columnMap.length; ) {
             Expression expr = colExpressions[ix++];
 
             if (expr.getType() == OpTypes.ROW) {
                 Object[] values = expr.getRowValue(session);
 
                 for (int j = 0; j < values.length; j++, i++) {
                     int        colIndex = columnMap[i];
                     Expression e        = expr.nodes[j];
 
                     // transitional - still supporting null for identity generation
                     if (targetTable.identityColumn == colIndex) {
                         if (e.getType() == OpTypes.VALUE
                                 && e.valueData == null) {
                             continue;
                         }
                     }
 
                     if (e.getType() == OpTypes.DEFAULT) {
                         if (targetTable.identityColumn == colIndex) {
                             continue;
                         }
 
                         data[colIndex] =
                             targetTable.colDefaults[colIndex].getValue(
                                 session);
 
                         continue;
                     }
 
                     data[colIndex] = colTypes[colIndex].convertToType(session,
                             values[j], e.dataType);
                 }
             } else if (expr.getType() == OpTypes.ROW_SUBQUERY) {
                 Object[] values = expr.getRowValue(session);
 
                 for (int j = 0; j < values.length; j++, i++) {
                     int colIndex = columnMap[i];
                     Type colType =
                         expr.subQuery.queryExpression.getMetaData()
                             .columnTypes[j];
 
                     data[colIndex] = colTypes[colIndex].convertToType(session,
                             values[j], colType);
                 }
             } else {
                 int colIndex = columnMap[i];
 
                 if (expr.getType() == OpTypes.DEFAULT) {
                     if (targetTable.identityColumn == colIndex) {
                         i++;
 
                         continue;
                     }
 
                     data[colIndex] =
                         targetTable.colDefaults[colIndex].getValue(session);
 
                     i++;
 
                     continue;
                 }
 
                 Object value = expr.getValue(session);
 
                 if (targets[i].getType() == OpTypes.ARRAY_ACCESS) {
                     data[colIndex] =
                         ((ExpressionAccessor) targets[i]).getUpdatedArray(
                             session, (Object[]) data[colIndex], value, true);
                 } else {
                     data[colIndex] = colTypes[colIndex].convertToType(session,
                             value, expr.dataType);
                 }
 
                 i++;
             }
         }
 
         return data;
     }
 
     /**
      * Executes a MERGE statement.
      *
      * @return Result object
      */
     Result executeMergeStatement(Session session) {
 
         Type[]          colTypes           = baseTable.getColumnTypes();
         Result          resultOut          = null;
         RowSetNavigator generatedNavigator = null;
 
         if (generatedIndexes != null) {
             resultOut = Result.newUpdateCountResult(generatedResultMetaData,
                     0);
             generatedNavigator = resultOut.getChainedResult().getNavigator();
         }
 
         int count = 0;
 
         // data generated for non-matching rows
         RowSetNavigatorClient newData = new RowSetNavigatorClient(8);
 
         // rowset for update operation
         RowSetNavigatorDataChange updateRowSet =
             new RowSetNavigatorDataChange();
         RangeVariable[] joinRangeIterators = targetRangeVariables;
 
         // populate insert and update lists
         RangeIterator[] rangeIterators =
             new RangeIterator[joinRangeIterators.length];
 
         for (int i = 0; i < joinRangeIterators.length; i++) {
             rangeIterators[i] = joinRangeIterators[i].getIterator(session);
         }
 
         for (int currentIndex = 0; currentIndex >= 0; ) {
             RangeIterator it          = rangeIterators[currentIndex];
             boolean       beforeFirst = it.isBeforeFirst();
 
             if (it.next()) {
                 if (currentIndex < joinRangeIterators.length - 1) {
                     currentIndex++;
 
                     continue;
                 }
             } else {
                 if (currentIndex == 1 && beforeFirst
                         && insertExpression != null) {
                     Object[] data =
                         getInsertData(session, colTypes,
                                       insertExpression.nodes[0].nodes);
 
                     if (data != null) {
                         newData.add(data);
                     }
                 }
 
                 it.reset();
 
                 currentIndex--;
 
                 continue;
             }
 
             // row matches!
             if (updateExpressions.length != 0) {
                 Row row = it.getCurrentRow();    // this is always the second iterator
                 Object[] data = getUpdatedData(session, targets, baseTable,
                                                updateColumnMap,
                                                updateExpressions, colTypes,
                                                row.getData());
 
                 try {
                     updateRowSet.addRow(session, row, data, colTypes,
                                         updateColumnMap);
                 } catch (HsqlException e) {
                     for (int i = 0; i < joinRangeIterators.length; i++) {
                         rangeIterators[i].reset();
                     }
 
                     throw Error.error(ErrorCode.X_21000);
                 }
             }
         }
 
         for (int i = 0; i < joinRangeIterators.length; i++) {
             rangeIterators[i].reset();
         }
 
         // run the transaction as a whole, updating and inserting where needed
         // update any matched rows
         if (updateExpressions.length != 0) {
             count = update(session, baseTable, updateRowSet);
         }
 
         // insert any non-matched rows
         if (newData.getSize() > 0) {
             insertRowSet(session, generatedNavigator, newData);
 
             count += newData.getSize();
         }
 
         if (insertExpression != null
                 && baseTable.triggerLists[Trigger.INSERT_AFTER].length > 0) {
             baseTable.fireTriggers(session, Trigger.INSERT_AFTER, newData);
         }
 
         if (resultOut == null) {
             if (count == 1) {
                 return Result.updateOneResult;
             }
 
             return new Result(ResultConstants.UPDATECOUNT, count);
         } else {
             resultOut.setUpdateCount(count);
 
             return resultOut;
         }
     }
 
     void insertRowSet(Session session, RowSetNavigator generatedNavigator,
                       RowSetNavigator newData) {
 
         PersistentStore store         = baseTable.getRowStore(session);
         RangeIterator   checkIterator = null;
 
         if (updatableTableCheck != null) {
             checkIterator = checkRangeVariable.getIterator(session);
         }
 
         newData.beforeFirst();
 
         if (baseTable.triggerLists[Trigger.INSERT_BEFORE_ROW].length > 0) {
             while (newData.hasNext()) {
                 Object[] data = (Object[]) newData.getNext();
 
                 baseTable.fireTriggers(session, Trigger.INSERT_BEFORE_ROW,
                                        null, data, null);
             }
 
             newData.beforeFirst();
         }
 
         while (newData.hasNext()) {
             Object[] data = (Object[]) newData.getNext();
 
             baseTable.insertSingleRow(session, store, data, null);
 
             if (checkIterator != null) {
                 checkIterator.setCurrent(data);
 
                 boolean check = updatableTableCheck.testCondition(session);
 
                 if (!check) {
                     throw Error.error(ErrorCode.X_44000);
                 }
             }
 
             if (generatedNavigator != null) {
                 Object[] generatedValues = getGeneratedColumns(data);
 
                 generatedNavigator.add(generatedValues);
             }
         }
 
         newData.beforeFirst();
 
         while (newData.hasNext()) {
             Object[] data = (Object[]) newData.getNext();
 
             performIntegrityChecks(session, baseTable, null, data, null);
         }
 
         newData.beforeFirst();
 
         if (baseTable.triggerLists[Trigger.INSERT_AFTER_ROW].length > 0) {
             while (newData.hasNext()) {
                 Object[] data = (Object[]) newData.getNext();
 
                 baseTable.fireTriggers(session, Trigger.INSERT_AFTER_ROW,
                                        null, data, null);
             }
 
             newData.beforeFirst();
         }
     }
 
     Result insertSingleRow(Session session, PersistentStore store,
                            Object[] data) {
 
         if (baseTable.triggerLists[Trigger.INSERT_BEFORE_ROW].length > 0) {
             baseTable.fireTriggers(session, Trigger.INSERT_BEFORE_ROW, null,
                                    data, null);
         }
 
         baseTable.insertSingleRow(session, store, data, null);
         performIntegrityChecks(session, baseTable, null, data, null);
 
         if (session.database.isReferentialIntegrity()) {
             for (int i = 0, size = baseTable.fkConstraints.length; i < size;
                     i++) {
                 baseTable.fkConstraints[i].checkInsert(session, baseTable,
                                                        data, true);
             }
         }
 
         if (baseTable.triggerLists[Trigger.INSERT_AFTER_ROW].length > 0) {
             baseTable.fireTriggers(session, Trigger.INSERT_AFTER_ROW, null,
                                    data, null);
         }
 
         if (baseTable.triggerLists[Trigger.INSERT_AFTER].length > 0) {
             baseTable.fireTriggers(session, Trigger.INSERT_AFTER,
                                    (RowSetNavigator) null);
         }
 
         return Result.updateOneResult;
     }
 
     Object[] getInsertData(Session session, Type[] colTypes,
                            Expression[] rowArgs) {
 
         Object[] data = baseTable.getNewRowData(session);
 
         session.sessionData.startRowProcessing();
 
         for (int i = 0; i < rowArgs.length; i++) {
             Expression e        = rowArgs[i];
             int        colIndex = insertColumnMap[i];
 
             if (e.opType == OpTypes.DEFAULT) {
                 if (baseTable.identityColumn == colIndex) {
                     continue;
                 }
 
                 if (baseTable.colDefaults[colIndex] != null) {
                     data[colIndex] =
                         baseTable.colDefaults[colIndex].getValue(session);
 
                     continue;
                 }
 
                 continue;
             }
 
             Object value = e.getValue(session);
             Type   type  = colTypes[colIndex];
 
             if (colTypes[colIndex] != e.dataType) {
                 value = type.convertToType(session, value, e.dataType);
             }
 
             data[colIndex] = value;
         }
 
         return data;
     }
 
     /**
      * Highest level multiple row update method.<p>
      *
      * Following clauses from SQL Standard section 11.8 are enforced 9) Let ISS
      * be the innermost SQL-statement being executed. 10) If evaluation of these
      * General Rules during the execution of ISS would cause an update of some
      * site to a value that is distinct from the value to which that site was
      * previously updated during the execution of ISS, then an exception
      * condition is raised: triggered data change violation. 11) If evaluation
      * of these General Rules during the execution of ISS would cause deletion
      * of a row containing a site that is identified for replacement in that
      * row, then an exception condition is raised: triggered data change
      * violation.
      *
      * @param session Session
      * @param table Table
      * @param updateList RowSetNavigatorDataChange
      * @return int
      */
     int update(Session session, Table table,
                RowSetNavigatorDataChange navigator) {
 
         int rowCount = navigator.getSize();
 
         // set identity column where null and check columns
         for (int i = 0; i < rowCount; i++) {
             navigator.next();
 
             Object[] data = navigator.getCurrentChangedData();
 
             /**
              * @todo 1.9.0 - make optional using database property -
              * this means the identity column can be set to null to force
              * creation of a new identity value
              */
             table.setIdentityColumn(session, data);
             table.setGeneratedColumns(session, data);
         }
 
         navigator.beforeFirst();
 
         if (table.fkMainConstraints.length > 0) {
             HashSet path = session.sessionContext.getConstraintPath();
 
             for (int i = 0; i < rowCount; i++) {
                 Row      row  = navigator.getNextRow();
                 Object[] data = navigator.getCurrentChangedData();
 
                 performReferentialActions(session, table, navigator, row,
                                           data, this.updateColumnMap, path);
                 path.clear();
             }
 
             navigator.beforeFirst();
         }
 
         for (int i = 0; i < navigator.getSize(); i++) {
             Row      row            = navigator.getNextRow();
             Object[] data           = navigator.getCurrentChangedData();
             int[]    changedColumns = navigator.getCurrentChangedColumns();
             Table    currentTable   = ((Table) row.getTable());
 
             if (currentTable.triggerLists[Trigger.UPDATE_BEFORE_ROW].length
                     > 0) {
                 currentTable.fireTriggers(session, Trigger.UPDATE_BEFORE_ROW,
                                           row.getData(), data, changedColumns);
                 currentTable.enforceRowConstraints(session, data);
             }
         }
 
         if (table.isView) {
             return rowCount;
         }
 
         navigator.beforeFirst();
 
         for (int i = 0; i < navigator.getSize(); i++) {
             Row   row            = navigator.getNextRow();
             Table currentTable   = ((Table) row.getTable());
             int[] changedColumns = navigator.getCurrentChangedColumns();
 
             session.addDeleteAction(currentTable, row, changedColumns);
         }
 
         navigator.beforeFirst();
 
         for (int i = 0; i < navigator.getSize(); i++) {
             Row             row          = navigator.getNextRow();
             Object[]        data         = navigator.getCurrentChangedData();
             Table           currentTable = ((Table) row.getTable());
             int[] changedColumns = navigator.getCurrentChangedColumns();
             PersistentStore store        = currentTable.getRowStore(session);
 
             if (data == null) {
                 continue;
             }
 
             Row newRow = currentTable.insertSingleRow(session, store, data,
                 changedColumns);
 
 //            newRow.rowAction.updatedAction = row.rowAction;
         }
 
         navigator.beforeFirst();
 
         OrderedHashSet extraUpdateTables = null;
         boolean hasAfterRowTriggers =
             table.triggerLists[Trigger.UPDATE_AFTER_ROW].length > 0;
 
         for (int i = 0; i < navigator.getSize(); i++) {
             Row      row            = navigator.getNextRow();
             Table    currentTable   = ((Table) row.getTable());
             Object[] changedData    = navigator.getCurrentChangedData();
             int[]    changedColumns = navigator.getCurrentChangedColumns();
 
             performIntegrityChecks(session, currentTable, row.getData(),
                                    changedData, changedColumns);
 
             if (currentTable != table) {
                 if (extraUpdateTables == null) {
                     extraUpdateTables = new OrderedHashSet();
                 }
 
                 extraUpdateTables.add(currentTable);
 
                 if (currentTable.triggerLists[Trigger.UPDATE_AFTER_ROW].length
                         > 0) {
                     hasAfterRowTriggers = true;
                 }
             }
         }
 
         navigator.beforeFirst();
 
         if (hasAfterRowTriggers) {
             for (int i = 0; i < navigator.getSize(); i++) {
                 Row      row            = navigator.getNextRow();
                 Object[] changedData    = navigator.getCurrentChangedData();
                 int[]    changedColumns = navigator.getCurrentChangedColumns();
                 Table    currentTable   = ((Table) row.getTable());
 
                 currentTable.fireTriggers(session, Trigger.UPDATE_AFTER_ROW,
                                           row.getData(), changedData,
                                           changedColumns);
             }
 
             navigator.beforeFirst();
         }
 
         baseTable.fireTriggers(session, Trigger.UPDATE_AFTER, navigator);
 
         if (extraUpdateTables != null) {
             for (int i = 0; i < extraUpdateTables.size(); i++) {
                 Table currentTable = (Table) extraUpdateTables.get(i);
 
                 currentTable.fireTriggers(session, Trigger.UPDATE_AFTER,
                                           navigator);
             }
         }
 
         return rowCount;
     }
 
     /**
      * Executes a DELETE statement.
      *
      * @return the result of executing the statement
      */
     Result executeDeleteStatement(Session session) {
 
         int count = 0;
         RangeIterator it = RangeVariable.getIterator(session,
             targetRangeVariables);
         RowSetNavigatorDataChange navigator = new RowSetNavigatorDataChange();
 
         while (it.next()) {
             Row currentRow = it.getCurrentRow();
 
             navigator.addRow(currentRow);
         }
 
         it.release();
 
         if (navigator.getSize() > 0) {
             count = delete(session, baseTable, navigator);
         } else {
             return Result.updateZeroResult;
         }
 
         if (count == 1) {
             return Result.updateOneResult;
         }
 
         return new Result(ResultConstants.UPDATECOUNT, count);
     }
 
     Result executeDeleteTruncateStatement(Session session) {
 
         PersistentStore store = targetTable.getRowStore(session);
         RowIterator     it    = targetTable.getPrimaryIndex().firstRow(store);
 
         try {
             while (it.hasNext()) {
                 Row row = it.getNextRow();
 
                 session.addDeleteAction((Table) row.getTable(), row, null);
             }
 
             if (restartIdentity && targetTable.identitySequence != null) {
                 targetTable.identitySequence.reset();
             }
         } finally {
             it.release();
         }
 
         return Result.updateOneResult;
     }
 
     /**
      *  Highest level multiple row delete method. Corresponds to an SQL
      *  DELETE.
      */
     int delete(Session session, Table table,
                RowSetNavigatorDataChange navigator) {
 
         int rowCount = navigator.getSize();
 
         navigator.beforeFirst();
 
         if (table.fkMainConstraints.length > 0) {
             HashSet path = session.sessionContext.getConstraintPath();
 
             for (int i = 0; i < rowCount; i++) {
                 navigator.next();
 
                 Row row = navigator.getCurrentRow();
 
                 performReferentialActions(session, table, navigator, row,
                                           null, null, path);
                 path.clear();
             }
 
             navigator.beforeFirst();
         }
 
         while (navigator.hasNext()) {
             navigator.next();
 
             Row      row            = navigator.getCurrentRow();
             Object[] changedData    = navigator.getCurrentChangedData();
             int[]    changedColumns = navigator.getCurrentChangedColumns();
             Table    currentTable   = ((Table) row.getTable());
 
             if (changedData == null) {
                 currentTable.fireTriggers(session, Trigger.DELETE_BEFORE_ROW,
                                           row.getData(), null, null);
             } else {
                 currentTable.fireTriggers(session, Trigger.UPDATE_BEFORE_ROW,
                                           row.getData(), changedData,
                                           changedColumns);
             }
         }
 
         if (table.isView) {
             return rowCount;
         }
 
         navigator.beforeFirst();
 
         boolean hasUpdate = false;
 
         for (int i = 0; i < navigator.getSize(); i++) {
             Row      row          = navigator.getNextRow();
             Object[] data         = navigator.getCurrentChangedData();
             Table    currentTable = ((Table) row.getTable());
 
             session.addDeleteAction(currentTable, row, null);
 
             if (data != null) {
                 hasUpdate = true;
             }
         }
 
         navigator.beforeFirst();
 
         if (hasUpdate) {
             for (int i = 0; i < navigator.getSize(); i++) {
                 Row             row          = navigator.getNextRow();
                 Object[]        data = navigator.getCurrentChangedData();
                 Table           currentTable = ((Table) row.getTable());
                 int[] changedColumns = navigator.getCurrentChangedColumns();
                 PersistentStore store = currentTable.getRowStore(session);
 
                 if (data == null) {
                     continue;
                 }
 
                 Row newRow = currentTable.insertSingleRow(session, store,
                     data, changedColumns);
 
 //                newRow.rowAction.updatedAction = row.rowAction;
             }
 
             navigator.beforeFirst();
         }
 
         OrderedHashSet extraUpdateTables = null;
         OrderedHashSet extraDeleteTables = null;
         boolean hasAfterRowTriggers =
             table.triggerLists[Trigger.DELETE_AFTER_ROW].length > 0;
 
         if (rowCount != navigator.getSize()) {
             while (navigator.hasNext()) {
                 navigator.next();
 
                 Row      row            = navigator.getCurrentRow();
                 Object[] changedData    = navigator.getCurrentChangedData();
                 int[]    changedColumns = navigator.getCurrentChangedColumns();
                 Table    currentTable   = ((Table) row.getTable());
 
                 if (changedData != null) {
                     performIntegrityChecks(session, currentTable,
                                            row.getData(), changedData,
                                            changedColumns);
                 }
 
                 if (currentTable != table) {
                     if (changedData == null) {
                         if (currentTable.triggerLists[Trigger.DELETE_AFTER_ROW]
                                 .length > 0) {
                             hasAfterRowTriggers = true;
                         }
 
                         if (extraDeleteTables == null) {
                             extraDeleteTables = new OrderedHashSet();
                         }
 
                         extraDeleteTables.add(currentTable);
                     } else {
                         if (currentTable.triggerLists[Trigger.UPDATE_AFTER_ROW]
                                 .length > 0) {
                             hasAfterRowTriggers = true;
                         }
 
                         if (extraUpdateTables == null) {
                             extraUpdateTables = new OrderedHashSet();
                         }
 
                         extraUpdateTables.add(currentTable);
                     }
                 }
             }
 
             navigator.beforeFirst();
         }
 
         if (hasAfterRowTriggers) {
             while (navigator.hasNext()) {
                 navigator.next();
 
                 Row      row          = navigator.getCurrentRow();
                 Object[] changedData  = navigator.getCurrentChangedData();
                 Table    currentTable = ((Table) row.getTable());
 
                 if (changedData == null) {
                     currentTable.fireTriggers(session,
                                               Trigger.DELETE_AFTER_ROW,
                                               row.getData(), null, null);
                 } else {
                     currentTable.fireTriggers(session,
                                               Trigger.UPDATE_AFTER_ROW,
                                               row.getData(), changedData,
                                               null);
                 }
             }
 
             navigator.beforeFirst();
         }
 
         table.fireTriggers(session, Trigger.DELETE_AFTER, navigator);
 
         if (extraUpdateTables != null) {
             for (int i = 0; i < extraUpdateTables.size(); i++) {
                 Table currentTable = (Table) extraUpdateTables.get(i);
 
                 currentTable.fireTriggers(session, Trigger.UPDATE_AFTER,
                                           navigator);
             }
         }
 
         if (extraDeleteTables != null) {
             for (int i = 0; i < extraDeleteTables.size(); i++) {
                 Table currentTable = (Table) extraDeleteTables.get(i);
 
                 currentTable.fireTriggers(session, Trigger.DELETE_AFTER,
                                           navigator);
             }
         }
 
         return rowCount;
     }
 
     static void performIntegrityChecks(Session session, Table table,
                                        Object[] oldData, Object[] newData,
                                        int[] updatedColumns) {
 
         if (newData == null) {
             return;
         }
 
         for (int i = 0, size = table.checkConstraints.length; i < size; i++) {
             table.checkConstraints[i].checkInsert(session, table, newData,
                                                   oldData == null);
         }
 
         if (!session.database.isReferentialIntegrity()) {
             return;
         }
 
         for (int i = 0, size = table.fkConstraints.length; i < size; i++) {
             boolean    check = oldData == null;
             Constraint c     = table.fkConstraints[i];
 
             if (!check) {
                 check = ArrayUtil.haveCommonElement(c.getRefColumns(),
                                                     updatedColumns);
             }
 
             if (check) {
                 c.checkInsert(session, table, newData, oldData == null);
             }
         }
     }
 
     static void performReferentialActions(Session session, Table table,
                                           RowSetNavigatorDataChange navigator,
                                           Row row, Object[] data,
                                           int[] changedCols, HashSet path) {
 
         if (!session.database.isReferentialIntegrity()) {
             return;
         }
 
         boolean delete = data == null;
 
         for (int i = 0, size = table.fkMainConstraints.length; i < size; i++) {
             Constraint c      = table.fkMainConstraints[i];
             int        action = delete ? c.core.deleteAction
                                        : c.core.updateAction;
 
             if (!delete) {
                 if (!ArrayUtil.haveCommonElement(changedCols,
                                                  c.core.mainCols)) {
                     continue;
                 }
 
                 if (c.core.mainIndex.compareRowNonUnique(
                         session, row.getData(), data, c.core.mainCols) == 0) {
                     continue;
                 }
             }
 
             RowIterator refiterator = c.findFkRef(session, row.getData());
 
             if (!refiterator.hasNext()) {
                 refiterator.release();
 
                 continue;
             }
 
             while (refiterator.hasNext()) {
                 Row      refRow  = refiterator.getNextRow();
                 Object[] refData = null;
 
                 /** @todo use MATCH */
                 if (c.core.refIndex.compareRowNonUnique(
                         session, refRow.getData(), row.getData(),
                         c.core.mainCols) != 0) {
                     break;
                 }
 
                 if (delete && refRow.getId() == row.getId()) {
                     continue;
                 }
 
                 switch (action) {
 
                     case SchemaObject.ReferentialAction.CASCADE : {
                         if (delete) {
                             if (navigator.addRow(refRow)) {
                                 performReferentialActions(session,
                                                           c.core.refTable,
                                                           navigator, refRow,
                                                           null, null, path);
                             }
 
                             continue;
                         }
 
                         refData = c.core.refTable.getEmptyRowData();
 
                         System.arraycopy(refRow.getData(), 0, refData, 0,
                                          refData.length);
 
                         for (int j = 0; j < c.core.refCols.length; j++) {
                             refData[c.core.refCols[j]] =
                                 data[c.core.mainCols[j]];
                         }
 
                         break;
                     }
                     case SchemaObject.ReferentialAction.SET_NULL : {
                         refData = c.core.refTable.getEmptyRowData();
 
                         System.arraycopy(refRow.getData(), 0, refData, 0,
                                          refData.length);
 
                         for (int j = 0; j < c.core.refCols.length; j++) {
                             refData[c.core.refCols[j]] = null;
                         }
 
                         break;
                     }
                     case SchemaObject.ReferentialAction.SET_DEFAULT : {
                         refData = c.core.refTable.getEmptyRowData();
 
                         System.arraycopy(refRow.getData(), 0, refData, 0,
                                          refData.length);
 
                         for (int j = 0; j < c.core.refCols.length; j++) {
                             ColumnSchema col =
                                 c.core.refTable.getColumn(c.core.refCols[j]);
 
                             refData[c.core.refCols[j]] =
                                 col.getDefaultValue(session);
                         }
 
                         break;
                     }
                     case SchemaObject.ReferentialAction.NO_ACTION :
                     case SchemaObject.ReferentialAction.RESTRICT : {
                         if (navigator.containsDeletedRow(refRow)) {
                             continue;
                         }
 
                         int errorCode = c.core.deleteAction
                                         == SchemaObject.ReferentialAction
                                             .NO_ACTION ? ErrorCode.X_23504
                                                        : ErrorCode.X_23001;
                         String[] info = new String[] {
                             c.core.refName.name, c.core.refTable.getName().name
                         };
 
                         refiterator.release();
 
                         throw Error.error(null, errorCode,
                                           ErrorCode.CONSTRAINT, info);
                     }
                     default :
                         continue;
                 }
 
                 refData = navigator.addRow(session, refRow, refData,
                                            table.getColumnTypes(),
                                            c.core.refCols);
 
                 if (!path.add(c)) {
                     continue;
                 }
 
                 performReferentialActions(session, c.core.refTable, navigator,
                                           refRow, refData, c.core.refCols,
                                           path);
                 path.remove(c);
             }
 
             refiterator.release();
         }
     }
 }
 