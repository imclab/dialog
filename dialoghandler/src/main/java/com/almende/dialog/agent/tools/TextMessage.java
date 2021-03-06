package com.almende.dialog.agent.tools;

import java.util.HashMap;
import java.util.Map;

public class TextMessage {

    private String localAddress = "";
    private String address = "";
    private String subject = "";
    private String body = "";
    private String recipientName = "";
    private String keyword = null;
    private String reference = null;
    private boolean extractKeyword = false;
    private Map<String, Object> extras = null;
	
    public TextMessage(){}
	
    public TextMessage(boolean extractKeyword) {

        this.extractKeyword = extractKeyword;
    }

    public String getLocalAddress() {

        return localAddress;
    }

    public void setLocalAddress(String localAddress) {

        this.localAddress = localAddress;
    }

    public String getAddress() {

        return address;
    }

    public void setAddress(String address) {

        this.address = address;
    }

    public String getSubject() {

        return subject;
    }

    public void setSubject(String subject) {

        this.subject = subject;
    }

    public String getBody() {

        return body;
    }

    public void setBody(String body) {

        this.body = body;
        extractKeyword();
    }

    public String getRecipientName() {

        return recipientName;
    }

    public void setRecipientName(String recipientName) {

        this.recipientName = recipientName;
    }

    public String getKeyword() {

        return keyword;
    }
	
    public Map<String, Object> getExtras()
    {
        extras = extras != null ? extras : new HashMap<String, Object>();
        return extras;
    }

    public void setExtras( Map<String, Object> extras )
    {
        this.extras = extras;
    }
	
    private void extractKeyword() {

        if (!this.body.isEmpty() && extractKeyword) {
            String[] words = this.body.split(" ");
            if (words.length > 1) {
                for (String word : words) {
                    if (!word.isEmpty()) {
                        this.keyword = word.toUpperCase();
                        return;
                    }
                }
            }
            else {
                if (!words[0].isEmpty())
                    this.keyword = words[0].toUpperCase();
            }
        }
    }
    
    public String getReference() {
        
        return reference;
    }

    public void setReference(String reference) {
    
        this.reference = reference;
    }
}
