package com.almende.dialog.model;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Logger;

import com.almende.dialog.model.impl.Q_fields;
import com.almende.dialog.model.intf.QuestionIntf;
import com.almende.dialog.util.QuestionTextTransformer;
import com.almende.util.ParallelInit;
import com.eaio.uuid.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;

import flexjson.JSON;
import flexjson.JSONSerializer;

public class Question implements QuestionIntf {
	private static final long serialVersionUID = -9069211642074173182L;
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	static final ObjectMapper om =ParallelInit.getObjectMapper();
	
	QuestionIntf question;
	private String preferred_language = "nl";

    private Collection<MediaHint> media_Hints;

	public Question() {
		this.question = new Q_fields(); // Default to simple in-memory class
	}

	// Factory functions:
	@JSON(include = false)
	public static Question fromURL(String url) {
		return fromURL(url,"");
	}
	@JSON(include = false)
	public static Question fromURL(String url,String remoteID)  {
		return fromURL(url, remoteID, "");
	}
	@JSON(include = false)
	public static Question fromURL(String url,String remoteID,String fromID)  {
            log.info( String.format( "Trying to parse Question from URL: %s with remoteId: %s and fromId: %s", url, remoteID, fromID  ));
            
                Client client = ParallelInit.getClient();
		WebResource webResource = client.resource(url);
	
		String json = "";
		try {
			webResource = webResource.queryParam("responder", URLEncoder.encode(remoteID, "UTF-8")).queryParam("requester", URLEncoder.encode(fromID, "UTF-8"));
			log.info("Getting question url: "+webResource.toString());
			json = webResource.type("text/plain").get(String.class);
			
			log.info("Received new question (fromURL): "+json);
		} catch (ClientHandlerException e) {
			log.severe(e.toString());
		} catch (UniformInterfaceException e) {
			log.severe(e.toString());
		} catch (UnsupportedEncodingException e) {
			log.severe(e.toString());
		}
		return fromJSON(json);
	}

	@JSON(include = false)
	public static Question fromJSON(String json) {
		Question question = null;
		if(json!=null) {
			try {
				question = om.readValue(json, Question.class);
			
				question.setQuestion_text( URLDecoder.decode( question.getQuestion_text() ) );
				
				//question = new JSONDeserializer<Question>().use(null,
				//		Question.class).deserialize(json);
			} catch (Exception e) {
				log.severe(e.toString());
			}
		}
		log.info( "question from JSON: %s" + question );
		return question;
	}

	@JSON(include = false)
	public String toJSON() {
		return toJSON(false);
	}
	
	@JSON(include = false)
	public String toJSON(boolean expanded_texts) {
		return new JSONSerializer().exclude("*.class").transform(new QuestionTextTransformer(expanded_texts), "question_text", "question_expandedtext", "answer_text", "answer_expandedtext", "answers.answer_text", "answers.answer_expandedtext")
				.include("answers", "event_callbacks").serialize(this);
	}

	@JSON(include = false)
	public void generateIds() {
		if (this.getQuestion_id() == null || this.getQuestion_id().equals("")) {
			this.setQuestion_id(new UUID().toString());
		}
		if(this.getAnswers()!=null) {
			for (Answer ans : this.getAnswers()) {
				if (ans.getAnswer_id() == null || ans.getAnswer_id().equals("")) {
					ans.setAnswer_id(new UUID().toString());
				}
			}
		}
	}

	@JSON(include = false)
	public Question answer(String responder, String answer_id, String answer_input) {
		Client client = ParallelInit.getClient();
		boolean answered=false;
		Answer answer = null;
		if (this.getType().equals("open")) {
			answer = this.getAnswers().get(0); // TODO: error handling, what if
												// answer doesn't exist, or
												// multiple answers
												// (=out-of-spec)
		} else if(this.getType().equals("openaudio")) {
			answer = this.getAnswers().get(0);
			try {
				answer_input = URLDecoder.decode(answer_input, "UTF-8");
				log.info("Received answer: "+answer_input);
			} catch (Exception e) {
			}
		} else if (this.getType().equals("comment") || this.getType().equals("referral")) {
			if(this.getAnswers() == null || this.getAnswers().size()==0)
				return null;
			answer = this.getAnswers().get(0);
			
		} else if (answer_id != null) {
			answered=true;
			ArrayList<Answer> answers = question.getAnswers();
			for (Answer ans : answers) {
				if (ans.getAnswer_id().equals(answer_id)) {
					answer = ans;
					break;
				}
			}
		} else if (answer_input != null) {
			answered=true;
			// check all answers of question to see if they match, possibly
			// retrieving the answer_text for each
			answer_input = answer_input.trim();
			ArrayList<Answer> answers = question.getAnswers();
			for (Answer ans : answers) {
				if (ans.getAnswer_text()!=null && ans.getAnswer_text().equals(answer_input)) {
					answer = ans;
					break;
				}
			}
			if (answer == null) {
				for (Answer ans : answers) {
					if (ans.getAnswer_expandedtext(this.preferred_language).equalsIgnoreCase(
							answer_input)) {
						answer = ans;
						break;
					}
				}
			}
			if (answer == null) {
				try {
					int answer_nr = Integer.parseInt(answer_input);
					if(answer_nr <= answers.size())
						answer = answers.get(answer_nr-1);
				} catch(NumberFormatException ex) {
					
				}
			}
		}
		Question newQ = null;
		if (!this.getType().equals("comment") && answer == null) {
			// Oeps, couldn't find/handle answer, just repeat last question:
			// TODO: somewhat smarter behavior? Should dialog standard provide
			// error handling?
			if(answered)
				newQ = this.event("exception","Wrong answer received", responder);
			if(newQ!=null)
				return newQ;
			
			return this;
		}
		// Send answer to answer.callback.
		WebResource webResource = client.resource(answer.getCallback());
		AnswerPost ans = new AnswerPost(this.getQuestion_id(),
				answer.getAnswer_id(), answer_input, responder);
		// Check if answer.callback gives new question for this dialog
		try {
			String post = om.writeValueAsString(ans);
			log.info("Going to send: "+post);
			String s = webResource.type("application/json").post(
					String.class, post);
			
			log.info("Received new question (answer): "+s);

			newQ = om.readValue(s, Question.class);
			newQ.setPreferred_language(preferred_language);
		} catch (Exception e) {
			log.severe(e.toString());
			newQ = this.event("exception", "Unable to parse question json", responder);
		}
		return newQ;
	}
	
