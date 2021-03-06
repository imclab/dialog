package com.almende.dialog.adapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.DateTime;
import org.scribe.model.Token;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.Facebook;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class WallFacebookServlet extends TextServlet {

    private static final long serialVersionUID = -5418264483130072610L;
    private static final int WALL_MESSAGE_RENTENTION = 7; // Only process message older then 7 days

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        ObjectMapper om = ParallelInit.getObjectMapper();
        Facebook fb = null;
        PrintWriter out = resp.getWriter();
        ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters(getAdapterType(), null, null);
        for(AdapterConfig config : adapters) {

            ArrayNode allMessages = om.createArrayNode();
            fb = new Facebook(new Token(config.getAccessToken(), config.getAccessTokenSecret()));

            DateTime msgDate = DateTime.now().minusDays(WALL_MESSAGE_RENTENTION);

            ArrayNode messages = fb.getWallMessages((msgDate.toDate().getTime()/1000)+"");
            for(JsonNode message : messages) {

                //proccess message
                // Don't process messages from yourself
                if(!config.getMyAddress().equals(message.get("from").get("id").asText())) {

                    String id = message.get("id").asText();
                    String since = Session.getString(getAdapterType()+"_comment_"+id);

                    if(since==null) {
                        since="0";
                    }

                    DateTime last = new DateTime(since);

                    if(message.get("message")!=null) {

                        TextMessage msg = new TextMessage();
                        msg.setAddress(id);
                        msg.setRecipientName(message.get("from").get("name").asText());
                        msg.setLocalAddress(config.getMyAddress());
                        try
                        {
                            if(message.get("comments").get("count").asInt() > 0) {

                                // Get the comments to respond to comment
                                ArrayNode comments = fb.getComments(id, (last.toDate().getTime()/1000)+"");
                                for(JsonNode comment : comments) {
                                    DateTime date = new DateTime(comment.get("created_time").asText());
                                    if(last.isBefore(date)) {
                                        if(!config.getMyAddress().equals(comment.get("from").get("id").asText())) {
                                            msg.setBody(comment.get("message").asText());
                                            processMessage(msg);
                                        }

                                        last = date;
                                    }
                                }
                            } else {

                                // Respond to this message
                                msg.setBody(message.get("message").asText());
                                DateTime date = new DateTime(message.get("created_time").asText());
                                if(last.isBefore(date))
                                    last = date;

                                processMessage(msg);
                            }
                        }
                        catch (Exception ex)
                        {
                            log.severe(ex.getLocalizedMessage());
                            ex.printStackTrace();
                        }
                    }

                    Session.storeString(getAdapterType()+"_comment_"+id, last.toString());
                }

                allMessages.add(message);
            }

            out.println(allMessages.toString());
        }
    }

    @Override
    protected int sendMessage(String message, String subject, String from, String fromName, String to, String toName,
        Map<String, Object> extras, AdapterConfig config, String accountId) {

        if (!ServerUtils.isInUnitTestingEnvironment()) {
            Facebook fb = new Facebook(new Token(config.getAccessToken(), config.getAccessTokenSecret()));
            fb.sendComment(message, to, toName);
        }
        return 1;
    }

    @Override
    protected int broadcastMessage(String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config, String accountId)
        throws Exception {

        int count = 0;
        for (String address : addressNameMap.keySet()) {
            String toName = addressNameMap.get(address);
            count = count + sendMessage(message, subject, from, senderName, address, toName, extras, config, accountId);
        }
        return count;
    }

    @Override
    protected TextMessage receiveMessage(HttpServletRequest req,
                                         HttpServletResponse resp) throws Exception {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected String getServletPath() {
        return "/facebook_wall";
    }

    @Override
    protected String getAdapterType() {
        return AdapterAgent.ADAPTER_TYPE_FACEBOOK;
    }

    @Override
    protected void doErrorPost(HttpServletRequest req, HttpServletResponse res)
            throws IOException {

    }
    
    @Override
    protected DDRRecord createDDRForIncoming(AdapterConfig adapterConfig, String accountId, String fromAddress,
        String message, String sessionKey) throws Exception {

        return DDRUtils.createDDRRecordOnIncomingCommunication(adapterConfig, accountId, fromAddress, message,
                                                               sessionKey);
    }

    @Override
    protected DDRRecord createDDRForOutgoing(AdapterConfig adapterConfig, String accountId, String senderName,
        Map<String, String> toAddress, String message, Map<String, String> sessionKeyMap) throws Exception {

        //add costs with no.of messages * recipients
        return DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig, accountId, toAddress, message,
                                                               sessionKeyMap);
    }

    @Override
    protected String getProviderType() {

        return AdapterAgent.ADAPTER_TYPE_FACEBOOK;
    }
}
