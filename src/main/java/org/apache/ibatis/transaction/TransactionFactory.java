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
package org.apache.ibatis.transaction;

import java.sql.Connection;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.session.TransactionIsolationLevel;

/**
 * Creates {@link Transaction} instances.
 *
 * @author Clinton Begin
 */
public interface TransactionFactory {
    // 对TransactionFactory对象进行配置，一般紧跟在TransactionFactory对象初始化之后，
    // 该方法主要是完成对TransactionFactory工厂的一些自定义配置
    default void setProperties(Properties props) {
    }

    // 在指定的数据库连接之上创建一个Transaction对象
    Transaction newTransaction(Connection conn);

    // 从指定数据源中获取数据库连接，并在此数据库连接之上创建一个关联的Transaction对象
    Transaction newTransaction(DataSource dataSource,
                               TransactionIsolationLevel level, boolean autoCommit);
}
