/**
 * Copyright 2009-2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.logging.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * ResultSet proxy to add logging.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 *
 */
public final class ResultSetLogger extends BaseJdbcLogger implements InvocationHandler {

    private static final Set<Integer> BLOB_TYPES = new HashSet<>();
    private boolean first = true;
    private int rows;
    private final ResultSet rs;
    private final Set<Integer> blobColumns = new HashSet<>();

    static {
        BLOB_TYPES.add(Types.BINARY);
        BLOB_TYPES.add(Types.BLOB);
        BLOB_TYPES.add(Types.CLOB);
        BLOB_TYPES.add(Types.LONGNVARCHAR);
        BLOB_TYPES.add(Types.LONGVARBINARY);
        BLOB_TYPES.add(Types.LONGVARCHAR);
        BLOB_TYPES.add(Types.NCLOB);
        BLOB_TYPES.add(Types.VARBINARY);
    }

    private ResultSetLogger(ResultSet rs, Log statementLog, int queryStack) {
        super(statementLog, queryStack);
        this.rs = rs;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
        try {
            if (Object.class.equals(method.getDeclaringClass())) {
                // 如果调用Object的方法，则直接调用，不做任何其他处理
                return method.invoke(this, params);
            }
            Object o = method.invoke(rs, params);
            // 针对ResultSet.next()方法进行后置处理
            if ("next".equals(method.getName())) {
                if ((Boolean) o) { // 检测next()方法的返回值，确定是否还存在下一行数据
                    rows++; // 记录ResultSet中的行数
                    if (isTraceEnabled()) {
                        // 获取数据集的列元数据
                        ResultSetMetaData rsmd = rs.getMetaData();
                        // 获取数据集的列数
                        final int columnCount = rsmd.getColumnCount();
                        if (first) { // 如果是数据集的第一行数据，这会输出表头信息
                            first = false;
                            // 这里除了输出表头，记录BLOB等超大类型的列名
                            printColumnHeaders(rsmd, columnCount);
                        }
                        // 输出当前遍历的这行记录，这里会过滤掉超大类型列的数据，不进行输出
                        printColumnValues(columnCount);
                    }
                } else { // 完成结果集的遍历之后，这里会在日志中会输出总函数
                    debug("     Total: " + rows, false);
                }
            }
            clearColumnInfo(); // 清空column*集合
            return o;
        } catch (Throwable t) {
            throw ExceptionUtil.unwrapThrowable(t);
        }
    }

    private void printColumnHeaders(ResultSetMetaData rsmd, int columnCount) throws SQLException {
        StringJoiner row = new StringJoiner(", ", "   Columns: ", "");
        for (int i = 1; i <= columnCount; i++) {
            if (BLOB_TYPES.contains(rsmd.getColumnType(i))) {
                blobColumns.add(i);
            }
            row.add(rsmd.getColumnLabel(i));
        }
        trace(row.toString(), false);
    }

    private void printColumnValues(int columnCount) {
        StringJoiner row = new StringJoiner(", ", "       Row: ", "");
        for (int i = 1; i <= columnCount; i++) {
            try {
                if (blobColumns.contains(i)) {
                    row.add("<<BLOB>>");
                } else {
                    row.add(rs.getString(i));
                }
            } catch (SQLException e) {
                // generally can't call getString() on a BLOB column
                row.add("<<Cannot Display>>");
            }
        }
        trace(row.toString(), false);
    }

    /**
     * Creates a logging version of a ResultSet.
     *
     * @param rs
     *          the ResultSet to proxy
     * @param statementLog
     *          the statement log
     * @param queryStack
     *          the query stack
     * @return the ResultSet with logging
     */
    public static ResultSet newInstance(ResultSet rs, Log statementLog, int queryStack) {
        InvocationHandler handler = new ResultSetLogger(rs, statementLog, queryStack);
        ClassLoader cl = ResultSet.class.getClassLoader();
        return (ResultSet) Proxy.newProxyInstance(cl, new Class[]{ResultSet.class}, handler);
    }

    /**
     * Get the wrapped result set.
     *
     * @return the resultSet
     */
    public ResultSet getRs() {
        return rs;
    }

}