	public Question event(String eventType, String message, String responder) {
		log.info("Received: "+eventType);
		Client client = ParallelInit.getClient();
		ArrayList<EventCallback> events = this.getEvent_callbacks();
		EventCallback eventCallback=null;
		if(events!=null) {
			for(EventCallback e : events) {
				if(e.getEvent().toLowerCase().equals(eventType)){
					eventCallback = e;
					break;
				}
			}
		}
		
		if(eventCallback==null) {
			// Oeps, couldn't find/handle event, just repeat last question:
			// TODO: somewhat smarter behavior? Should dialog standard provide
			// error handling?
			if(eventType.equals("hangup") || eventType.equals("exception")) {
				return null;
			}
			
			return this;
		}
		Question newQ = null;
		WebResource webResource = client.resource(eventCallback.getCallback());
		EventPost event = new EventPost(responder, this.getQuestion_id(),
				eventType, message);
		try {
			String post = om.writeValueAsString(event);
			String s = webResource.type("application/json").post(
					String.class, post);
			
			log.info("Received new question (event): "+s);

			if(s!=null && !s.equals("")) { 
				newQ = om.readValue(s, Question.class);
				newQ.setPreferred_language(preferred_language);
			}
		} catch (Exception e) {
			log.severe(e.toString());
		}
		return newQ;
	}

	// Getters/Setters:
	@Override
	public String getQuestion_id() {
		return question.getQuestion_id();
	}

	@Override
	public String getQuestion_text() {
		return question.getQuestion_text();
	}

	@Override
	public String getType() {
		return question.getType();
	}

	@Override
	public String getUrl() {
		return question.getUrl();
	}

	@Override
	public String getRequester() {
		return question.getRequester();
	}
	@Override
	@JSON(include = false)
	public HashMap<String,String> getExpandedRequester() {
		return question.getExpandedRequester(this.preferred_language);
	}
	@Override
	@JSON(include = false)
	public HashMap<String,String> getExpandedRequester(String language) {
		this.preferred_language=language;
		return question.getExpandedRequester(language);
	}	
	@Override
	public ArrayList<Answer> getAnswers() {
		return question.getAnswers();
	}

	@Override
	public ArrayList<EventCallback> getEvent_callbacks() {
		return question.getEvent_callbacks();
	}

	@Override
	@JSON(include = false)
	public String getQuestion_expandedtext() {
		return question.getQuestion_expandedtext(this.preferred_language);
	}

	@Override
	@JSON(include = false)
	public String getQuestion_expandedtext(String language) {
		this.preferred_language=language;
		return question.getQuestion_expandedtext(language);
	}

	@Override
	public void setQuestion_id(String question_id) {
		question.setQuestion_id(question_id);
	}

	@Override
	public void setQuestion_text(String question_text) {
		question.setQuestion_text(question_text);
	}

	@Override
	public void setType(String type) {
		question.setType(type);
	}

	@Override
	public void setUrl(String url) {
		question.setUrl(url);
	}
	@Override
	public void setRequester(String requester) {
		question.setRequester(requester);
	}

	@Override
	public void setAnswers(ArrayList<Answer> answers) {
		question.setAnswers(answers);
	}

	@Override
	public void setEvent_callbacks(ArrayList<EventCallback> event_callbacks) {
		question.setEvent_callbacks(event_callbacks);
	}

	@JSON(include = false)
	public String getPreferred_language() {
		return preferred_language;
	}

	@JSON(include = false)
	public void setPreferred_language(String preferred_language) {
		this.preferred_language = preferred_language;
	}

	@Override
	public String getData() {
		return this.question.getData();
	}

	@Override
	public void setData(String data) {
		this.question.setData(data);
		
	}

	@Override
	public String getTrackingToken() {
		return this.question.getTrackingToken();
	}

	@Override
	public void setTrackingToken(String token) {
		this.question.setTrackingToken(token);
	}

    public Collection<MediaHint> getMedia_Hints()
    {
        return media_Hints;
    }

    public void setMedia_Hints( Collection<MediaHint> media_Hints )
    {
        this.media_Hints = media_Hints;
    }

    public void addMedia_Hint( MediaHint mediaHint )
    {
        media_Hints = media_Hints == null ? new ArrayList<MediaHint>() : media_Hints;
        media_Hints.add( mediaHint );
    }
}
