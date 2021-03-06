package com.almende.dialog.adapter;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.event.ConnectionEvent;
import javax.mail.event.ConnectionListener;
import javax.mail.event.MessageChangedEvent;
import javax.mail.event.MessageChangedListener;
import javax.mail.event.MessageCountEvent;
import javax.mail.event.MessageCountListener;
import javax.mail.event.StoreEvent;
import javax.mail.event.StoreListener;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.DateTime;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.almende.util.TypeUtil;


public class MailServlet extends TextServlet implements Runnable, MessageChangedListener, MessageCountListener, ConnectionListener, StoreListener {

    public static final String SENDING_PROTOCOL_KEY = "SENDING_PROTOCOL";
    public static final String SENDING_HOST_KEY = "SENDING_HOST";
    public static final String SENDING_PORT_KEY = "SENDING_PORT";
    public static final String RECEIVING_PROTOCOL_KEY = "RECEIVING_PROTOCOL";
    public static final String RECEIVING_HOST_KEY = "RECEIVING_HOST";
    
    public static final String GMAIL_SENDING_PORT = "465";
    public static final String GMAIL_SENDING_HOST = "smtp.gmail.com";
    public static final String GMAIL_SENDING_PROTOCOL = "smtps";
    public static final String GMAIL_RECEIVING_HOST = "imap.gmail.com";
    public static final String GMAIL_RECEIVING_PROTOCOL = "imaps";
    public static final String CC_ADDRESS_LIST_KEY = "cc_email";
    public static final String BCC_ADDRESS_LIST_KEY = "bcc_email";
    private static final long serialVersionUID = 6892283600126803780L;
    private static final String servletPath = "/dialoghandler/_ah/mail/";
    public static final String DEFAULT_SENDER_EMAIL = "askfasttest@gmail.com";
    public static final String DEFAULT_SENDER_EMAIL_PASSWORD = "askask2times";

    public void doErrorPost(HttpServletRequest req, HttpServletResponse res) {

    }

    private AdapterConfig adapterConfig = null;

    public MailServlet() {

    }
	
    public MailServlet(AdapterConfig adapterConfig) {

        this.adapterConfig = adapterConfig;
    }
	
    @Override
    protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        Properties props = new Properties();
        javax.mail.Session mailSession = javax.mail.Session.getDefaultInstance(props, null);

        MimeMessage message = new MimeMessage(mailSession, req.getInputStream());

        String uri = req.getRequestURI();
        String recipient = uri.substring(servletPath.length());

