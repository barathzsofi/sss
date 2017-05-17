package chat;

import java.sql.*;

public class JDBCTest {

    public static void main(String[] args) throws Exception {
    
        //osztaly betoltese 
        Class.forName("org.hsqldb.jdbc.JDBCDriver");
        
        //adatbazis letrehozasa
        Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:chat.db");
        
        System.out.println("adatbazis letrehozva");
        
        //tabla letrehozasa
        Statement stat = conn.createStatement();
        
        stat.executeUpdate("drop table if exists users;");
        String sql = "CREATE TABLE IF NOT EXISTS users (" +
        		"id INTEGER IDENTITY PRIMARY KEY, " + 
        		"username VARCHAR(50) UNIQUE, " +
        		"firstName VARCHAR(50), " +
        		"lastName VARCHAR(50), " +
        		"email VARCHAR(50), " +
        		"password VARCHAR(50) NOT NULL );";        
        stat.executeUpdate(sql);
        System.out.println("tabla letrehozva");
        

        
        //lekerdezes
        ResultSet rs = stat.executeQuery("select * from users;");
        while (rs.next()) {
            System.out.println(rs.getString("username") + ": " + rs.getString("password") + ": " + rs.getString("firstName") + ": " + rs.getString("lastName") + ": " + rs.getString("email"));
        }
        rs.close();
        
        stat.executeUpdate("drop table if exists group_chat;");
        stat.executeUpdate("CREATE TABLE IF NOT EXISTS group_chat (id INTEGER IDENTITY PRIMARY KEY, name VARCHAR(50) UNIQUE);");
        
        rs = stat.executeQuery("select * from group_chat;");
        while (rs.next()) {
            System.out.println(rs.getInt("id") + ": " + rs.getString("name"));
        }
        rs.close();
        
        stat.executeUpdate("drop table if exists messages;");
        stat.executeUpdate("CREATE TABLE IF NOT EXISTS messages (id INTEGER IDENTITY PRIMARY KEY, chat_group_id INTEGER NOT NULL, user_id INTEGER NOT NULL, message_text VARCHAR(200) NOT NULL, date TIMESTAMP NOT NULL);");
        
      //lekerdezes
        rs = stat.executeQuery("select * from messages;");
        while (rs.next()) {
            System.out.println(rs.getInt("chat_group_id") + ": " + rs.getInt("user_id") + ": " + rs.getString("message_text") + ": " + rs.getTimestamp("date"));
        }
        rs.close();
        
        stat.executeUpdate("drop table if exists group_user;");
        stat.executeUpdate("CREATE TABLE IF NOT EXISTS group_user (id INTEGER IDENTITY PRIMARY KEY, chat_group_id INTEGER NOT NULL, user_id INTEGER NOT NULL);");
        
        rs = stat.executeQuery("select * from group_user;");
        while (rs.next()) {
            System.out.println(rs.getInt("chat_group_id") + ": " + rs.getInt("user_id"));
        }
        rs.close();

        //lezaras
        stat.close();
        conn.close();
    }
}