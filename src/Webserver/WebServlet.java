package Webserver;
import java.util.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import javax.activation.*;
import java.util.logging.*;

public class WebServlet extends Thread {

	protected Socket websocket;
	protected int id;
	protected PrintWriter out;
	protected Logger logger;
	
	
	public WebServlet(Socket socket, int id){
		this.websocket = socket;
		this.id = id;
	}
	
	private void openLog(){
		logger = Logger.getLogger("SkynetLogger");
		FileHandler fh;
		try{
			fh = new FileHandler("log.txt", true);
			logger.addHandler(fh);
			SimpleFormatter format = new SimpleFormatter();
			fh.setFormatter(format);
		}
		catch(Exception e){
			System.err.println("An error has occured opening the log: " + e.toString());
		}
	}
	
	//este es el metodo que corre cuando el main thread le da start()
	//procesa los HTTP requests
	public void run(){
		System.out.println("Client ID: " + this.id);
		StringBuilder logstring = new StringBuilder("Request: \n");
		try{
			//open log
			openLog();
			BufferedReader in = new BufferedReader(new InputStreamReader(this.websocket.getInputStream()));
			out = new PrintWriter(this.websocket.getOutputStream());
			System.out.println("Opened I/O sockets...");
			
			//read request headers
			ArrayList<String> httpHeaders = new ArrayList<String>();
			String request = in.readLine();
			logstring.append(request);
			logstring.append("\n");
			while(!request.equals("")){ //lee hasta llegar a un blankline
				httpHeaders.add(request);
				//podria procesar linea por linea o guardar todo y despues parsear
				//System.out.println(request); //placeholder
				request = in.readLine();
				logstring.append(request);
				logstring.append("\n");
			}
			
			
			
			//TODO: read body! in queda justo antes del body
			//Log request into a logfile!
			logger.info(logstring.toString());
			
			
			request = httpHeaders.get(0); //check first header
			
			//check for HTTP verb and respond accordingly
			if(request.startsWith("GET")){
				System.out.println("Processing a GET request...");
				httpGet(httpHeaders);
			}
			else if(request.startsWith("POST")){
				System.out.println("Processing a POST request...");
				//find content-length
				int contentLength = 0;
				for(int i = 0; i < httpHeaders.size(); i++){
					if(httpHeaders.get(i).startsWith("Content-Length:")){
						request = httpHeaders.get(i);
						contentLength = Integer.parseInt(request.substring(16));
						break;
					}
				}
				char [] body = new char[contentLength];
				in.read(body, 0, contentLength);
				String httpBody = new String(body);
				System.out.println("Req body: \n" + httpBody);
				httpPost(httpHeaders, httpBody);
			}
			else if(request.startsWith("HEAD")){
				System.out.println("Processing a HEAD request...");
				httpHead(httpHeaders);
			}
			else{
				returnStatusCode(405);		
			}
			//cleanup
			out.close();
			in.close();
			this.websocket.close();
		}
		catch(Exception e){
			System.err.println("ID: " + this.id);
			System.err.println("Error processing request: " + e.toString());
			return;
		}
	}
	