        /*
         * Address[] recipients = message.getAllRecipients(); Address
         * recipient=null; if (recipients.length>0){ recipient = recipients[0];
         * }
         */
        return receiveMessage(message, recipient);
    }

    /**
     * method separated from the original
     * @link{MailServlet#receiveMessage(HttpServletRequest,
     * HttpServletResponse)} so that it can be tested without any data mock-ups.
     * @since 3/09/2013
     */
    private TextMessage receiveMessage(MimeMessage message, String recipient) throws Exception {

        TextMessage msg = new TextMessage();
        msg.setSubject("RE: " + message.getSubject());
        if (recipient != null && !recipient.equals("")) {
            msg.setLocalAddress(recipient.toString());
        }
        else {
            Address[] recipients = message.getAllRecipients();
            if (recipients.length > 0) {
                InternetAddress recip = (InternetAddress) recipients[0];
                msg.setLocalAddress(recip.getAddress());
            }
            else
                throw new Exception("MailServlet: Can't determine local address! (Dev)");
        }

        Address[] senders = message.getFrom();
        if (senders != null && senders.length > 0) {
            InternetAddress sender = (InternetAddress) senders[0];
            msg.setAddress(sender.getAddress());
            msg.setRecipientName(sender.getPersonal());
        }

        Multipart mp = null;
        if (message.getContent() instanceof Multipart) {
            mp = (Multipart) message.getContent();
        }
        else {
            mp = new MimeMultipart();
            mp.addBodyPart(new MimeBodyPart(new InternetHeaders(), message.getContent().toString().getBytes()));
        }
        if (mp.getCount() > 0) {
            msg.setBody(getText((Part) mp.getBodyPart(0)));
            Session ses = Session.getSessionByInternalKey(AdapterAgent.ADAPTER_TYPE_EMAIL, msg.getLocalAddress(),
                                                          msg.getAddress());
            if (ses != null && ses.getQuestion() != null && ses.getQuestion().getType().equals("closed")) {
                msg.setBody(getFristLineOfEmail(msg, mp.getBodyPart(0).getContentType()));
                log.info("Receive mail trimmed down body: " + msg.getBody());
            }
            else {
                log.info("Receive mail: " + msg.getBody());
            }
        }
        return msg;
    }
	
	private String getText(Part p) throws MessagingException, IOException {
		if (p.isMimeType("text/*")) {
			String s = (String) p.getContent();
			p.isMimeType("text/html");
			return s;
		}
		if (p.isMimeType("multipart/alternative")) {
			// prefer plain text over html
			Multipart mp = (Multipart) p.getContent();
			String text = null;
			for (int i = 0; i < mp.getCount(); i++) {
				Part bp = mp.getBodyPart(i);
				if (bp.isMimeType("text/plain")) {
					if (text == null)
						text = getText(bp);
					return text;
				} else if (bp.isMimeType("text/html")) {
					String s = getText(bp);
					if (s != null)
						return s;
				} else {
					return getText(bp);
				}
			}
			return text;
		} else if (p.isMimeType("multipart/*")) {
			Multipart mp = (Multipart) p.getContent();
			for (int i = 0; i < mp.getCount(); i++) {
				String s = getText(mp.getBodyPart(i));
				if (s != null)
					return s;
			}
		}

		return null;
	}
	
	protected String getFristLineOfEmail(TextMessage message, String contentType)throws Exception{
		String[] lines;
		if(contentType.equals("text/html")){
			lines = message.getBody().split("<br/?>");

		}else{
			lines =message.getBody().split("\r?\n"); 

		}
		for(int i =0; i < lines.length; i++){
			if(!("".equals(lines[i]))) {
				return lines[i];
			}
		}
		return "";
	}

    @Deprecated
    /**
     * @Deprecated use broadcastMessage instead
     */
    @Override
    protected int sendMessage( String message, String subject, String from, String fromName, String to, String toName,
        Map<String, Object> extras, AdapterConfig config, String accountId ) throws Exception
    {
        HashMap<String, String> addressNameMap = new HashMap<>( 1 );
        addressNameMap.put( to, toName );
        return broadcastMessage(message, subject, from, fromName, addressNameMap, extras, config, accountId);
    }
	
    @Override
    protected int broadcastMessage(String message, String subject, String from, String senderName,
                         Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config, String accountId) throws Exception {

        final String sendingHost = config.getProperties().get(SENDING_HOST_KEY) != null ? config.getProperties()
                                        .get(SENDING_HOST_KEY).toString() : GMAIL_SENDING_HOST;
        final String sendingPort = config.getProperties().get(SENDING_PORT_KEY) != null ? config.getProperties()
                                        .get(SENDING_PORT_KEY).toString() : GMAIL_SENDING_PORT;
        final String sendingProtocol = config.getProperties().get(SENDING_PROTOCOL_KEY) != null ? config
                                        .getProperties().get(SENDING_PROTOCOL_KEY).toString() : GMAIL_SENDING_PROTOCOL;

        //set username and password if its found and if its not an appengine mail proxy, else use the default one
        final String username = config.getXsiUser() != null && !config.getXsiUser().isEmpty() &&
                                !config.getXsiUser().contains("appspotmail.com") ? config.getXsiUser()
                                                                                : DEFAULT_SENDER_EMAIL;
        final String password = config.getXsiPasswd() != null && !config.getXsiPasswd().isEmpty() &&
                                !config.getXsiUser().contains("appspotmail.com") ? config.getXsiPasswd()
                                                                                : DEFAULT_SENDER_EMAIL_PASSWORD;
        Properties props = new Properties();
        props.put("mail.smtp.host", sendingHost);
        props.put("mail.smtp.port", sendingPort);
        props.put("mail.smtp.user", username);
        props.put("mail.smtp.password", password);
        props.put("mail.smtp.auth", "true");
        javax.mail.Session session = javax.mail.Session.getDefaultInstance(props);
        Message simpleMessage = new MimeMessage(session);
        int totalCount = 0;
        try {
            log.info(String.format("sending email from: %s senderName: %s to: %s with host: %s port: %s user: %s ",
                                   from, senderName, ServerUtils.serialize(addressNameMap), sendingHost, sendingPort,
                                   username));
            //add to list
            if (senderName != null) {
                simpleMessage.setFrom(new InternetAddress(from, senderName));
                //add the senderName to the reply list if its an emailId
                if (senderName.contains("@")) {
                    Address[] addresses = new InternetAddress[1];
                    addresses[0] = new InternetAddress(senderName);
                    simpleMessage.setReplyTo(addresses);
                }
            }
            else {
                simpleMessage.setFrom(new InternetAddress(from));
            }
            for (String address : addressNameMap.keySet()) {
                String toName = addressNameMap.get(address) != null ? addressNameMap.get(address) : address;
                try {
                    InternetAddress internetAddress = new InternetAddress(address, toName);
                    internetAddress.validate();
                    simpleMessage.addRecipient(Message.RecipientType.TO, internetAddress);
                }
                catch (Exception e) {
                    String errorMessage = String.format("Error in adding TO: receipient: %s (%s). Ignored. Message: %s",
                                                        address, toName, e.toString());
                    log.severe(errorMessage);
                    Session askfastSession = Session.getSessionByInternalKey(config.getAdapterType(),
                                                                             config.getMyAddress(), address);
                    logger.severe(config, errorMessage.replace("javax.mail.internet.AddressException: ", ""),
                                  askfastSession);
                }
            }
            if (extras != null) {
                //add cc list
                if (extras.get(CC_ADDRESS_LIST_KEY) != null) {
                    if (extras.get(CC_ADDRESS_LIST_KEY) instanceof Map) {
                        TypeUtil<HashMap<String, String>> injector = new TypeUtil<HashMap<String, String>>() {
                        };
                        HashMap<String, String> ccAddressNameMap = injector.inject(extras.get(CC_ADDRESS_LIST_KEY));
                        for (String address : ccAddressNameMap.keySet()) {
                            String toName = ccAddressNameMap.get(address) != null ? ccAddressNameMap.get(address)
                                                                                 : address;
                            try {
                                InternetAddress ccInternetAddress = new InternetAddress(address, toName);
                                ccInternetAddress.validate();
                                simpleMessage.addRecipient(Message.RecipientType.CC, ccInternetAddress);
                            }
                            catch (Exception e) {
                                String errorMessage = String.format("Error in adding CC: receipient: %s (%s). Ignored. Message: %s",
                                                                    address, toName, e.toString());
                                log.severe(errorMessage);
                                Session askfastSession = Session.getSession(Session.getInternalSessionKey(config, address));
                                logger.severe(config, errorMessage.replace("javax.mail.internet.AddressException: ", ""),
                                              askfastSession);
                            }
                        }
                    }
                    else {
                        log.severe(String.format("CC list seen but not of Map type: %s",
                                                 ServerUtils.serializeWithoutException(extras.get(CC_ADDRESS_LIST_KEY))));
                    }
                }
                //add bcc list
                if (extras.get(BCC_ADDRESS_LIST_KEY) != null) {
                    if (extras.get(BCC_ADDRESS_LIST_KEY) instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> bccAddressNameMap = (Map<String, String>) extras.get(BCC_ADDRESS_LIST_KEY);
                        for (String address : bccAddressNameMap.keySet()) {
                            String toName = bccAddressNameMap.get(address) != null ? bccAddressNameMap.get(address)
                                                                                  : address;
                            try {
                                InternetAddress bccInternetAddress = new InternetAddress(address, toName);
                                bccInternetAddress.validate();
                                simpleMessage.addRecipient(Message.RecipientType.BCC, bccInternetAddress);
                            }
                            catch (Exception e) {
                                String errorMessage = String.format("Error in adding BCC: receipient: %s (%s). Ignored. Message: %s",
                                                                    address, toName, e.toString());
                                log.severe(errorMessage);
                                Session askfastSession = Session.getSession(Session.getInternalSessionKey(config, address));
                                logger.severe(config, errorMessage.replace("javax.mail.internet.AddressException: ", ""),
                                              askfastSession);
                            }
                        }
                    }
                    else {
                        log.severe(String.format("BCC list seen but not of Map type: %s", ServerUtils
                                                        .serializeWithoutException(extras.get(BCC_ADDRESS_LIST_KEY))));
                    }
                }
            }
            simpleMessage.setSubject(subject);
            simpleMessage.setContent(message, "text/html; charset=utf-8");
            //attach the number of emails sent
            totalCount = simpleMessage.getAllRecipients().length;
            /*
             * if appspotmail is used as the senderId/from, then send the email
             * from the default account and attach the sender as the replyTo
             * option, to continue the dialog
             */
            if (from.contains("appspotmail.com")) {
                HashSet<Address> replyToAddresses = new HashSet<Address>();
                replyToAddresses.add(new InternetAddress(from));
                if (simpleMessage.getReplyTo() != null) {
                    for (Address address : simpleMessage.getReplyTo()) {
                        replyToAddresses.add(address);
                    }
                }
                simpleMessage.setReplyTo(replyToAddresses.toArray(new Address[replyToAddresses.size()]));
                sendEmailWithDefaultAccount(simpleMessage, session);
            }
            else {
                if (!ServerUtils.isInUnitTestingEnvironment()) {
                    //sometimes Transport.send(simpleMessage); is used, but for gmail it's different
                    Transport transport = session.getTransport(sendingProtocol);
                    transport.connect(sendingHost, Integer.parseInt(sendingPort), username, password);
                    transport.sendMessage(simpleMessage, simpleMessage.getAllRecipients());
                    transport.close();
                }
                else {
                    TestServlet.logForTest(simpleMessage);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            log.warning("Failed to send message, because encoding: " + e.getLocalizedMessage());
            throw e;
        }
        return totalCount;
    }
    
    public void readInboundEmail( AdapterConfig adapterConfig )
    {
        String receivingProtocol = adapterConfig.getProperties().get( RECEIVING_PROTOCOL_KEY ) != null ? adapterConfig
            .getProperties().get( RECEIVING_PROTOCOL_KEY ).toString() : GMAIL_RECEIVING_PROTOCOL;
        String receivingHost = adapterConfig.getProperties().get( RECEIVING_HOST_KEY ) != null ? adapterConfig
            .getProperties().get( RECEIVING_HOST_KEY ).toString() : GMAIL_RECEIVING_HOST;

        final String username = adapterConfig.getXsiUser();
        final String password = adapterConfig.getXsiPasswd();
        if ( username != null && !username.isEmpty() && password != null && !password.isEmpty() )
        {
            Properties props = new Properties();
            props.setProperty( "mail.store.protocol", receivingProtocol );
            javax.mail.Session mailSession = javax.mail.Session.getDefaultInstance( props, null );
            try
            {
                //fetch the last Email timestamp read
                String lastEmailTimestamp = Session.getString("incoming_email" + adapterConfig.getConfigId() );
                String updatedLastEmailTimestamp = null;
                //if no lastEmailTimestamp is seen, default it to when the adapter was created
                if ( lastEmailTimestamp == null )
                {
                    if ( adapterConfig.getProperties().get( AdapterConfig.ADAPTER_CREATION_TIME_KEY ) != null )
                    {
                        lastEmailTimestamp = adapterConfig.getProperties()
                            .get( AdapterConfig.ADAPTER_CREATION_TIME_KEY ).toString();
                    }
                    else
                    //default it from this morning 00:00:00
                    {
                        DateTime currentTime = TimeUtils.getServerCurrentTime();
                        currentTime = currentTime.minusMillis( currentTime.getMillisOfDay() );
                        lastEmailTimestamp = String.valueOf( currentTime.getMillis() );
                    }
                    Session.storeString( "lastEmailTimestamp", lastEmailTimestamp );
                }
                log.info( String.format( "initial EMail timestamp for adapterId: %s is: %s",
                    adapterConfig.getConfigId(), lastEmailTimestamp ) );

                Store store = mailSession.getStore( receivingProtocol );
                store.connect( receivingHost, username, password );
                Folder folder = store.getFolder( "INBOX" );
                folder.open( Folder.READ_ONLY );
                Message messages[] = null;
                if ( lastEmailTimestamp != null )
                {
                    ReceivedDateTerm receivedDateTerm = new ReceivedDateTerm( ComparisonTerm.GT, new Date(
                        Long.parseLong( lastEmailTimestamp ) ) );
                    messages = folder.search( receivedDateTerm );
                }
                else
                {
                    messages = folder.getMessages();
                }
                log.info( String.format( "%s emails fetched with timestamp greater than: %s",
                    messages != null ? messages.length : 0, lastEmailTimestamp ) );
                for ( int i = 0; messages != null && i < messages.length; i++ )
                {
                    if ( messages[i].getReceivedDate().getTime() > Long.parseLong( lastEmailTimestamp ) )
                    {
                        InternetAddress fromAddress = ( (InternetAddress) messages[i].getFrom()[0] );
                        //skip if the address contains no-reply as its address
                        if ( !fromAddress.toString().contains( "no-reply" )
                            && !fromAddress.toString().contains( "noreply" ) )
                        {
                            log.info( String
                                .format(
                                    "Email from: %s with subject: %s received at: %s is being responded. Last email timestamp was: %s",
                                    fromAddress.getAddress(), messages[i].getSubject(), messages[i].getReceivedDate()
                                        .getTime(), lastEmailTimestamp ) );
                            try
                            {
                                MimeMessage mimeMessage = new MimeMessage( mailSession, messages[i].getInputStream() );
                                mimeMessage.setFrom( fromAddress );
                                mimeMessage.setSubject( messages[i].getSubject() );
                                mimeMessage.setContent( messages[i].getContent(), messages[i].getContentType() );
                                TextMessage receiveMessage = receiveMessage( mimeMessage, adapterConfig.getMyAddress() );
                                processMessage( receiveMessage );
                            }
                            catch ( Exception e )
                            {
                                log.warning( String.format(
                                    "Adapter: %s of type: %s threw exception: %s while reading inboundEmail scedule",
                                    adapterConfig.getConfigId(), adapterConfig.getAdapterType(),
                                    e.getLocalizedMessage() ) );
                            }
                            updatedLastEmailTimestamp = String.valueOf( messages[i].getReceivedDate().getTime() );
                        }
                    }
                }
                folder.close( true );
                store.close();
                if ( updatedLastEmailTimestamp != null && !updatedLastEmailTimestamp.equals( lastEmailTimestamp ) )
                {
                    Session.storeString( "lastEmailTimestamp", lastEmailTimestamp );
                }
            }
            catch ( Exception e )
            {
                log.warning( String.format(
                    "Adapter: %s of type: %s threw exception: %s while reading inboundEmail scedule",
                    adapterConfig.getConfigId(), adapterConfig.getAdapterType(), e.getLocalizedMessage() ) );
            }
        }
    }
    
    public void listenForIncomingEmails() throws MessagingException
    {
        Folder emailFolder = getEmailStore( adapterConfig );
        emailFolder.addMessageChangedListener( this );
        emailFolder.addMessageCountListener( this );
        emailFolder.addConnectionListener( this );
    }
    
    private Folder getEmailStore(AdapterConfig adapterConfig) throws MessagingException
    {
        String receivingProtocol = adapterConfig.getProperties().get( RECEIVING_PROTOCOL_KEY ) != null ? adapterConfig
            .getProperties().get( RECEIVING_PROTOCOL_KEY ).toString() : GMAIL_RECEIVING_PROTOCOL;
        String receivingHost = adapterConfig.getProperties().get( RECEIVING_HOST_KEY ) != null ? adapterConfig
            .getProperties().get( RECEIVING_HOST_KEY ).toString() : GMAIL_RECEIVING_HOST;
        Properties props = new Properties();
        props.setProperty( "mail.store.protocol", receivingProtocol );
        javax.mail.Session session = javax.mail.Session.getDefaultInstance(props, null);
        Store store = session.getStore( receivingProtocol );
        store.connect( receivingHost, adapterConfig.getXsiUser(), adapterConfig.getXsiPasswd() );
        Folder folder = store.getFolder( "INBOX" );
        folder.open( Folder.READ_WRITE );
        store.addStoreListener( this );
        folder.close( false );
        return folder;
    }
    
	@Override
	protected String getServletPath() {
		return servletPath;
	}
	
	@Override
	protected String getAdapterType() {
		return AdapterAgent.ADAPTER_TYPE_EMAIL;
	}

    @Override
    public void run()
    {
        readInboundEmail( adapterConfig );
    }

    @Override
    public void messageChanged( MessageChangedEvent messageChangedEvent )
    {
        log.info( "messageChanged" + messageChangedEvent.toString() );
        String subject = null;
        Address fromAddress = null;
        try
        {
            Message message = messageChangedEvent.getMessage();
            subject = message.getSubject();
            fromAddress = message.getFrom()[0];
            if ( !fromAddress.toString().contains( "no-reply" ) && !fromAddress.toString().contains( "noreply" ) )
            {
                javax.mail.Session session = javax.mail.Session.getDefaultInstance( new Properties(), null );
                MimeMessage mimeMessage = new MimeMessage( session, message.getInputStream() );
                mimeMessage.setFrom( message.getFrom()[0] );
                mimeMessage.setSubject( subject );
                mimeMessage.setContent( message.getContent(), message.getContentType() );
                TextMessage receiveMessage = receiveMessage( mimeMessage, adapterConfig.getMyAddress() );
                processMessage( receiveMessage );
            }
        }
        catch ( Exception ex )
        {
            ex.printStackTrace();
            if ( subject != null && fromAddress != null )
            {
                log.severe( String.format(
                    "Error seen while trying to process the message. Subject: %s from: %s to: %s", subject,
                    fromAddress, adapterConfig.getMyAddress() ) );
            }
        }
    }

    @Override
    public void messagesAdded( MessageCountEvent e )
    {
        log.info( "messagesAdded" + e.toString() );
    }

    @Override
    public void messagesRemoved( MessageCountEvent e )
    {
        log.info( "messagesRemoved" + e.toString() );
    }

    @Override
    public void opened( ConnectionEvent e )
    {
        log.info( "opened" + e.toString() );
    }

    @Override
    public void disconnected( ConnectionEvent e )
    {
        log.info( "disconnected" + e.toString() );
    }

    @Override
    public void closed( ConnectionEvent e )
    {
        log.info( e.toString() );
    }

    @Override
    public void notification( StoreEvent e )
    {
        log.info( "notification" + e.toString() );
    }
    
    /**
     * used as a proxy to send outbound emails when the appspotemail is
     * configured as the fromAddress
     * 
     * @param simpleMessage
     * @param session
     * @throws NumberFormatException
     * @throws MessagingException
     */
    private void sendEmailWithDefaultAccount(Message simpleMessage, javax.mail.Session session)
        throws NumberFormatException, MessagingException {

        if (!ServerUtils.isInUnitTestingEnvironment()) {
            Transport transport = session.getTransport(GMAIL_SENDING_PROTOCOL);
            transport.connect(GMAIL_SENDING_HOST, Integer.parseInt(GMAIL_SENDING_PORT), DEFAULT_SENDER_EMAIL,
                              DEFAULT_SENDER_EMAIL_PASSWORD);
            transport.sendMessage(simpleMessage, simpleMessage.getAllRecipients());
            transport.close();
        }
        else {
            TestServlet.logForTest(simpleMessage);
        }
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

        return DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig, accountId, toAddress, message,
                                                               sessionKeyMap);
    }

    @Override
    protected String getProviderType() {

        return AdapterAgent.ADAPTER_TYPE_EMAIL;
    }
}
