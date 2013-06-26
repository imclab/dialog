package com.almende.dialog.adapter.tools;

import java.io.StringWriter;
import java.util.logging.Logger;

import org.znerd.xmlenc.XMLOutputter;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.util.ParallelInit;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

public class CM {

	private static final Logger log = Logger.getLogger(CM.class.getName()); 
	
	private static final String MIN_MESSAGE_PARTS="1";
	private static final String MAX_MESSAGE_PARTS="6";
	
	private static final String MESSAGE_TYPE_GSM7 = "0";
	private static final String MESSAGE_TYPE_UTF8 = "8";
	private static final String MESSAGE_TYPE_BIN = "4";
	
	private static final String url = "http://smssgateway.clubmessage.nl/cm/gateway.ashx";
	
	private String userID = "";
	private String userName = "";
	private String password = "";
	
	public CM(String userID, String userName, String password) {
		
		this.userID = userID;
		this.userName = userName;
		this.password = password;
	}
	
	public int sendMessage(String message, String subject, String from,
			String fromName, String to, String toName, AdapterConfig config) throws Exception {

		String type="TEXT";
		String dcs="";
		if(!isGSMSeven(message)) {
			dcs=MESSAGE_TYPE_UTF8;
		} else {
			dcs=MESSAGE_TYPE_GSM7;
		}
		
		// TODO: Check message for special chars, if so change dcs.		
		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("MESSAGES");
				outputter.startTag("CUSTOMER");
					outputter.attribute("ID", userID);
				outputter.endTag();
				
				outputter.startTag("USER");
					outputter.attribute("LOGIN", userName);
					outputter.attribute("PASSWORD", password);
				outputter.endTag();
				
				// TODO: Create delivery reference
				
				outputter.startTag("MSG");
				outputter.startTag("CONCATENATIONTYPE");
					outputter.cdata(type);
				outputter.endTag();
				
				outputter.startTag("FROM");
					outputter.cdata(from);
				outputter.endTag();
				
				outputter.startTag("BODY");
					outputter.attribute("TYPE", type);
					outputter.cdata(message);
				outputter.endTag();
				
				outputter.startTag("TO");
					outputter.cdata(to);
				outputter.endTag();
		
				outputter.startTag("DCS");
					outputter.cdata(dcs);
				outputter.endTag();
				
				outputter.startTag("MINIMUMNUMBEROFMESSAGEPARTS");
					outputter.cdata(MIN_MESSAGE_PARTS);
				outputter.endTag();
				
				outputter.startTag("MAXIMUMNUMBEROFMESSAGEPARTS");
					outputter.cdata(MAX_MESSAGE_PARTS);
				outputter.endTag();
				
				outputter.endTag(); //MSG
			outputter.endTag(); //MESSAGES
			outputter.endDocument();
		} catch (Exception e) {
			log.severe("Exception in creating question XML: "+ e.toString());
			return 0;
		}

		Client client = ParallelInit.getClient();

		WebResource webResource = client.resource(url);
		String result = webResource.type("text/plain").post(String.class, sw.toString());
		if(!result.equals(""))
			throw new Exception(result);
		log.info("Result from CM: "+result);
		
		int count = countMessageParts(message, dcs);
		return count;
	}
	
	public boolean isGSMSeven(CharSequence str0) {
        if (str0 == null) {
            return true;
        }

        int len = str0.length();
        for (int i = 0; i < len; i++) {
            // get the char in this string
            char c = str0.charAt(i);
            // simple range checks for most common characters (0x20 -> 0x5F) or (0x61 -> 0x7E)
            if ((c >= ' ' && c <= '_') || (c >= 'a' && c <= '~')) {
                continue;
            } else {
                // 10X more efficient using a switch statement vs. a lookup table search
                switch (c) {
                    case '\u00A3':	// £
                    case '\u00A5':	// ¥
                    case '\u00E8':	// è
                    case '\u00E9':	// é
                    case '\u00F9':	// ù
                    case '\u00EC':	// ì
                    case '\u00F2':	// ò
                    case '\u00C7':	// Ç
                    case '\n':          // newline
                    case '\u00D8':	// Ø
                    case '\u00F8':	// ø
                    case '\r':          // carriage return
                    case '\u00C5':	// Å
                    case '\u00E5':	// å
                    case '\u0394':	// Δ
                    case '\u03A6':	// Φ
                    case '\u0393':	// Γ
                    case '\u039B':	// Λ
                    case '\u03A9':	// Ω
                    case '\u03A0':	// Π
                    case '\u03A8':	// Ψ
                    case '\u03A3':	// Σ
                    case '\u0398':	// Θ
                    case '\u039E':	// Ξ
                    case '\u00C6':	// Æ
                    case '\u00E6':	// æ
                    case '\u00DF':	// ß
                    case '\u00C9':	// É
                    case '\u00A4':	// ¤
                    case '\u00A1':	// ¡
                    case '\u00C4':	// Ä
                    case '\u00D6':	// Ö
                    case '\u00D1':	// Ñ
                    case '\u00DC':	// Ü
                    case '\u00A7':	// §
                    case '\u00BF':	// ¿
                    case '\u00E4':	// ä
                    case '\u00F6':	// ö
                    case '\u00F1':	// ñ
                    case '\u00FC':	// ü
                    case '\u00E0':	// à
                    case '\u20AC':	// €
                        continue;
                    default:
                        return false;
                }
            }
        }
        return true;
    }
	
	private int countMessageParts(String message, String type) {

		int maxChars = 0;
		
		if(type.equals(MESSAGE_TYPE_GSM7)) {
			maxChars=160;
			if(message.toCharArray().length<maxChars) // Test if concatenated message
				maxChars = 153;				
		} else if(type.equals(MESSAGE_TYPE_UTF8)) {
			maxChars=70;
			if(message.toCharArray().length<maxChars)
				maxChars = 67;
		} else if (type.equals(MESSAGE_TYPE_BIN)) {
			maxChars=280;
			if(message.toCharArray().length<maxChars)
				maxChars = 268;
		}
		
		int count = Math.round((message.toCharArray().length-1) / maxChars) + 1;
		return count;
	}
}