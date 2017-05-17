package chat;

import java.net.*;
import java.io.*;
import java.util.*;
import java.sql.*;

public class ChatServer {

    private final int port;
    private ServerSocket server;
    
    private Map<String, PrintWriter> clients = new HashMap<String, PrintWriter>();
    private Map<String, Integer> currentRoom = new HashMap<String, Integer>();
    
    public int getPort() {
        return port;
    }
    
   
    
    ChatServer(int port) {
        this.port = port;
        try {
            server = new ServerSocket(port);
            System.out.println("A chat server elindult.");
        } catch (IOException e) {
            System.err.println("Hiba a chat server inditasanal.");
            e.printStackTrace();
        }
    }
        
    public void handleClients() {
        while (true) {
            try {
                new ClientHandler(server.accept()).start();
            } catch (IOException e) {
                System.err.println("Hiba a kliensek fogadasakor.");
                e.printStackTrace();
            }
        }
    }
    
        
    //Bejelentkezése
    private synchronized boolean addClient(String name, String password, PrintWriter pw) {
        if (clients.get(name) != null) return false;
        try{
	        //Class.forName("org.hsqldb.jdbc.JDBCDriver");
	        Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:chat.db");
	        //Statement stat = conn.createStatement();
	        
	        String query = "select * from users where username = ?;";
	        PreparedStatement prepStmt = conn.prepareStatement(query);
	        prepStmt.setString(1, name);
	        ResultSet rs = prepStmt.executeQuery();
	    
	        
	        if(rs.next()){
	        	System.out.println(rs.getString(1)+ ": " + rs.getString(2));
		        if(rs.getString("password").equals(password)){
		        	clients.put(name, pw);
		        	currentRoom.put(name, -1);
			        send(name, "bejelentkezett.", "-1");
			        rs.close();
			        return true;
		        }else{
		        	System.out.println("Hibas jelszo");
		        }
	        } else {
	        	rs.close();
	        	return false;
	        }
        } catch (Exception e){
        	System.err.println("Hiba a kliensek fogadasakor.");
            e.printStackTrace();
        }
        return false;
    }
        
    private synchronized void removeClient(String name) {
        clients.remove(name);
        currentRoom.remove(name);
    }
    

        
    //altalanos kuldes, a kliens uzenetet tovabbitja a tobbi kliensnek, az uzeneteket elmenti az adatbazisba    
    private synchronized void send(String name, String message, String room) {
        System.out.println(name + ": " + message);
        
        for (String n : clients.keySet()) {
        	if (! n.equals(name)) {
        		clients.get(n).println(name + ": " + message);
        	}
        }
    }
    
    //az adott group korabbi uzeneteit osszeszedi, alkuldi a megadott usernek
    private synchronized void sendPrevMessages(Integer groupId, String name) {
        try{
        	//Class.forName("org.hsqldb.jdbc.JDBCDriver");
	        Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:chat.db");
	        Statement stat = conn.createStatement();
	        
	        String query = "select * from messages where chat_group_id = ?;";
	        PreparedStatement prepStmt = conn.prepareStatement(query);
	        prepStmt.setInt(1, groupId);
	        ResultSet rs = prepStmt.executeQuery();
	        
	        clients.get(name).println("PREVMESSAGES");
	        while (rs.next()) {
	        	String username = getUsername(rs.getInt("user_id"));
	            String toSend = username + ": " + rs.getString("message_text") + " : " + rs.getTimestamp("date");
	            clients.get(name).println(toSend);
	        }
	        clients.get(name).println("DONE");
	        rs.close();
	        
        } catch (Exception e){
        	System.err.println("Hiba a korabbi uzenetek betoltese kozben.");
            e.printStackTrace();
        }
        
    }
    
