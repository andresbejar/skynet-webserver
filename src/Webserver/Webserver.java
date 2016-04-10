package Webserver;

import java.io.*;
import java.util.*;
import java.net.*;

public class Webserver {
	
	public Webserver(){
		//ocupa algo? guardar el puerto? dunno...
	}

	
	protected static void launch(int port){
		System.out.println("Starting webserver on port " + port);
		
		try(ServerSocket server = new ServerSocket(port)){ //autocloseable FTW
			int id = 0;
			
			//ahora el servidor espera por una conexion
			while(true){
				Socket connection = null;
				try{
					connection = server.accept();
					System.out.println("Incoming connection...");
					/*try{
						BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
						PrintWriter out = new PrintWriter(connection.getOutputStream());
						System.out.println("Opened I/O sockets...");
						//read request headers
						String request = ".";
						while(!request.equals("")){
							request = in.readLine();
							
							//podria procesar linea por linea o guardar todo y despues parsear
							System.out.println(request); //placeholder
						}
						out.println("HTTP/1.1 200 OK");
						//out.println("Date: " + getServerTime());
						out.println("Server: Skynet Webserver 1.0");
						out.println("Content-type: text/html");
						out.println("");
						out.println("<h1>Hello humans!</h1>");
						out.flush();
						
						//cleanup
						out.close();
						in.close();
						connection.close();
					}
					catch(Exception e){
						System.err.println("Error processing request: " + e.toString());
						return;
					}*/
					WebServlet servlet = new WebServlet(connection, id++);
					servlet.start();
				}
				catch(Exception e){
					System.err.println("Error: connection refused.");
					return;
				}
			}
		}
		catch (Exception e){
			System.err.println("Error opening server: " + e.toString());
			return;
		}
		
	}

	public static void main(String[] args) {
		int port;
		if(args.length != 1){
			System.err.println("Usage: java Webserver <port>");
			System.err.println("Defaulting to port 8080");
			port = 8080;
		}
		else{
			port = new Integer(args[0]);
		}
		launch(port);
	}

}
