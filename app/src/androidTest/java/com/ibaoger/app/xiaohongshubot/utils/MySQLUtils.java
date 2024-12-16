package com.ibaoger.app.xiaohongshubot.utils;

import com.ibaoger.app.xiaohongshubot.Config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// SQL 工具类
// 使用示例：
// new Thread(new Runnable() {
//    @Override
//    public void run() {
//        DatabaseHelper dbHelper = new DatabaseHelper();
//        String sql = "SELECT * FROM 表名 WHERE 列名 = ?";
//        List<Map<String, Object>> results = dbHelper.executeQuery(sql, "参数值");
//
//        // 处理查询结果
//        for (Map<String, Object> row : results) {
//            // 根据列名获取数据
//            Object data = row.get("列名");
//            Log.d("数据", data.toString());
//        }
//
//        // 在需要更新 UI 的地方
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                // 更新 UI，例如显示结果数量
//                Toast.makeText(MainActivity.this, "查询到 " + results.size() + " 条记录", Toast.LENGTH_LONG).show();
//            }
//        });
//    }
//}).start();
public class MySQLUtils {

    // 加载 JDBC 驱动
    static {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // 获取数据库连接
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(Config.MYSQL_URL, Config.MYSQL_USER, Config.MYSQL_PASSWORD);
    }

    // 执行查询并返回结果
    public List<Map<String, Object>> executeQuery(String sql, Object... params) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // 设置参数
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            // 执行查询
            ResultSet rs = pstmt.executeQuery();

            // 获取结果集的元数据
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // 处理结果集
            while (rs.next()) {
                Map<String, Object> rowData = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    rowData.put(columnName, value);
                }
                resultList.add(rowData);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return resultList;
    }

    // 执行更新操作（INSERT、UPDATE、DELETE）
    public int executeUpdate(String sql, Object... params) {
        int affectedRows = 0;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // 设置参数
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            // 执行更新
            affectedRows = pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return affectedRows;
    }

    public int executeInsert(String sql, Object... params) {
        return this.executeUpdate(sql, params);
    }

    public int executeDelete(String sql, Object... params) {
        return this.executeUpdate(sql, params);
    }

}