    //kikeresi hogy melyik felhasznalok vannak eppen benne a groupban
    private synchronized void updatePrevMessages(int groupId, String name, String message){
    	try{
        	//Class.forName("org.hsqldb.jdbc.JDBCDriver");
	        
    		Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:chat.db");
	        //Statement stat = conn.createStatement();
	        
    		int userId = getUserId(name);
    		
	        //uzenet elmentese
	        PreparedStatement prep = conn.prepareStatement("insert into messages (chat_group_id, user_id, message_text, date) values (?, ?, ?, ?);");
	        java.sql.Timestamp  sqlDate = new java.sql.Timestamp(new java.util.Date().getTime());
	        prep.setInt(1, groupId); prep.setInt(2, userId);  prep.setString(3, message); prep.setTimestamp(4, sqlDate); prep.addBatch();
	        prep.executeBatch();
    		
	        //uzenet elkuldese
	        for (String n : currentRoom.keySet()) {
	        	if (currentRoom.get(n) == groupId) {
	        		clients.get(n).println(name + ": " + message);
	        	}
	        }

        } catch (Exception e){
        	System.err.println("Hiba az uzenetek updateje kozben.");
            e.printStackTrace();
        }
    	
    }
    
    
    
    //elkuldi a kliensnek hogy kik vannak bejelentkezve (a fooldali listahoz)
    private synchronized void sendSignedInUsers(String name) {
    	clients.get(name).println("USERS");
    	for (String n : clients.keySet()) {
            if (! n.equals(name)) {
                clients.get(name).println(n + " is signed in ");
            }
        }
    	clients.get(name).println("DONE");
    }
    
    
    //az osszes felhasznalot kuldi el a kliensnek (uj felhasznalao felvetele a szobaba)
    private synchronized void sendAllUsers(String name) {
    	
    	try{
        	//Class.forName("org.hsqldb.jdbc.JDBCDriver");
	        Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:chat.db");
	        Statement stat = conn.createStatement();
	        
	        clients.get(name).println("USERS");
	        ResultSet rs = stat.executeQuery("select * from users;");
	        while (rs.next()) {
	            String toSend = rs.getInt("id") + ": " + rs.getString("username");
	            clients.get(name).println(toSend);
	        }
	        clients.get(name).println("DONE");
	        rs.close();        	
    	}catch(Exception e){
    		System.err.println("Hiba a felhasznalok listazasa kozben.");
            e.printStackTrace();
    	}
    	
    }
    
    //a kliens szamara elerheto szobakat kuldi at
    private synchronized void sendChatrooms(String name) {
    	
    	try{
        	//Class.forName("org.hsqldb.jdbc.JDBCDriver");
	        Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:chat.db");
	        Statement stat = conn.createStatement();
	        
	        clients.get(name).println("CHATROOMS");
	        PreparedStatement prepStmt = conn.prepareStatement("select * from group_user where user_id = ?;");
	        prepStmt.setInt(1, getUserId(name));
	        ResultSet rs = prepStmt.executeQuery();
	        while (rs.next()) {
	        	String groupName = getGroupName(rs.getInt("chat_group_id"));
	            String toSend = rs.getInt("chat_group_id") +  ": " + groupName;
	            clients.get(name).println(toSend);
	        }
	        clients.get(name).println("DONE");
	        rs.close();   
	        

    	}catch(Exception e){
    		System.err.println("Hiba a szobak atkuldese kozben.");
            e.printStackTrace();
    	}
    	
    }

    //chatszoba letrehozasa
    private synchronized void createRoom(String name, String userName) {
        
        try{
	        //Class.forName("org.hsqldb.jdbc.JDBCDriver");
	        Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:chat.db");
	        Statement stat = conn.createStatement();
	        
	        //szoba letrehozasa
	        PreparedStatement prep = conn.prepareStatement("insert into group_chat (name) values (?);");
	        prep.setString(1, name); prep.addBatch();
	        prep.executeBatch();
	        //letrehozonak elerheto legyen:
	        prep = conn.prepareStatement("insert into group_user (chat_group_id, user_id) values (?,?);");
	        prep.setInt(1, getGroupId(name)); prep.setInt(2, getUserId(userName)); prep.addBatch();
	        prep.executeBatch();
	        
	        
        } catch (Exception e){
        	System.err.println("Hiba a szoba letrehozasa kozben.");
            e.printStackTrace();
        }
        
    }
    
    
    //felhasznalo letrehozasa
    private synchronized void createUser(String username, String firstname, String lastname, String email, String password) {
        
        try{
	        //Class.forName("org.hsqldb.jdbc.JDBCDriver");
	        Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:chat.db");
	        Statement stat = conn.createStatement();
	        
	        PreparedStatement prep = conn.prepareStatement("insert into users (username, firstName, lastName, email, password) values (?, ?, ?, ?, ?);");
	        prep.setString(1, username); prep.setString(2, firstname); prep.setString(3, lastname); prep.setString(4, email); prep.setString(5, password); prep.addBatch();
	        prep.executeBatch();
	        System.out.println("User is created");
	        
        } catch (Exception e){
        	System.err.println("Hiba a felhasznalo letrehozasa kozben.");
            e.printStackTrace();
        }
        
    }
    
    
    