	public String getServerTime() {
	    Calendar calendar = Calendar.getInstance();
	    SimpleDateFormat dateFormat = new SimpleDateFormat(
	        "EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
	    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
	    return dateFormat.format(calendar.getTime());
	}
	
	public void httpGet(ArrayList<String> httpHeaders){
		//reviso si el request es valido (el resource existe (404), http version es correcta (400)?)
		String currentHeader = httpHeaders.get(0);
		StringBuilder logstring = new StringBuilder("\nResponse: \n");
		if(!currentHeader.endsWith("HTTP/1.1")){
			returnStatusCode(400);
			return;
		}
		
		String filename = currentHeader.substring(4, currentHeader.length()-9).trim();
		try{
			System.out.println("Current directory: " + System.getProperty("user.dir"));
			if(filename.endsWith("/")){
				filename += "index.html";
			}
			while (filename.indexOf("/")==0)
		          filename=filename.substring(1);
			filename=filename.replace('/', File.separator.charAt(0));
			//revisar por hacks!
			if(filename.indexOf("..") != -1 || filename.indexOf(":") != -1
					|| filename.indexOf("/.ht")!=-1 || filename.endsWith("~")){
				throw new FileNotFoundException();
			}
			System.out.println("Looking for file: " + filename);
			File file = new File(filename);
			InputStream filestream = new FileInputStream(file);
			//si aqui no tira excepcion, el archivo existe
			
			//OJO: Tengo que revisar por host o referer?
			//luego reviso si el formato en accept es igual al del resource (406)
			String fileFormat = checkMimeType(filename);
			
			System.out.println("Media-type: " + fileFormat);
			System.out.println("Content-length: " + file.length());
			
			//ocupo buscar el encabezado Accept!
			String acceptHeader = "";
			int i = 1;
			while(i < httpHeaders.size() && acceptHeader.equals("")){
				if(httpHeaders.get(i).startsWith("Accept:")){
					acceptHeader = httpHeaders.get(i);
				}
				i++;
			}
			if(!acceptHeader.contains((CharSequence)fileFormat) && !acceptHeader.contains("*/*")){
				System.out.println(acceptHeader);
				returnStatusCode(406);
				return;
			}
			
			//si todo pasa, devuelvo el resource y un 200 OK
			String response = "HTTP/1.1 200 OK \r\n" +
					"Date: " + getServerTime() +
					"\r\nContent-Type: " + fileFormat +
					"\r\nContent-Length: " + file.length() +
					"\r\nServer: Skynet Webserver 1.0\r\n";
			out.println(response);
			/*out.println("HTTP/1.1 200 OK");
			out.println("Date: " + getServerTime());
			out.println("Content-type: " + fileFormat);
			out.println("Content-length: " + file.length());
			out.println("Server: Skynet Webserver 1.0");
			out.println("");*/
			out.flush();
			sendFile(filestream);
			filestream.close();
			
			//log response
			logstring.append(response);
			logger.info(logstring.toString());
			
			//cleanup is done back at run()
			System.out.println("Request fulfilled!");
		}
		catch(FileNotFoundException f){
			System.err.println("Error: File not found!");
			returnStatusCode(404);
		}
		catch(Exception e){
			System.err.println("Error: " + e.toString());
		}
		
	}
	
	public void httpPost(ArrayList<String> httpHeaders, String body){
		//primero: revisar si el request es valido (version correcta)
		//segundo: revisar content-type! si es multiform o x-www-form-urlencoded
		//case 1: si es x-www-form-urlencoded, obtengo parametros y los devuelvo
		
		//TODO: case 2: si es multiform, obtengo el delimiter, busco el nombre y tipo de archivo
		//TODO: copio datos en nuevo archivo, retorno 201 con el nombre del nuevo archivo
		StringBuilder logstring = new StringBuilder("\nResponse: \n");
		String currentHeader = httpHeaders.get(0);
		if(!currentHeader.endsWith("HTTP/1.1")){
			returnStatusCode(400);
			return;
		}
		int i = 0;
		String contentType = "";
		while(i < httpHeaders.size() && contentType.equals("")){
			if(httpHeaders.get(i).startsWith("Content-Type:")){
				contentType = httpHeaders.get(i).substring(14);
			}
			i++;
		}
		System.out.println("Content-Type: " + contentType);
		if(contentType.equals("application/x-www-form-urlencoded")){ //case 1
			String html = "<html>" +
							"<head><title>Great success!</title></head>" +
							"<body>" +
							"<h1>Great success!</h1>" +
							"<p>You sent: " + body + "</p>" +
							"</body>" +
							"</html>";
			String response = "HTTP/1.1 200 OK \r\n" +
					"Date: " + getServerTime() + "\r\n" +
					"Content-Type: text/html \r\n" +
					"Content-Length: " + html.length() + "\r\n" +
					"Server: Skynet Webserver 1.0\r\n";
			out.println(response);
			out.println(html);
			out.flush();
			
			//log response
			logstring.append(response);
			logger.info(logstring.toString());
			
			//cleanup is done back at run()
			System.out.println("Request fulfilled!");
		}
		else{ //case 2
			returnStatusCode(501);
			return;
		}
	}
	
	//igual que el GET excepto que no se envía ningún archivo, no hay body
	public void httpHead(ArrayList<String> httpHeaders){
		//reviso si el request es valido (el resource existe (404), http version es correcta (400)?)
		String currentHeader = httpHeaders.get(0);
		StringBuilder logstring = new StringBuilder("\nResponse: \n");
		if(!currentHeader.endsWith("HTTP/1.1")){
			returnStatusCode(400);
			return;
		}
				
		String filename = currentHeader.substring(4, currentHeader.length()-9).trim();
		try{
			System.out.println("Current directory: " + System.getProperty("user.dir"));
			
			if(filename.endsWith("/")){
				filename += "index.html";
			}
			
			while (filename.indexOf("/")==0){
			         filename=filename.substring(1);
			}
			
			filename=filename.replace('/', File.separator.charAt(0));
			
			//revisar por hacks!
			if(filename.indexOf("..") != -1 || filename.indexOf(":") != -1
					|| filename.indexOf("/.ht")!=-1 || filename.endsWith("~")){
				returnStatusCode(403);
				return;
			}
			
			System.out.println("Looking for file: " + filename);
			File file = new File(filename);
			InputStream filestream = new FileInputStream(file);
			//si aqui no tira excepcion, el archivo existe
			
			//OJO: Tengo que revisar por host o referer?
			//luego reviso si el formato en accept es igual al del resource (406)
			String fileFormat = checkMimeType(filename);
			System.out.println("Media-type: " + fileFormat);
			System.out.println("Content-length: " + file.length());
			//ocupo buscar el encabezado Accept!
			String acceptHeader = "";
			int i = 1;
			while(i < httpHeaders.size() && acceptHeader.equals("")){
				if(httpHeaders.get(i).startsWith("Accept:")){
					acceptHeader = httpHeaders.get(i);
				}
				i++;
			}
			if(!acceptHeader.contains((CharSequence)fileFormat) || !acceptHeader.contains("*/*")){
				returnStatusCode(406);
			}
			
			//si todo pasa, devuelvo un 200 OK
			String response = "HTTP/1.1 200 OK \r\n" +
					"Date: " + getServerTime() +
					"\r\nContent-Type: " + fileFormat +
					"\r\nContent-Length: " + file.length() +
					"\r\nServer: Skynet Webserver 1.0\r\n";
			out.println(response);
			out.flush();
			/*out.println("HTTP/1.1 200 OK");
			out.println("Date: " + getServerTime());
			out.println("Content-Type: " + fileFormat);
			out.println("Content-Length: " + file.length());
			out.println("Server: Skynet Webserver 1.0");
			out.println("");*/
			filestream.close();
			
			//log response
			logstring.append(response);
			logger.info(logstring.toString());
			
			//cleanup is done back at run()
			System.out.println("Request fulfilled!");
		}
		catch(FileNotFoundException f){
			System.err.println("Error: File not found!");
			returnStatusCode(404);
		}
		catch(Exception e){
			System.err.println("Error: " + e.toString());
		}
	}
	
	//TODO: Log response
	private void returnStatusCode(int status){
		switch(status){
		case 400:
			out.println("HTTP/1.1 400 Bad Request");
			break;
		case 403:
			out.println("HTTP/1.1 403 Forbidden");
			break;
		case 404:
			out.println("HTTP/1.1 404 File Not Found"); //retornar un html con 404?
			break;
		case 405:
			out.println("HTTP/1.1 405 Method Not Allowed");
			out.println("Allow: GET, POST, HEAD");	
			break;
		case 406:
			out.println("HTTP/1.1 406 Not Acceptable");
			break;
		default:
			out.println("HTTP/1.1 501 Not Implemented");
		}
		out.println("Date: " + getServerTime());
		out.println("Server: Skynet Webserver 1.0");
		out.println("");
	}
	
	public String checkMimeType(String filename){
		System.out.println("Checking request media-type");
		MimetypesFileTypeMap mimeMap = new MimetypesFileTypeMap();
		String mimetype = mimeMap.getContentType(filename);
		return mimetype;
	}
	
	private void sendFile(InputStream filestream){
		System.out.println("Sending file! -~-~-~-~->");
		try(OutputStream fout = new BufferedOutputStream(this.websocket.getOutputStream())){
			byte[] arr = new byte[1024];
			while(filestream.available() > 0){
				fout.write(arr, 0, filestream.read(arr));
			}
		}
		catch(Exception e){
			System.err.println("An error occurred: " + e.toString());
		}		
	}
}
