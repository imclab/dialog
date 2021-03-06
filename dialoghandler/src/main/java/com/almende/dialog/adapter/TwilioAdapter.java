package com.almende.dialog.adapter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import com.almende.dialog.LogLevel;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.Language;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.resource.factory.CallFactory;
import com.twilio.sdk.resource.instance.Account;
import com.twilio.sdk.resource.instance.Call;
import com.twilio.sdk.verbs.Dial;
import com.twilio.sdk.verbs.Gather;
import com.twilio.sdk.verbs.Hangup;
import com.twilio.sdk.verbs.Play;
import com.twilio.sdk.verbs.Record;
import com.twilio.sdk.verbs.Redirect;
import com.twilio.sdk.verbs.Say;
import com.twilio.sdk.verbs.TwiMLException;
import com.twilio.sdk.verbs.TwiMLResponse;
import com.twilio.sdk.verbs.Verb;

@Path("twilio")
public class TwilioAdapter {
	protected static final Logger log = Logger.getLogger(VoiceXMLRESTProxy.class.getName());
	protected static final com.almende.dialog.Logger dialogLog =  new com.almende.dialog.Logger();
	private static final int LOOP_DETECTION=10;
	protected String TIMEOUT_URL="timeout";
	//protected String EXCEPTION_URL="exception";
	
    /**
     * Initiates a call to all the numbers in the addressNameMap and returns a
     * Map of <adress, SessionKey>
     * 
     * @param addressNameMap
     *            Map with address (e.g. phonenumber or email) as Key and name
     *            as value. The name is useful for email and not used for SMS
     *            etc
     * @param dialogIdOrUrl
     *            If a String with leading "http" is found its considered as a
     *            url. Else a Dialog of this id is tried t The URL on which a
     *            GET HTTPRequest is performed and expected a question JSON
     * @param config
     *            the adapterConfig which is used to perform this broadcast
     * @param accountId
     *            AccoundId initiating this broadcast. All costs are applied to
     *            this accountId
     * @param applicationId
     *            This is set in the DialogAgent and should match that with the
     *            applicationId of the twillo account
     * @return A Map of <adress, SessionKey> 
     * @throws Exception
     */
    public static HashMap<String, String> dial(Map<String, String> addressNameMap, String dialogIdOrUrl,
        AdapterConfig config, String accountId, String applicationId) throws Exception {

        HashMap<String, String> resultSessionMap = new HashMap<String, String>();
        // If it is a broadcast don't provide the remote address because it is deceiving.
        String loadAddress = "";
        Session session = null;
        if (addressNameMap == null || addressNameMap.isEmpty()) {
            throw new Exception("No address given. Error in call request");
        }
        else if (addressNameMap.size() == 1) {
            loadAddress = addressNameMap.keySet().iterator().next();
            loadAddress = PhoneNumberUtils.formatNumber(loadAddress, null);
        }
        //create a session for the first remote address
        String firstRemoteAddress = loadAddress != null ? new String(loadAddress) : new String(addressNameMap.keySet()
                                        .iterator().next());
        firstRemoteAddress = PhoneNumberUtils.formatNumber(firstRemoteAddress, null);
        session = Session.getOrCreateSession(config, firstRemoteAddress);
        session.setAccountId(accountId);
        session.storeSession();
        String url = Dialog.getDialogURL(dialogIdOrUrl, accountId, session);

        //fetch the question
        Question question = Question.fromURL(url, config.getConfigId(), loadAddress, config.getMyAddress(),
                                             session != null ? session.getKey() : null, null);
        if (question != null) {
            for (String address : addressNameMap.keySet()) {
                String formattedAddress = PhoneNumberUtils.formatNumber(address, PhoneNumberFormat.E164);
                if (formattedAddress != null) {

                    //ignore the address for which the session is already created.
                    if (!formattedAddress.equals(session.getRemoteAddress())) {
                        //create a new session for every call request 
                        session = Session.createSession(config, formattedAddress);
                    }
                    session.killed = false;
                    session.setStartUrl(url);
                    session.setAccountId(accountId);
                    session.setDirection("outbound");
                    session.setRemoteAddress(formattedAddress);
                    session.setType(AdapterAgent.ADAPTER_TYPE_CALL);
                    session.addExtras(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.TWILIO.toString());
                    session.setAdapterID(config.getConfigId());
                    session.setQuestion(question);
                    //update session with account credentials
                    session.addExtras(AdapterConfig.ACCESS_TOKEN_KEY, config.getAccessToken());
                    session.addExtras(AdapterConfig.ACCESS_TOKEN_SECRET_KEY, config.getAccessTokenSecret());
                    session.storeSession();
                    dialogLog.log(LogLevel.INFO, session.getAdapterConfig(), String
                                                    .format("Outgoing call requested from: %s to: %s",
                                                            session.getLocalAddress(), formattedAddress), session);
                    String extSession = "";
                    if (!ServerUtils.isInUnitTestingEnvironment()) {
                        
                        Account mainAccount = getTwilioAccount(config.getAccessToken(), config.getAccessTokenSecret());

                        // Make a call
                        CallFactory callFactory = mainAccount.getCallFactory();
                        Map<String, String> callParams = new HashMap<String, String>();
                        callParams.put("To", formattedAddress); // Replace with a valid phone number
                        callParams.put("From", config.getMyAddress()); // Replace with a valid phone
                        // number in your account
                        callParams.put("ApplicationSid", applicationId);
                        //callParams.put("Url", "http://" + Settings.HOST + "/dialoghandler/rest/twilio/new");
                        callParams.put("StatusCallback", "http://" + Settings.HOST + "/dialoghandler/rest/twilio/cc");
                        callParams.put("StatusCallbackMethod", "GET");
                        callParams.put("IfMachine", "Hangup");
                        callParams.put("Record", "false");

                        Call call = callFactory.create(callParams);
                        log.info(String.format("Call triggered with external id: %s", call.getSid()));
                        extSession = call.getSid();
                        session.setExternalSession(extSession);
                        session.storeSession();
                    }
                    resultSessionMap.put(formattedAddress, session.getKey());
                }
                else {
                    resultSessionMap.put(address, "Invalid address");
                    log.severe(String.format("To address is invalid: %s. Ignoring.. ", address));
                }
            }
        }
        else {
            log.severe(String.format("Question not fetched from: %s. Request for outbound call rejected ",
                                     dialogIdOrUrl));
            dialogLog.log(LogLevel.SEVERE, session.getAdapterConfig(),
                          String.format("Question not fetched from: %s. Request for outbound call rejected ",
                                        dialogIdOrUrl), session);
        }
        return resultSessionMap;
    }