    //felhasznalo meghivasa a szobaba
	 private synchronized void inviteUser(String name, int roomId, int invitedUserId) {
	 	
	 	try{
	     	//Class.forName("org.hsqldb.jdbc.JDBCDriver");
		        Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:chat.db");
		        Statement stat = conn.createStatement();
		        
		        /*PreparedStatement prepStmt = conn.prepareStatement("select count(*) from group_user where user_id = ?;");
		        prepStmt.setInt(1, invitedUserId);
		        ResultSet rs = prepStmt.executeQuery();
		        if (rs.next()) {
		        	
		        	send(name, "User is already invited", Integer.toString(roomId));
		        	
		        	
		        }else{
		        */
			        PreparedStatement prep = conn.prepareStatement("insert into group_user (chat_group_id, user_id) values (?,?);");
			        prep.setInt(1, roomId); prep.setInt(2, invitedUserId); prep.addBatch();
			        prep.executeBatch();
		        //}
		        
	 	}catch(Exception e){
	 		System.err.println("Hiba a szobaba valo belepes kozben.");
	         e.printStackTrace();
	 	}
	 	
	 }
    
    //nev alapjan visszaadja a felhasznalo id-t
    private int getUserId(String userName){
    	int userId = -1;
    	try{
	        //Class.forName("org.hsqldb.jdbc.JDBCDriver");
	        Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:chat.db");
	        Statement stat = conn.createStatement();
	        
	        String query = "select * from users where username = ?;";
	        PreparedStatement prepStmt = conn.prepareStatement(query);
	        prepStmt.setString(1, userName);
	        ResultSet rs = prepStmt.executeQuery();
	        if(rs.next()){
	        	userId = rs.getInt("id");
	        }
	        	        
        } catch (Exception e){
        	System.err.println("Hiba.");
            e.printStackTrace();
        }
    	
    	return userId;
    }
    
    //hasonlo userre
    private int getGroupId(String groupName){
    	int groupId = -1;
    	try{
	        //Class.forName("org.hsqldb.jdbc.JDBCDriver");
	        Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:chat.db");
	        Statement stat = conn.createStatement();
	        
	        String query = "select * from group_chat where name = ?;";
	        PreparedStatement prepStmt = conn.prepareStatement(query);
	        prepStmt.setString(1, groupName);
	        ResultSet rs = prepStmt.executeQuery();
	        if(rs.next()){
	        	groupId = rs.getInt("id");
	        }
	        	        
        } catch (Exception e){
        	System.err.println("Hiba.");
            e.printStackTrace();
        }
    	
    	return groupId;
    }
    
    
    private String getUsername(Integer userId){
    	String username = "";
    	try{
	    	//Class.forName("org.hsqldb.jdbc.JDBCDriver");
	        Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:chat.db");
	        Statement stat = conn.createStatement();
	        
	        String query = "select * from users where id = ?;";
	        PreparedStatement prepStmt = conn.prepareStatement(query);
	        prepStmt.setInt(1, userId);
	        ResultSet rs = prepStmt.executeQuery();
	        if(rs.next()){
	        	username = rs.getString("username");
	        }
    	} catch (Exception e){
    		System.err.println("Hiba.");
            e.printStackTrace();
    	}
    	
    	return username;
    }
    
    
    
