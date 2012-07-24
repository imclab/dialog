package com.almende.dialog.adapter.vxml;

import java.io.StringWriter;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.znerd.xmlenc.XMLOutputter;

import com.almende.dialog.Settings;

@Path("/ccxml/")
public class CCXMLProxy {
	
	private String callOut(String selectedDialog, String dialURL, String remoteID){
		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("ccxml");
				outputter.attribute("version", "1.0");
				outputter.attribute("xmlns", "http://www.w3.org/2002/09/ccxml");
				outputter.startTag("var");
					outputter.attribute("name", "currentState");
					outputter.attribute("expr", "'initial'");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name", "vxmlScript");
					outputter.attribute("expr", "'"+selectedDialog+"'");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name", "dialogID");
					outputter.attribute("expr", "''");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name", "inboundID");
					outputter.attribute("expr", "''");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name", "outboundID");
					outputter.attribute("expr", "''");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name", "dialURL");
					outputter.attribute("expr", "'"+dialURL+"'");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name", "remoteID");
					outputter.attribute("expr", "'"+remoteID+"'");
				outputter.endTag();
				outputter.startTag("eventprocessor");
					outputter.attribute("statevariable", "currentState");
					outputter.startTag("transition");
						outputter.attribute("state", "initial");
						outputter.attribute("event", "connection.alerting");
						outputter.startTag("assign");
							outputter.attribute("name", "inboundID");
							outputter.attribute("expr", "event$.connectionid");
						outputter.endTag();
						outputter.startTag("accept");
							outputter.attribute("connectionid", "inboundID");
						outputter.endTag();
						outputter.startTag("createcall");
							outputter.attribute("dest", "dialURL");
							outputter.attribute("connectionid", "outboundID");
						outputter.endTag();
						outputter.startTag("assign");
							outputter.attribute("name", "currentState");
							outputter.attribute("expr", "'calling'");
						outputter.endTag();						
					outputter.endTag();
					outputter.startTag("transition");
						outputter.attribute("state", "calling");
						outputter.attribute("event", "connection.connected");
						outputter.startTag("dialogstart");
							outputter.attribute("connectionid", "outboundID");
							outputter.attribute("dialogid", "dialogID");
							outputter.attribute("src", "vxmlScript");
							outputter.attribute("namelist", "remoteID");
						outputter.endTag();
						outputter.startTag("assign");
							outputter.attribute("name", "currentState");
							outputter.attribute("expr", "'connestablished'");
						outputter.endTag();
						outputter.startTag("disconnect");
							outputter.attribute("connectionid", "inboundID");
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("transition");
						outputter.attribute("state", "connestablished");
						outputter.attribute("event", "dialog.exit");
						outputter.startTag("exit");
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("transition");
						outputter.attribute("state", "connestablished");
						outputter.attribute("event", "dialog.transfer");
						outputter.startTag("redirect");
							outputter.attribute("connectionid", "outboundID");
							outputter.attribute("dest", "event$.URI");
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("transition");
						outputter.attribute("event", "connection.failed");
						outputter.startTag("exit");
						outputter.endTag();
					outputter.endTag();
				outputter.endTag();
			outputter.endTag();
			outputter.endDocument();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return sw.toString();
		
	}
	
	private String inboundCall(String selectedDialog){
		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("ccxml");
				outputter.attribute("version", "1.0");
				outputter.attribute("xmlns", "http://www.w3.org/2002/09/ccxml");
				outputter.startTag("var");
					outputter.attribute("name", "currentState");
					outputter.attribute("expr", "'initial'");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name", "vxmlScript");
					outputter.attribute("expr", "'"+selectedDialog+"'");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name", "dialogID");
					outputter.attribute("expr", "''");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name", "connectionID");
					outputter.attribute("expr", "''");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name", "remoteID");
					outputter.attribute("expr", "''");
				outputter.endTag();
				outputter.startTag("eventprocessor");
					outputter.attribute("statevariable", "currentState");
					outputter.startTag("transition");
						outputter.attribute("state", "initial");
						outputter.attribute("event", "connection.alerting");
						outputter.startTag("assign");
							outputter.attribute("name", "connectionID");
							outputter.attribute("expr", "event$.connectionid");
						outputter.endTag();
						outputter.startTag("assign");
							outputter.attribute("name","remoteID");
							outputter.attribute("expr","event$.connection.remote");
						outputter.endTag();
						outputter.startTag("accept");
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("transition");
						outputter.attribute("state", "initial");
						outputter.attribute("event", "connection.connected");
						outputter.startTag("dialogstart");
							outputter.attribute("connectionid", "connectionID");
							outputter.attribute("dialogid", "dialogID");
							outputter.attribute("src", "vxmlScript");
							outputter.attribute("namelist", "remoteID");
						outputter.endTag();
						outputter.startTag("assign");
							outputter.attribute("name", "currentState");
							outputter.attribute("expr", "'connestablished'");
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("transition");
						outputter.attribute("state", "connestablished");
						outputter.attribute("event", "dialog.exit");
						outputter.startTag("exit");
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("transition");
						outputter.attribute("state", "connestablished");
						outputter.attribute("event", "dialog.transfer");
						outputter.startTag("redirect");
							outputter.attribute("connectionid", "connectionID");
							outputter.attribute("dest", "event$.URI");
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("transition");
						outputter.attribute("event", "connection.failed");
						outputter.startTag("exit");
						outputter.endTag();
					outputter.endTag();
				outputter.endTag();
			outputter.endTag();
			outputter.endDocument();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return sw.toString();
	}
	
	@GET
	@Produces("application/ccxml+xml")
	public Response getCCXML(){
		return Response.ok(inboundCall("/vxml/new?url=http://"+Settings.HOST+"/howIsTheWeather/%3Fpreferred_medium=audio/wav")).build();
	}
	
}