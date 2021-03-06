package com.almende.dialog.model.impl;

import java.util.logging.Logger;
import com.almende.dialog.model.intf.AnswerIntf;
import com.almende.dialog.util.AFHttpClient;
import com.almende.util.ParallelInit;

public class A_fields implements AnswerIntf {
	private static final long serialVersionUID = -2673880321244315796L;
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	String answer_id;
	String answer_text;
	String callback;
	
	public A_fields() {}

	@Override
	public String getAnswer_id() {
		return answer_id;
	}

	@Override
	public String getAnswer_text() {
		return answer_text;
	}

	@Override
	public String getCallback() {
		return callback;
	}

	@Override
	public void setAnswer_id(String answer_id) {
		this.answer_id = answer_id;
	}

	@Override
	public void setAnswer_text(String answer_text) {
		this.answer_text = answer_text;
	}

	@Override
	public void setCallback(String callback) {
		this.callback = callback;
	}

    @Override
    public String getAnswer_expandedtext(String language, String authorizationHeader) {

        String url = this.getAnswer_text();
        if (url == null || url.equals(""))
            return "";
        if (url.startsWith("text://"))
            return url.replace("text://", "");
        if (url.startsWith("dtmfKey://")) {
            return url;
        }
        if (language != null && !language.equals("")) {
            url += url.indexOf("?") > 0 ? "&" : "?";
            url += "preferred_language=" + language;
        }
        AFHttpClient client = ParallelInit.getAFHttpClient();
        if (authorizationHeader != null) {
            client.addBasicAuthorizationHeader(authorizationHeader);
        }
        String text = "";
        try {
            text = client.get(url);
        }
        catch (Exception e) {
            log.severe(e.toString());
        }
        return text;
    }
    
    @Override
    public String getAnswer_expandedtext() {

        return getAnswer_expandedtext(null, null);
    }

}
