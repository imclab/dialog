package com.almende.dialog.model.impl;

import java.util.logging.Logger;

import com.almende.dialog.model.ClientCon;
import com.almende.dialog.model.intf.AnswerIntf;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

public class A_fields implements AnswerIntf {
	private static final long serialVersionUID = -2673880321244315796L;
	private static final Logger log = Logger.getLogger(com.almende.dialog.model.impl.A_fields.class.getName()); 	
	
	String answer_id;
	String answer_text;
	String callback;
	
	public A_fields() {}

	public String getAnswer_id() {
		return answer_id;
	}

	public String getAnswer_text() {
		return answer_text;
	}

	public String getCallback() {
		return callback;
	}

	public void setAnswer_id(String answer_id) {
		this.answer_id = answer_id;
	}

	public void setAnswer_text(String answer_text) {
		this.answer_text = answer_text;
	}

	public void setCallback(String callback) {
		this.callback = callback;
	}

	@Override
	public String getAnswer_expandedtext() {
		Client client = ClientCon.client;
		WebResource webResource = client.resource(this.getAnswer_text());
		String text = "";
		try {
			text = webResource.type("text/plain").get(String.class);
		} catch (Exception e){
			log.severe(e.toString());
		}
		return text;
	}


}