    @Path("new")
    @GET
    @Produces("application/xml")
    public Response getNewDialog(@QueryParam("CallSid") String CallSid, @QueryParam("AccountSid") String AccountSid,
        @QueryParam("From") String localID, @QueryParam("To") String remoteID,
        @QueryParam("Direction") String direction, @QueryParam("ForwardedFrom") String forwardedFrom) {

        return getNewDialogPost(CallSid, AccountSid, localID, remoteID, direction, forwardedFrom);
    }
	
    @Path("new")
    @POST
    @Produces("application/xml")
    public Response getNewDialogPost(@FormParam("CallSid") String CallSid, @FormParam("AccountSid") String AccountSid,
        @FormParam("From") String localID, @FormParam("To") String remoteID, @FormParam("Direction") String direction,
        @FormParam("ForwardedFrom") String forwardedFrom) {

        log.info("call started:" + direction + ":" + remoteID + ":" + localID);
        localID = checkAnonymousCallerId(localID);
        Map<String, String> extraParams = new HashMap<String, String>();
        if (forwardedFrom != null) {
            extraParams.put("forwardedFrom", forwardedFrom);
        }

        //swap the remote and the local numbers if its inbound
        if (direction.equals("inbound")) {
            String tmpLocalId = new String(localID);
            localID = new String(remoteID);
            remoteID = tmpLocalId;
        }
        AdapterConfig config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, localID);
        String formattedRemoteId = PhoneNumberUtils.formatNumber(remoteID, null);
        Session session = Session.getSessionByExternalKey(CallSid);