    private String getGroupName(Integer groupId){
    	String groupname = "";
    	try{
	    	//Class.forName("org.hsqldb.jdbc.JDBCDriver");
	        Connection conn = DriverManager.getConnection("jdbc:hsqldb:file:chat.db");
	        Statement stat = conn.createStatement();
	        
	        String query = "select * from group_chat where id = ?;";
	        PreparedStatement prepStmt = conn.prepareStatement(query);
	        prepStmt.setInt(1, groupId);
	        ResultSet rs = prepStmt.executeQuery();
	        if(rs.next()){
	        	groupname = rs.getString("name");
	        }
    	} catch (Exception e){
    		System.err.println("Hiba.");
            e.printStackTrace();
    	}
    	
    	return groupname;
    }

    
    class ClientHandler extends Thread {
                
        PrintWriter pw;
        BufferedReader br;
        String name;
        String password;
                
        ClientHandler(Socket s) {
            try {
                pw = new PrintWriter(s.getOutputStream(), true);
                br = new BufferedReader(new InputStreamReader(s.getInputStream()));
            } catch (IOException e) {
                System.err.println("Inicializalasi problema egy kliensnel.");
            }
        }
                
        @Override
        public void run() {
            String message = null;
            try {
            	//regisztracio
            	if(br.readLine().equals("2")){
            		String username = br.readLine();
            		//System.out.println(username);
            		String firstname = br.readLine();
            		//System.out.println(firstname);
            		String lastname = br.readLine();
            		//System.out.println(lastname);
            		String email = br.readLine();
            		//System.out.println(email);
            		String password = br.readLine();
            		//System.out.println(password);
            		
            		createUser(username, firstname, lastname, email, password);
            		
            	}
            	
            	//bejelentkezes
            	boolean ok = false;
                while (! ok) {
                    name = br.readLine();
                    password = br.readLine();
                    //System.out.println(password);
                    if (name == null) return;
                    ok = addClient(name, password, pw);
                    if (ok) pw.println("ok"); else pw.println("nok");
                }
            	
                //bejelentkezett felhasznalok es szobak kuldesi a kliensnek
                sendSignedInUsers(name);
                sendChatrooms(name);
                //kliens megadja hogy mit akar
                String toDo = br.readLine();
                
                while(!toDo.equals("3")){
                	
	                if(toDo.equals("1")){
	                	//Chatszoba letrehozasa
	                	String roomName = br.readLine();
	                	createRoom(roomName, name);
	                	
	                }else if(toDo.equals("2")){
	                	//Belepes chatszobaba;
	                	
	                	//Elerheto szobak elkuldese a kliensnek
	                	sendChatrooms(name);
	                	String roomId = br.readLine();
	                	
	                	//bejelentkezes a szobaba
	                	currentRoom.replace(name, Integer.parseInt(roomId));
	                	//korabbi uzenetek atkuldese a felhasznalonak
	                	sendPrevMessages(Integer.parseInt(roomId), name);
	                	//a csoportban levo osszes embernek elkuldi az infot hogy a felhasznalo belepett a szobaba
	                	updatePrevMessages(Integer.parseInt(roomId), name, " belepett.");
	                	
	                	while (! "exit".equals(message)) {
	                		if(! "INVITEUSER".equals(message)){
	                			//uzenet kuldese a szobaba
		                        message = br.readLine();
		                        if (message == null) break;
		                        updatePrevMessages(Integer.parseInt(roomId), name, message);
	                		} else {
	                			//felhasznalo meghivasa a szobaba
	                			sendAllUsers(name);
	                			message = br.readLine();
	                			inviteUser(name, Integer.parseInt(roomId), Integer.parseInt(message));
	                		}
	                    }
	                	//kilepteti a szobabol
	                	currentRoom.replace(name, -1);
	                	
	                	
	                }
	                
	                sendSignedInUsers(name);
	                sendChatrooms(name);
	                toDo = br.readLine();
                	message = "";
	                
                }

            } catch (IOException e) {
                System.err.println("Kommunikacios problema egy kliensnel. Nev: " + name);
            } finally {
                if (name != null) send(name, "kijelentkezett.", "-1");
                removeClient(name);
                try {pw.close(); br.close();} catch (IOException e) {}
            }
        }
    }
        
    public static void main(String[] args) throws Exception {
    	Class.forName("org.hsqldb.jdbc.JDBCDriver");
        ChatServer server = new ChatServer(8081);
        if (server != null) server.handleClients();
    }
        
}
