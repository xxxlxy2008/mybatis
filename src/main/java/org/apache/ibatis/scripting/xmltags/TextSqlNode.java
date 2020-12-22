/**
 * Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.scripting.xmltags;

import java.util.regex.Pattern;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.scripting.ScriptingException;
import org.apache.ibatis.type.SimpleTypeRegistry;

/**
 * @author Clinton Begin
 */
public class TextSqlNode implements SqlNode {
    private final String text;
    private final Pattern injectionFilter;

    public TextSqlNode(String text) {
        this(text, null);
    }

    public TextSqlNode(String text, Pattern injectionFilter) {
        this.text = text;
        this.injectionFilter = injectionFilter;
    }

    public boolean isDynamic() {
        DynamicCheckerTokenParser checker = new DynamicCheckerTokenParser();
        GenericTokenParser parser = createParser(checker);
        parser.parse(text);
        return checker.isDynamic();
    }

    @Override
    public boolean apply(DynamicContext context) {
        // 创建GenericTokenParser解析器，这里指定的占位符的起止符号分别是"${"和"}"
        GenericTokenParser parser = createParser(
                new BindingTokenParser(context, injectionFilter));
        // 将解析之后的SQL片段追加到DynamicContext暂存
        context.appendSql(parser.parse(text));
        return true;
    }

    private GenericTokenParser createParser(TokenHandler handler) {
        return new GenericTokenParser("${", "}", handler);
    }

    private static class BindingTokenParser implements TokenHandler {

        private DynamicContext context;
        private Pattern injectionFilter;

        public BindingTokenParser(DynamicContext context, Pattern injectionFilter) {
            this.context = context;
            this.injectionFilter = injectionFilter;
        }

        @Override
        public String handleToken(String content) {
            // 获取用户提供的实参数据
            Object parameter = context.getBindings().get("_parameter");
            if (parameter == null) { // 通过value占位符，也可以查找到parameter对象
                context.getBindings().put("value", null);
            } else if (SimpleTypeRegistry.isSimpleType(parameter.getClass())) {
                context.getBindings().put("value", parameter);
            }
            // 通过Ognl解析"${}"占位符中的表达式
            Object value = OgnlCache.getValue(content, context.getBindings());
            String srtValue = value == null ? "" : String.valueOf(value); // issue #274 return "" instead of "null"
            checkInjection(srtValue); // 对解析后的值进行过来
            return srtValue; // 通过过滤的值才能正常返回
        }

        private void checkInjection(String value) {
            if (injectionFilter != null && !injectionFilter.matcher(value).matches()) {
                throw new ScriptingException("Invalid input. Please conform to regex" + injectionFilter.pattern());
            }
        }
    }

    private static class DynamicCheckerTokenParser implements TokenHandler {

        private boolean isDynamic;

        public DynamicCheckerTokenParser() {
            // Prevent Synthetic Access
        }

        public boolean isDynamic() {
            return isDynamic;
        }

        @Override
        public String handleToken(String content) {
            this.isDynamic = true;
            return null;
        }
    }

}
