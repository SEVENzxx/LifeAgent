package com.lifeagent.common;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;

/**
 * 集成测试数据库名称校验工具。
 *
 * <p>所有破坏性清理（DELETE／TRUNCATE）执行前必须调用 {@link #requireTestDatabase(DataSource)}，
 * 确保当前连接的数据库是 {@code lifeagent_test}，防止误操作本地开发数据库。</p>
 */
public final class DatabaseNameValidator {

    private static final String EXPECTED_DATABASE = "lifeagent_test";

    private DatabaseNameValidator() {
    }

    /**
     * 校验当前数据库是否为 lifeagent_test。
     *
     * @throws IllegalStateException 如果不是 lifeagent_test
     */
    public static void requireTestDatabase(DataSource dataSource) {
        String actual = getCurrentDatabase(dataSource);
        if (!EXPECTED_DATABASE.equals(actual)) {
            throw new IllegalStateException(
                    "集成测试禁止操作非测试数据库：" + actual
                            + "，期望：" + EXPECTED_DATABASE
                            + "。请检查 application-test.yml 的 datasource URL。");
        }
    }

    private static String getCurrentDatabase(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_database()")) {
            rs.next();
            return rs.getString(1);
        } catch (Exception e) {
            throw new RuntimeException("无法获取当前数据库名称", e);
        }
    }
}