        String url = "";
        DDRRecord ddrRecord = null;
        try {
            if (session != null && direction.startsWith("outbound")) {
                try {
                    url = Dialog.getDialogURL(session.getStartUrl(), session.getAccountId(), session);
                }
                catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    dialogLog.log(LogLevel.WARNING, config,
                                  String.format("Dialog url encoding failed. Error: %s ", e.toString()), session);
                }
                ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(config, session.getAccountId(),
                                                                            formattedRemoteId, 1, url, session.getKey());
                direction = "outbound";
                dialogLog.log(LogLevel.INFO, config,
                              String.format("Trying to fetch dialog for %s, due to outgoing Call from: %s ",
                                            formattedRemoteId, config.getMyAddress()), session);
            }
            else if (direction.equals("inbound")) {
                //create a session for incoming only. Flush any existing one
                if (session != null) {
                    session.drop();
                }
                session = Session.createSession(config, formattedRemoteId);
                session.setAccountId(config.getOwner());
                session.setExternalSession(CallSid);
                session.storeSession();
                url = config.getURLForInboundScenario(session);
                ddrRecord = DDRUtils.createDDRRecordOnIncomingCommunication(config, session.getAccountId(),
                                                                            formattedRemoteId, 1, url, session.getKey());
            }
            session.setDdrRecordId(ddrRecord != null ? ddrRecord.getId() : null);
            ddrRecord.addAdditionalInfo(Session.TRACKING_TOKEN_KEY, session.getTrackingToken());
        }
        catch (Exception e) {
            String errorMessage = String.format("Creating DDR records failed. Direction: %s for adapterId: %s with address: %s remoteId: %s and localId: %s",
                                                direction, config.getConfigId(), config.getMyAddress(),
                                                formattedRemoteId, localID);
            log.severe(errorMessage);
            dialogLog.severe(config.getConfigId(), errorMessage, ddrRecord != null ? ddrRecord.getId() : null,
                             session != null ? session.getKey() : null);
        }
        finally {
            ddrRecord.createOrUpdate();
            session.setStartUrl(url);
            session.setDirection(direction);
            session.setRemoteAddress(formattedRemoteId);
            session.setType(AdapterAgent.ADAPTER_TYPE_CALL);
            session.addExtras(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.TWILIO.toString());
            session.setAdapterID(config.getConfigId());
            session.storeSession();
        }

        Question question = session.getQuestion();
        if (question == null) {
            question = Question.fromURL(url, session.getAdapterConfig().getConfigId(), formattedRemoteId, localID,
                                        session.getDdrRecordId(), session.getKey(), extraParams);
        }
        
        if (!ServerUtils.isValidBearerToken(session, config, dialogLog)) {
            Language language = ServerUtils.getLanguage(question, config, null);
            String insufficientCreditMessage = ServerUtils.getInsufficientMessage(language);
            return Response.ok(renderExitQuestion(question, Arrays.asList(insufficientCreditMessage), session.getKey()))
                                            .build();
        }
        // Check if we were able to load a question
        if (question == null) {
            //If not load a default error message
            question = Question.getError(config.getPreferred_language());
        }
        session.setQuestion(question);

        if (session.getQuestion() != null) {
            return handleQuestion(question, config, formattedRemoteId, session.getKey(), extraParams);
        }
        else {
            return Response.ok().build();
        }
    }
	
    /**
     * The answer inputs are redirected to this endpoint
     * 
     * @param answer_id
     *            This is generally not associated with a twilio answer
     * @param answer_input
     *            The actual answer given for a previous question
     * @param localID
     *            The from address of this call
     * @param remoteID
     *            The to address of this call
     * @param direction
     *            "inbound" or "outbound-dial"
     * @param recordingUrl
     *            Url for the voice recording if previous question was of type
     *            OPEN_AUDIO
     * @param dialCallStatus
     *            The call status
     * @param callSid
     *            The external id for this call. This can also be the parent
     *            externalId if previous question was a referral
     * @return
     */
    @Path("answer")
    @GET
    @Produces("application/xml")
    public Response answer(@QueryParam("answerId") String answer_id, @QueryParam("Digits") String answer_input,
        @QueryParam("From") String localID, @QueryParam("To") String remoteID,
        @QueryParam("Direction") String direction, @QueryParam("RecordingUrl") String recordingUrl,
        @QueryParam("DialCallStatus") String dialCallStatus, @QueryParam("CallSid") String callSid) {

        TwiMLResponse twiml = new TwiMLResponse();
        localID = checkAnonymousCallerId(localID);

        try {
            answer_input = answer_input != null ? URLDecoder.decode(answer_input, "UTF-8") : answer_input;
        }
        catch (UnsupportedEncodingException e) {
            log.warning(String.format("Answer input decode failed for: %s", answer_input));
        }

        if (recordingUrl != null) {
            answer_input = recordingUrl.replace(".wav", "") + ".wav";
        }

        if (direction.equals("inbound")) {
            String tmpLocalId = new String(localID);
            localID = new String(remoteID);
            remoteID = tmpLocalId;
        }

        Session session = Session.getSessionByExternalKey(callSid);
        List<String> callIgnored = Arrays.asList("no-answer", "busy", "canceled", "failed");

        if (session != null) {
            
            //add a tag in the session saying its picked up
            session.setCallPickedUpStatus(true);
            session.storeSession();
            /*
             * Important note: Remove the referralSession. Twilio gives a
             * "completed" status on a preconnect pickup-accept and a preconnect
             * pickup-reject (for pickup-decline it gives a no-answer status)
             */
            if ("completed".equals(dialCallStatus)) {

                AdapterConfig config = session.getAdapterConfig();

                //check if this is the parent session to a linked child session due to a redirect
                Session linkedChildSession = session.getLinkedChildSession();
                //if the linked child session is found and not pickedup. trigger the next question
                if (linkedChildSession != null && !linkedChildSession.isCallPickedUp()) {

                    //handle the next question if there is any.. else stop costs
                    Question answeredAndHungUpQuestion = session.getQuestion().event("hangup", "Call hungup",
                                                                                     session.getPublicExtras(),
                                                                                     remoteID, session.getKey());
                    Response renderedNextQuestion = handleQuestion(answeredAndHungUpQuestion,
                                                                   session.getAdapterConfig(), remoteID,
                                                                   session.getKey(), null);
                    // if its not a empty response (i.e a dont do anything request to twilio), then do something, instead of hangup :)
                    if (renderedNextQuestion != null &&
                        !(new TwiMLResponse()).toXML().equalsIgnoreCase(renderedNextQuestion.getEntity().toString())) {
                        return renderedNextQuestion;
                    }
                }
                finalizeCall(config, session, callSid, remoteID);
            }
            //if call is rejected. call the hangup event
            else if (callIgnored.contains(dialCallStatus) && session.getQuestion() != null) {

                session.addExtras("requester", session.getLocalAddress());
                Question noAnswerQuestion = session.getQuestion().event("timeout", "Call rejected",
                                                                        session.getPublicExtras(), remoteID,
                                                                        session.getKey());
                AdapterConfig config = session.getAdapterConfig();
                finalizeCall(config, session, callSid, remoteID);
                return handleQuestion(noAnswerQuestion, session.getAdapterConfig(), remoteID, session.getKey(), null);
            }

            Question question = session.getQuestion();
            if (question != null) {
                String responder = session.getRemoteAddress();
                if (session.killed) {
                    log.warning("session is killed");
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }
                if (question.getType() != null && !question.getType().equalsIgnoreCase("comment")) {
                    dialogLog.log(LogLevel.INFO,
                                  session.getAdapterConfig(),
                                  String.format("Answer input: %s from: %s to question: %s", answer_input,
                                                session.getRemoteAddress(),
                                                question.getQuestion_expandedtext(session.getKey())), session);
                }
                String answerForQuestion = question.getQuestion_expandedtext(session.getKey());
                question = question.answer(responder, session.getAdapterConfig().getConfigId(), answer_id,
                                           answer_input, session.getKey());
                //reload the session
                session = Session.getSession(session.getKey());
                session.setQuestion(question);
                session.storeSession();
                //check if ddr is in session. save the answer in the ddr
                if (session.getDdrRecordId() != null) {
                    try {
                        DDRRecord ddrRecord = DDRRecord.getDDRRecord(session.getDdrRecordId(), session.getAccountId());
                        if (ddrRecord != null) {
                            ddrRecord.addAdditionalInfo(DDRRecord.ANSWER_INPUT_KEY + ":" + answerForQuestion,
                                                        answer_input);
                            ddrRecord.createOrUpdateWithLog(session);
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return handleQuestion(question, session.getAdapterConfig(), responder, session.getKey(), null);
            }
            else {
                log.warning("No question found in session!");
            }
        }
        else {
            log.warning("No session found for external call id: " + callSid);
            dialogLog.severe(null, "No session found!", session);
        }
        String reply = twiml.toXML();
        return Response.ok(reply).build();
    }
	
    @Path("timeout")
    @GET
    @Produces("application/xml")
    public Response timeout(@QueryParam("From") String localID, @QueryParam("To") String remoteID,
        @QueryParam("Direction") String direction, @QueryParam("CallSid") String callSid) throws Exception {

        localID = checkAnonymousCallerId(localID);

        //swap local and remote ids if its an incoming call
        if (direction.equals("inbound")) {
            String tmpLocalId = new String(localID);
            localID = new String(remoteID);
            remoteID = tmpLocalId;
        }
        Session session = Session.getSessionByExternalKey(callSid);
        if (session != null) {
            Question question = session.getQuestion();
            String responder = session.getRemoteAddress();
            if (session.killed) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
            dialogLog.log(LogLevel.INFO,
                          session.getAdapterConfig(),
                          String.format("Timeout from: %s for question: %s", responder,
                                        question.getQuestion_expandedtext(session.getKey())), session);
            HashMap<String, Object> extras = new HashMap<String, Object>();
            extras.put("sessionKey", session.getKey());
            extras.put("requester", session.getLocalAddress());
            question = question.event("timeout", "No answer received", extras, responder, session.getKey());
            session.setQuestion(question);
            if (question != null) {
                String retryLimit = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.RETRY_LIMIT);
                retryLimit = retryLimit != null ? retryLimit : String.valueOf(Question.DEFAULT_MAX_QUESTION_LOAD);
                Integer retryCount = session.getRetryCount();
                retryCount = retryCount != null ? retryCount : 0;
                if (retryCount < Integer.parseInt(retryLimit)) {
                    session.setRetryCount(++retryCount);
                }
                else {
                    //hangup so set question to null
                    question = null;
                }
            }
            else {
                log.warning("No question found for this session :" + session.getKey());
            }
            session.storeSession();
            return handleQuestion(question, session.getAdapterConfig(), responder, session.getKey(), null);
        }
        else {
            log.warning("Strange that no session is found for external call id: " + callSid);
        }
        TwiMLResponse twiml = new TwiMLResponse();
        String reply = twiml.toXML();
        return Response.ok(reply).build();
    }
	
    @Path("preconnect")
    @GET
    @Produces("application/xml")
    public Response preconnect(@QueryParam("From") String localID, @QueryParam("AccountSid") String accountSid,
        @QueryParam("To") String remoteID,
        @QueryParam("Direction") String direction, @QueryParam("CallSid") String callSid) {

        String reply = (new TwiMLResponse()).toXML();
        Session session = Session.getSessionByExternalKey(callSid);
        //fetch session from parent call
        if (session == null) {
            session = fetchSessionFromParent(localID, accountSid, callSid);
        }

        if (session != null && session.getQuestion() != null) {
            Question question = session.getQuestion();
            String responder = session.getRemoteAddress();

            if (session.killed) {
                return Response.status(Response.Status.BAD_REQUEST).build();
            }
            dialogLog.log(LogLevel.INFO,
                          session.getAdapterConfig(),
                          String.format("Wrong answer received from: %s for question: %s", responder,
                                        question.getQuestion_expandedtext(session.getKey())), session);

            answered(direction, remoteID, localID, session.getKey());

            HashMap<String, String> extras = new HashMap<String, String>();
            extras.put("sessionKey", session.getKey());
            extras.put("requester", session.getLocalAddress());
            question = question.event("preconnect", "preconnect event", extras, responder, session.getKey());
            //reload the session
            session = Session.getSession(session.getKey());
            session.setQuestion(question);
            session.storeSession();
            return handleQuestion(question, session.getAdapterConfig(), responder, session.getKey(), null);
        }
        return Response.ok(reply).build();
    }

    @Path("cc")
    @GET
    public Response receiveCCMessage(@QueryParam("CallSid") String callSid, @QueryParam("From") String localID,
        @QueryParam("To") String remoteID, @QueryParam("Direction") String direction,
        @QueryParam("CallStatus") String status) {

        localID = checkAnonymousCallerId(localID);

        log.info("Received twiliocc status: " + status);

        if (direction.equals("outbound-api")) {
            direction = "outbound";
        }
        else if (direction.equals("inbound")) {
            String tmpLocalId = new String(localID);
            localID = remoteID;
            remoteID = tmpLocalId;
        }
        AdapterConfig config = AdapterConfig.findAdapterConfig(AdapterAgent.ADAPTER_TYPE_CALL, localID);
        Session session = Session.getSessionByExternalKey(callSid);
        if (session != null) {
            //update session with call timings
            if (status.equals("completed")) {
                finalizeCall(config, session, callSid, remoteID);
            }
        }
        log.info("Session key: or external sid" + session != null ? session.getKey() : callSid);
        return Response.ok("").build();
    }
    
    public void answered(String direction, String remoteID, String localID, String sessionKey) {

        log.info("call answered with:" + direction + "_" + remoteID + "_" + localID);
        Session session = Session.getSession(sessionKey);
        //for direction = transfer (redirect event), json should not be null        
        //make sure that the answered call is not triggered twice
        if (session != null && session.getQuestion() != null && !isEventTriggered("answered", session)) {
            String responder = session.getRemoteAddress();
            String referredCalledId = session.getAllExtras().get("referredCalledId");
            HashMap<String, Object> timeMap = new HashMap<String, Object>();
            timeMap.put("referredCalledId", referredCalledId);
            timeMap.put("sessionKey", sessionKey);
            timeMap.put("requester", session.getLocalAddress());
            session.getQuestion().event("answered", "Answered", timeMap, responder, session.getKey());
            dialogLog.log(LogLevel.INFO, session.getAdapterConfig(),
                          String.format("Call from: %s answered by: %s", session.getLocalAddress(), responder), session);
        }
    }
	
    /**
     * Retrieve call information and with that: - update ddr record - destroy
     * session - send hangup
     * 
     * @param config
     * @param session
     * @param callSid
     * @param direction
     * @param remoteID
     */
    private void finalizeCall(AdapterConfig config, Session session, String callSid, String remoteID) {

        String accountSid = config.getAccessToken();
        String authToken = config.getAccessTokenSecret();
        TwilioRestClient client = new TwilioRestClient(accountSid, authToken);

        Call call = client.getAccount().getCall(callSid);

        if (session == null) {
            remoteID = call.getTo();
            session = Session.getSessionByExternalKey(callSid);
        }

        if (session != null) {
            log.info(String.format("Finalizing call for id: %s, internal id: %s", session.getKey(),
                                   session.getInternalSession()));
            String direction = call.getDirection() != null && call.getDirection().equalsIgnoreCase("outbound-dial") ? "outbound"
                                                                                                                   : "inbound";
            try {
                //sometimes answerTimeStamp is only given in the ACTIVE ccxml
                updateSessionWithCallTimes(session, call);
                session.setDirection(direction);
                session.setRemoteAddress(remoteID);
                session.setLocalAddress(config.getMyAddress());
                session.storeSession();
                
                //flush the keys if ddrProcessing was successful
                if (DDRUtils.stopDDRCosts(session.getKey(), true)) {
                    session.drop();
                }
                hangup(session);

            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        else {
            log.warning("Failed to finalize call because no session was found for: " + callSid);
        }
    }
    
    /**
     * hang up a call based on the session.
     * 
     * @param session
     *            if null, doesnt trigger an hangup event. Also expects a
     *            question to be there in this session, or atleast a startURL
     *            from where the question can be fetched.
     * @return
     * @throws Exception
     */
    public Response hangup(Session session) throws Exception {

        if (session != null) {
            log.info("call hangup with:" + session.getDirection() + ":" + session.getRemoteAddress() + ":" +
                     session.getLocalAddress());
            if (session.getQuestion() == null) {
                Question question = Question.fromURL(session.getStartUrl(), session.getAdapterConfig().getConfigId(),
                                                     session.getRemoteAddress(), session.getLocalAddress(),
                                                     session.getDdrRecordId(), session.getKey(), new HashMap<String, String>());
                session.setQuestion(question);
            }
            if (session.getQuestion() != null && !isEventTriggered("hangup", session)) {
                
                HashMap<String, Object> timeMap = getTimeMap(session.getStartTimestamp(), session.getAnswerTimestamp(),
                                                             session.getReleaseTimestamp());
                timeMap.put("referredCalledId", session.getAllExtras().get("referredCalledId"));
                timeMap.put("sessionKey", session.getKey());
                if(session.getAllExtras() != null && !session.getAllExtras().isEmpty()) {
                    timeMap.putAll(session.getPublicExtras());
                }
                Response hangupResponse = handleQuestion(null, session.getAdapterConfig(), session.getRemoteAddress(),
                                                         session.getKey(), null);
                timeMap.put("requester", session.getLocalAddress());
                session.getQuestion().event("hangup", "Hangup", timeMap, session.getRemoteAddress(), session.getKey());
                dialogLog.log(LogLevel.INFO, session.getAdapterConfig(),
                              String.format("Call hungup from: %s", session.getRemoteAddress()), session);
                return hangupResponse;
            }
            else {
                log.info("no question received");
            }
        }
        return Response.ok("").build();
    }
    
    /**
     * Updates the session with the call times
     * @param session
     * @param call
     * @return
     * @throws ParseException
     */
    public static Session updateSessionWithCallTimes(Session session, Call call) throws ParseException {

        if (call != null && session != null) {
            String pattern = "EEE, dd MMM yyyy HH:mm:ss Z";
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ENGLISH);
            String created = call.getProperty("date_created");
            session.setStartTimestamp(format.parse(created).getTime() + "");
            if (call.getEndTime() != null) {
                session.setReleaseTimestamp(format.parse(call.getEndTime()).getTime() + "");
            }
            if (call.getDuration() != null) {
                if(call.getDuration().equals("0")) {
                    session.setAnswerTimestamp(session.getReleaseTimestamp());
                }
                else if(call.getStartTime() != null) {
                    session.setAnswerTimestamp(format.parse(call.getStartTime()).getTime() + "");
                }
            }
            else {
                session.setAnswerTimestamp(null);
                session.setReleaseTimestamp(session.getStartTimestamp());
            }
            if (session.getReleaseTimestamp() == null && session.getAnswerTimestamp() != null) {
                Long releaseTimeByDuration = Long.parseLong(session.getAnswerTimestamp()) +
                                             (Long.parseLong(call.getDuration()) * 1000);
                session.setReleaseTimestamp(releaseTimeByDuration + "");
            }
            return session;
        }
        return null;
    }
    
    /**
     * @param startTime
     * @param answerTime
     * @param releaseTime
     * @return
     */
    private HashMap<String, Object> getTimeMap( String startTime, String answerTime, String releaseTime )
    {
        HashMap<String, Object> timeMap = new HashMap<String, Object>();
        timeMap.put( "startTime", startTime );
        timeMap.put( "answerTime", answerTime );
        timeMap.put( "releaseTime", releaseTime );
        return timeMap;
    }
    
    /**
     * check if for this session an 
     * @param eventName
     * @param session
     * @return
     */
    private static boolean isEventTriggered(String eventName, Session session) {

        if (session != null) {
            if (session.getAllExtras().get("event_" + eventName) != null) {
                String timestamp = TimeUtils.getStringFormatFromDateTime(Long.parseLong(session.getAllExtras()
                                                .get("event_" + eventName)), null);
                log.warning(eventName + "event already triggered before for this session at: " + timestamp);
                return true;
            }
            else {
                session.getAllExtras().put("event_" + eventName, String.valueOf(TimeUtils.getServerCurrentTimeInMillis()));
                session.storeSession();
            }
        }
        return false;
    }
	
    public class Return{
        ArrayList<String> prompts;
        Question question;

        public Return( ArrayList<String> prompts, Question question ) {
            this.prompts = prompts;
            this.question = question;
        }
    }
	
    public Return formQuestion(Question question, String adapterID, String address, String ddrRecordId,
                               String sessionKey, Map<String, String> extraParams) {

        ArrayList<String> prompts = new ArrayList<String>();
        for (int count = 0; count <= LOOP_DETECTION; count++) {
            if (question == null)
                break;
            log.info("Going to form question of type: " + question.getType());
            if (question.getType() == null) {
                question = null;
                break;
            }
            String preferred_language = question.getPreferred_language();
            question.setPreferred_language(preferred_language);
            String qText = question.getQuestion_text();

            if (qText != null && !qText.equals("")) {
                prompts.add(qText);
            }
            
            if (question.getType().equalsIgnoreCase("closed")) {
                for (Answer ans : question.getAnswers()) {
                    String answer = ans.getAnswer_text();
                    if (answer != null && !answer.equals("") && !answer.startsWith("dtmfKey://")) {
                        prompts.add(answer);
                    }
                }
                break; //Jump from forloop
            }
            else if (question.getType().equalsIgnoreCase("comment")) {
                //question = question.answer(null, adapterID, null, null);
                break;
            }
            else if (question.getType().equalsIgnoreCase("referral")) {
                if (question.getUrl() != null && !question.getUrl().startsWith("tel:")) {
                    question = Question.fromURL(question.getUrl(), adapterID, address, ddrRecordId, sessionKey, extraParams);
                    //question = question.answer(null, null, null);
                    //					break;
                }
                else {
                    // Break out because we are going to reconnect
                    break;
                }
            }
            else {
                break; //Jump from forloop (open questions, etc.)
            }
        }
        return new Return(prompts, question);
    }
    
    /**
     * Gets the corresponding twilio account linked to the given config
     * 
     * @param config
     * @return
     */
    public static Account getTwilioAccount(String accountSid, String authToken) {

        if (accountSid != null && authToken != null) {
            TwilioRestClient twilio = new TwilioRestClient(accountSid, authToken);
            // Get the main account (The one we used to authenticate the client)
            return twilio.getAccount();
        }
        return null;
    }
	
    protected String renderComment(Question question, ArrayList<String> prompts, String sessionKey) {

        TwiMLResponse twiml = new TwiMLResponse();
        try {
            addPrompts(prompts, question.getPreferred_language(), twiml);
            if (question != null && question.getAnswers() != null && !question.getAnswers().isEmpty()) {
                Redirect redirect = new Redirect(getAnswerUrl());
                redirect.setMethod("GET");
                twiml.append(redirect);
            }
        }
        catch (TwiMLException e) {
            e.printStackTrace();
        }
        return twiml.toXML();
    }
    
    protected String renderReferral(Question question,ArrayList<String> prompts, String sessionKey, String remoteID){
        TwiMLResponse twiml = new TwiMLResponse();

        try {
            addPrompts( prompts, question.getPreferred_language(), twiml );

            String redirectTimeoutProperty = question
                .getMediaPropertyValue( MediumType.BROADSOFT,
                                        MediaPropertyKey.TIMEOUT );
            String redirectTimeout = redirectTimeoutProperty != null ? redirectTimeoutProperty.replace( "s", "" ) : "30";
            int timeout = 30;
            try {
                timeout = Integer.parseInt( redirectTimeout );
            }
            catch ( NumberFormatException e ) {
                e.printStackTrace();
            }

            com.twilio.sdk.verbs.Number number = new com.twilio.sdk.verbs.Number( question.getUrl() );

            number.setMethod( "GET" );
            number.setUrl( getPreconnectUrl() );

            Dial dial = new Dial();
            dial.setCallerId( remoteID );
            dial.append( number );
            dial.setTimeout( timeout );

            dial.setMethod( "GET" );
            dial.setAction( getAnswerUrl() );

            twiml.append( dial );
        }
        catch ( TwiMLException e ) {
            log.warning( "Failed to create referal" );
        }
        return twiml.toXML();
    }
	
    protected String renderClosedQuestion(Question question, ArrayList<String> prompts, String sessionKey) {

        try {
            sessionKey = URLEncoder.encode(sessionKey, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        TwiMLResponse twiml = new TwiMLResponse();
        Gather gather = new Gather();
        gather.setAction(getAnswerUrl());
        gather.setMethod("GET");
        gather.setNumDigits(1);

        String noAnswerTimeout = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT);
        
        boolean useHash = true;
        if(question.getAnswers().size() > 11) {
        	useHash = false;
        }
        else {
            List<Answer> answers = question.getAnswers();
            for (Answer answer : answers) {
                if (answer != null && answer.getAnswer_text() != null &&
                    answer.getAnswer_text().startsWith("dtmfKey://#")) {

                    useHash = true;
                    break;
                }
            }
        }
        
        //assign a default timeout if one is not specified
        noAnswerTimeout = noAnswerTimeout != null ? noAnswerTimeout : "5";
        if (noAnswerTimeout.endsWith("s")) {
            log.warning("No answer timeout must end with 's'. E.g. 10s. Found: " + noAnswerTimeout);
            noAnswerTimeout = noAnswerTimeout.replace("s", "");
        }
        int timeout = 5;
        try {
            timeout = Integer.parseInt(noAnswerTimeout);
        }
        catch (NumberFormatException e) {
            e.printStackTrace();
        }
        gather.setTimeout(timeout);
        if(useHash) {
        	gather.setFinishOnKey("");
        }
        try {
            addPrompts(prompts, question.getPreferred_language(), gather);
            
            twiml.append(gather);
            Redirect redirect = new Redirect(getTimeoutUrl());
            redirect.setMethod("GET");
            twiml.append(redirect);
        }
        catch (TwiMLException e) {
            e.printStackTrace();
        }

        return twiml.toXML();
    }

    protected String renderOpenQuestion(Question question, ArrayList<String> prompts, String sessionKey) {

        TwiMLResponse twiml = new TwiMLResponse();

        String typeProperty = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TYPE);
        if (typeProperty != null && typeProperty.equalsIgnoreCase("audio")) {
            renderVoiceMailQuestion(question, prompts, sessionKey, twiml);
        }
        else {

            Gather gather = new Gather();
            gather.setAction(getAnswerUrl());
            gather.setMethod("GET");

            String dtmfMaxLength = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                  MediaPropertyKey.ANSWER_INPUT_MAX_LENGTH);
            if (dtmfMaxLength != null) {
                try {
                    int digits = Integer.parseInt(dtmfMaxLength);
                    gather.setNumDigits(digits);
                }
                catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }

            String noAnswerTimeout = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT);
            //assign a default timeout if one is not specified

            noAnswerTimeout = noAnswerTimeout != null ? noAnswerTimeout : "5";
            if (noAnswerTimeout.endsWith("s")) {
                log.warning("No answer timeout must end with 's'. E.g. 10s. Found: " + noAnswerTimeout);
                noAnswerTimeout = noAnswerTimeout.replace("s", "");
            }
            int timeout = 5;
            try {
                timeout = Integer.parseInt(noAnswerTimeout);
            }
            catch (NumberFormatException e) {
                e.printStackTrace();
            }
            gather.setTimeout(timeout);

            try {
                addPrompts(prompts, question.getPreferred_language(), gather);

                twiml.append(gather);
                Redirect redirect = new Redirect(getTimeoutUrl());
                redirect.setMethod("GET");
                twiml.append(redirect);
            }
            catch (TwiMLException e) {
                e.printStackTrace();
            }
        }

        return twiml.toXML();
    }

    /**
     * renders/updates the xml for recording an audio and posts it to the user
     * on the callback
     * 
     * @param question
     * @param prompts
     * @param sessionKey
     * @param outputter
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    protected void renderVoiceMailQuestion(Question question, ArrayList<String> prompts, String sessionKey,
        TwiMLResponse twiml) {

        addPrompts(prompts, question.getPreferred_language(), twiml);

        Record record = new Record();
        record.setAction(getAnswerUrl());
        record.setMethod("GET");

        // Set max voicemail length
        //assign a default voice mail length if one is not specified
        String voiceMessageLengthProperty = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                           MediaPropertyKey.VOICE_MESSAGE_LENGTH);
        voiceMessageLengthProperty = voiceMessageLengthProperty != null ? voiceMessageLengthProperty : "3600";
        int length = 15;
        try {
            length = Integer.parseInt(voiceMessageLengthProperty);
        }
        catch (NumberFormatException e) {
            log.warning("Failed to parse timeout for voicemail e: " + e.getMessage());
        }
        record.setMaxLength(length);

        // Set timeout
        String timeoutProperty = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.TIMEOUT);
        timeoutProperty = timeoutProperty != null ? timeoutProperty : "20";
        int timeout = 5;
        try {
            timeout = Integer.parseInt(timeoutProperty);
        }
        catch (NumberFormatException e) {
            log.warning("Failed to parse timeout for voicemail e: " + e.getMessage());
        }

        record.setTimeout(timeout);

        // Set voicemail beep
        String voiceMailBeep = question.getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.VOICE_MESSAGE_BEEP);
        voiceMailBeep = voiceMailBeep != null ? voiceMailBeep : "true";
        boolean beep = Boolean.parseBoolean(voiceMailBeep);
        record.setPlayBeep(beep);

        try {
            twiml.append(record);

            Redirect redirect = new Redirect(getTimeoutUrl());
            redirect.setMethod("GET");
            twiml.append(redirect);
        }
        catch (TwiMLException e) {
            log.warning("Failed to append record");
        }
    }
    
    protected String renderExitQuestion(Question question, List<String> prompts, String sessionKey) {

        TwiMLResponse twiml = new TwiMLResponse();
        addPrompts(prompts, question.getPreferred_language(), twiml);
        try {
            twiml.append(new Hangup());
        }
        catch (TwiMLException e) {
            log.warning("Failed to append hangup");
        }
        return twiml.toXML();
    }
    
    protected void addPrompts(List<String> prompts, String language, Verb twiml) {

        String lang = language.contains("-") ? language : "nl-NL";
        try {
            for (String prompt : prompts) {
                if (prompt.startsWith("http")) {
                    twiml.append(new Play(prompt));
                }
                else {
                    Say say = new Say(prompt.replace("text://", ""));
                    say.setLanguage(lang);
                    twiml.append(say);
                }
            }
        }
        catch (TwiMLException e) {
            log.warning("failed to added prompts: " + e.getMessage());
        }
    }
    
    private Response handleQuestion(Question question, AdapterConfig adapterConfig, String remoteID, String sessionKey,
        Map<String, String> extraParams) {

        String result = (new TwiMLResponse()).toXML();
        Return res = formQuestion(question, adapterConfig.getConfigId(), remoteID, null, sessionKey, extraParams);
        if (question != null && !question.getType().equalsIgnoreCase("comment"))
            question = res.question;
        Session session = Session.getSession(sessionKey);
        // if the adapter is a trial adapter, add a introductory node
        log.info("question formed at handleQuestion is: " + ServerUtils.serializeWithoutException(question));
        log.info("prompts formed at handleQuestion is: " + res.prompts);

        if (question != null) {
            question.generateIds();
            session.setQuestion(question);
            session.setRemoteAddress(remoteID);
            session.storeSession();

            if (question.getType().equalsIgnoreCase("closed")) {
                result = renderClosedQuestion(question, res.prompts, sessionKey);
            }
            else if (question.getType().equalsIgnoreCase("open")) {
                result = renderOpenQuestion(question, res.prompts, sessionKey);
            }
            else if (question.getType().equalsIgnoreCase("referral")) {
                if (question.getUrl() != null && question.getUrl().startsWith("tel:")) {
                    // added for release0.4.2 to store the question in the
                    // session,
                    // for triggering an answered event
                    // Check with remoteID we are going to use for the call
                    String newRemoteID = remoteID;
                    String externalCallerId = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                             MediaPropertyKey.USE_EXTERNAL_CALLERID);
                    Boolean callerId = false;
                    if (externalCallerId != null) {
                        callerId = Boolean.parseBoolean(externalCallerId);
                    }
                    if (!callerId) {
                        newRemoteID = adapterConfig.getMyAddress();
                    }

                    log.info(String.format("current session key before referral is: %s and remoteId %s", sessionKey,
                                           remoteID));
                    String redirectedId = PhoneNumberUtils.formatNumber(question.getUrl().replace("tel:", ""), null);
                    if (redirectedId != null) {
                        
                        //check credits
                        if (!ServerUtils.isValidBearerToken(session, adapterConfig, dialogLog)) {

                            Language language = ServerUtils.getLanguage(question, adapterConfig, null);
                            String insufficientCreditMessage = ServerUtils.getInsufficientMessage(language);
                            return Response.ok(renderExitQuestion(null, Arrays.asList(insufficientCreditMessage),
                                                                  session.getKey())).build();
                        }
                        updateSessionOnRedirect(question, adapterConfig, remoteID, session, newRemoteID, redirectedId);
                        result = renderReferral(question, res.prompts, sessionKey, newRemoteID);
                    }
                    else {
                        log.severe(String.format("Redirect address is invalid: %s. Ignoring.. ", question.getUrl()
                                                        .replace("tel:", "")));
                    }
                }
            }
            else if (question.getType().equalsIgnoreCase("exit")) {
                result = renderExitQuestion(question, res.prompts, sessionKey);
            }
            else if (res.prompts.size() > 0) {
                result = renderComment(question, res.prompts, sessionKey);
            }
        }
        else if (res.prompts.size() > 0) {
            result = renderComment(null, res.prompts, sessionKey);
        }
        else {
            log.info("Going to hangup? So clear Session?");
        }
        log.info("Sending xml: " + result);
        return Response.status(Status.OK).type(MediaType.APPLICATION_XML).entity(result).build();
    }

    /** Creates a new {@link Session} and {@link DDRRecord} for the redirection.
     * @param question
     * @param adapterConfig
     * @param originalRemoteID The remoteId before the referral has taken place
     * @param session The previous sesison in contention that existed between the originalRemoteId and the referralFromId
     * @param referralFromID The origin Id of the referral
     * @param referralToId The destination id of the referral
     */
    private void updateSessionOnRedirect(Question question, AdapterConfig adapterConfig, String originalRemoteID,
        Session session, String referralFromID, String referralToId) {

        // update url with formatted redirecteId. RFC3966
        // returns format tel:<blabla> as expected
        question.setUrl(referralToId);
        // store the remoteId as its lost while trying to
        // trigger the answered event
        session.addExtras("referredCalledId", referralToId);
        session.setQuestion(question);
        session.setRemoteAddress(referralFromID);

        // create a new ddr record and session to catch the
        // redirect
        Session referralSession = Session.createSession(adapterConfig, referralFromID, referralToId);
        referralSession.addExtras("originalRemoteId", originalRemoteID);
        referralSession.addExtras("redirect", "true");
        referralSession.setAccountId(session.getAccountId());
        if (session.getDirection() != null) {
            DDRRecord ddrRecord = null;
            try {
                ddrRecord = DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig,
                                                                            referralSession.getAccountId(),
                                                                            referralToId, 1, question.getUrl(),
                                                                            referralSession.getKey());
                if (ddrRecord != null) {
                    ddrRecord.addAdditionalInfo(Session.TRACKING_TOKEN_KEY, referralSession.getTrackingToken());
                    ddrRecord.createOrUpdate();
                    referralSession.setDdrRecordId(ddrRecord.getId());
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe(String.format("Continuing without DDR. Error: %s", e.toString()));
            }
            referralSession.setDirection("outbound");
            referralSession.setTrackingToken(session.getTrackingToken());
        }
        referralSession.setQuestion(session.getQuestion());
        referralSession.addExtras(Session.PARENT_SESSION_KEY, session.getKey());
        referralSession.storeSession();
        session.addExtras(Session.CHILD_SESSION_KEY, referralSession.getKey());
        session.storeSession();
    }
    
    protected String getAnswerUrl() {
        return "http://"+Settings.HOST+"/dialoghandler/rest/twilio/answer";
    }
    
    protected String getTimeoutUrl() {
    	return "http://"+Settings.HOST+"/dialoghandler/rest/twilio/timeout";
    }
    
    protected String getPreconnectUrl() {
    	return "http://"+Settings.HOST+"/dialoghandler/rest/twilio/preconnect";
    }
    
    private String checkAnonymousCallerId(String callerId) {

        if (callerId.contains("+266696687")) {
            return "";
        }
        return callerId;
    }
    
    /**
     * Fetch the linked session using a parentCall sid stored within that of a
     * childCallSid
     * 
     * @param localID
     * @param accountSid
     * @param callSid
     * @return
     */
    public Session fetchSessionFromParent(String localID, String accountSid, String callSid) {

        try {
            AdapterConfig adapterConfig = AdapterConfig.findAdapterConfig(AdapterType.CALL.toString(), localID, null);
            //fetch the parent session for this preconnect
            Account twilioAccount = getTwilioAccount(accountSid != null ? accountSid : adapterConfig.getAccessToken(),
                                                     adapterConfig.getAccessTokenSecret());
            if (twilioAccount != null) {
                Call call = twilioAccount.getCall(callSid);
                return Session.getSessionFromParentExternalId(callSid, call.getParentCallSid());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            log.severe(String.format("Session fetch failed from parent callsid. localId: %s, accountSid: %s and callSid: %s",
                                     localID, accountSid, callSid));
        }
        return null;
    }
}
