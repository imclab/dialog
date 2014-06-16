package com.almende.dialog.adapter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.CLXUSSD;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;

public class CLXUSSDServlet extends TextServlet {

	private static final long serialVersionUID = -1195021911106214263L;
	protected static final com.almende.dialog.Logger dialogLog =  new com.almende.dialog.Logger();
	private static final String servletPath = "/ussd/";
	private static final boolean USE_KEYWORDS = true;
	
	@Override
	protected int sendMessage(String message, String subject, String from,
			String fromName, String to, String toName,
			Map<String, Object> extras, AdapterConfig config) throws Exception {

		System.out.println("Send message text: " + message);

		CLXUSSD clxussd = new CLXUSSD(config.getAccessToken(),
				config.getAccessTokenSecret(), config);
		String subId = config.getXsiSubscription();
		
		
			if (subId == "" || subId ==null) {
				subId = clxussd.startSubScription(to, config);
			}
			
		String xml = "";
		if (extras.get("questionType").equals("comment")) {
			

		}
 
		return 0;
	}

	@Override
	protected int broadcastMessage(String message, String subject, String from,
			String senderName, Map<String, String> addressNameMap,
			Map<String, Object> extras, AdapterConfig config) throws Exception {
		for ( String address : addressNameMap.keySet() )
        {
            sendMessage( message, subject, from, senderName, address, addressNameMap.get( address ), extras, config );
        }
		return 0;
	}
	

	
	@Override
	protected TextMessage receiveMessage(HttpServletRequest req,
			HttpServletResponse resp) throws Exception {
		
		String postBody= extractPostRequestBody(req);
		System.out.println("message resieved: "+ postBody );
		
		
		if(postBody.contains("open_session")){
			String sessionId = getSessionId(postBody);
			
			resp.getWriter().println("<?xml version=\"1.0\"?>	<umsprot version=\"1\"><prompt_req reqid=\"0\" sessionid=\"" + sessionId+ "\">Welcome to the USSD demo!Please enter your name:</prompt_req></umsprot>");
			System.out.println("responded with session id:"+sessionId);
			return null;
		}
		else if(postBody.contains("promp_req")){
			TextMessage msg = buildMessageFromXML(postBody);
			if (msg != null) {
				return msg;
			} else {
				log.warning("USSD no return message ");
				return null;
			}
		}
		else{
			resp.getWriter().println("nothing to report");
			return null;
		}
		
	}
	
	static String extractPostRequestBody(HttpServletRequest request) throws IOException {
	    if ("POST".equalsIgnoreCase(request.getMethod())) {
	        Scanner s = new Scanner(request.getInputStream(), "UTF-8").useDelimiter("\\A");
	        return s.hasNext() ? s.next() : "";
	    }
	    return "";
	}
	
	static String getSessionId (String postBody){
		
		String sessionId =postBody;
		sessionId = sessionId.substring(sessionId.indexOf("sessionid")+11);
		sessionId = sessionId.substring(0,sessionId.indexOf("\" requesttime"));
		
		return sessionId;
	}
	
	@Override
    protected void doPost( HttpServletRequest req, HttpServletResponse resp ) throws ServletException, IOException
    {
        super.doPost( req, resp );
    }

	@Override
	protected String getServletPath() {
		return servletPath;
	}

	@Override
	protected String getAdapterType() {
		return AdapterAgent.ADAPTER_TYPE_USSD;
	}

	@Override
	protected void doErrorPost(HttpServletRequest req, HttpServletResponse res)
			throws IOException {}
	
	private HashMap<String, String> getPostData(HttpServletRequest req) {
		StringBuilder sb = new StringBuilder();
	    try {
	        BufferedReader reader = req.getReader();
	        reader.mark(10000);

	        String line;
	        do {
	            line = reader.readLine();
	            sb.append(line).append("\n");
	        } while (line != null);
	        reader.reset();
	        // do NOT close the reader here, or you won't be able to get the post data twice
	    } catch(IOException e) {
	        log.warning("getPostData couldn't.. get the post data");  // This has happened if the request's reader is closed    
	    }
	    
	    log.info("Received data: "+sb.toString());

	    HashMap<String, String> data = new HashMap<String, String>();
		String[] params = sb.toString().split("&");
		for(String param : params) {
			String[] qp = param.split("=");
			if(qp.length>0)
				data.put(qp[0], (qp.length>1?qp[1]:""));
		}
		
		return data;
	}
	private String buildXmlQuestion (String question, String subId) {
		String xml = "<?xml version=\"1.0\"?><umsprot version=\"1\"><prompt_req reqid=\"0\" sessionid=\""+subId+"\">"+question+"</prompt_req></umsprot>";
		
		return xml;
	}
	
	private TextMessage buildMessageFromXML(String xml) {
		System.out.println("input xml :" + xml+" :end");
		TextMessage msg = new TextMessage();
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			
			Document dom = db.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
			Node root = dom.getDocumentElement();
			msg.setBody(root.getTextContent());
			
			String sessionId = xml.substring(xml.indexOf("sessionid=\"")+11);
			sessionId = sessionId.substring(0, sessionId.indexOf("\""));
			
			
			
		} catch(Exception ex){
			ex.printStackTrace();
		}
		return msg;
	}

	@Override
	protected DDRRecord createDDRForIncoming(AdapterConfig adapterConfig,
			String fromAddress) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected DDRRecord createDDRForOutgoing(AdapterConfig adapterConfig,
			Map<String, String> toAddress, String message) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}
}

