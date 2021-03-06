package com.almende.dialog.adapter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import com.almende.dialog.IntegrationTest;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.uuid.UUID;

@Category(IntegrationTest.class)
public class MailServletIT extends TestFramework
{
    /**
     * test if an outgoing Email is triggered by the MailServlet 
     * @throws Exception 
     */
    @Test
    @Ignore
    public void sendDummyMessageTest() throws Exception
    {
        String testMessage = "testMessage";
        //create mail adapter
        AdapterConfig adapterConfig = createEmailAdapter( "askfasttest@gmail.com", "askask2times", null, null, null,
            null, null, null, null, new UUID().toString(), null );
        //create session
        Session.createSession( adapterConfig, remoteAddressEmail );
        
        //fetch and invoke the receieveMessage method
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( remoteAddressEmail, "Test" );
        String url = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType", QuestionInRequest.SIMPLE_COMMENT.name() );
        url = ServerUtils.getURLWithQueryParams( url, "question", testMessage );
        
        MailServlet mailServlet = new MailServlet();
        mailServlet.broadcastMessage(testMessage, "Test", adapterConfig.getXsiUser(), "Test message", addressNameMap,
                                     null, adapterConfig, adapterConfig.getOwner());
        Message message = super.getMessageFromDetails( remoteAddressEmail, localAddressMail, testMessage, "sendDummyMessageTest" );
        assertOutgoingTextMessage( message );
    }
    
    /**
     * test if a "dummy" TextMessage is generated and processed properly by MailServlet 
     * @throws Exception 
     */
    @Test
    public void MailServletReceiveDummyMessageTest() throws Exception
    {
        String initialAgentURL = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType", QuestionInRequest.APPOINTMENT.name());
        initialAgentURL = ServerUtils.getURLWithQueryParams( initialAgentURL, "question", "start" );
        
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_EMAIL, null, TEST_PUBLIC_KEY,
                                                          localAddressMail, initialAgentURL);
        //create session
        Session.createSession( adapterConfig, remoteAddressEmail );
        
        MimeMessage mimeMessage = new MimeMessage( javax.mail.Session.getDefaultInstance( new Properties(), null) );
        mimeMessage.setFrom( new InternetAddress( remoteAddressEmail ) );
        MimeMultipart mimeMultipart = getTestMimeMultipart( remoteAddressEmail, localAddressMail, "dummyData", null );
        mimeMessage.setContent( mimeMultipart );
        
        //fetch and invoke the receieveMessage method
        MailServlet mailServlet = new MailServlet();

        Method fetchMethodByReflection = fetchMethodByReflection( "receiveMessage", MailServlet.class, 
                                                                  Arrays.asList( MimeMessage.class, String.class ) );
        TextMessage textMessage = (TextMessage) invokeMethodByReflection( fetchMethodByReflection, mailServlet, 
                                                       Arrays.asList( mimeMessage, localAddressMail ));
        
        //fetch the processMessage function
        Method processMessage = fetchMethodByReflection( "processMessage", TextServlet.class,  TextMessage.class);
        int count = (Integer) invokeMethodByReflection( processMessage, mailServlet, textMessage );
        assertTrue( count == 1 );
    }
    
    /**
     * test if a "hi" TextMessage is generated and processed properly by XMPP servlet
     *  as a new session
     * @throws Exception 
     */
    @Test
    public void ReceiveAppointmentNewSessionMessageTest() throws Exception
    {
        String initialAgentURL = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.APPOINTMENT.name() );
        initialAgentURL = ServerUtils.getURLWithQueryParams( initialAgentURL, "question", "start" );
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_EMAIL, null, TEST_PUBLIC_KEY,
                                                          localAddressMail, initialAgentURL);
        //create session
        Session session = Session.createSession( adapterConfig, remoteAddressEmail );
        TextMessage textMessage = mailAppointmentInteraction("hi");
        //update the question text in the textMessage
        Question question = Question.fromURL(initialAgentURL, adapterConfig.getConfigId(), null, null);
        if(question != null) {
            textMessage.setBody(question.getTextWithAnswerTexts(session.getKey()));
        }
        assertOutgoingTextMessage(textMessage);
    }
    
    /**
     * use an existing session and send yes to it
     * @throws Exception 
     */
    @Test
    public void AcceptAppointmentExistingSessionMessageTest() 
    throws Exception
    {
        //use existing session to recreate a scenario where the first question is alrady asked
        ReceiveAppointmentNewSessionMessageTest();
        
        //send Yup to the Appointment question
        TextMessage textMessage = mailAppointmentInteraction(TestServlet.APPOINTMENT_YES_ANSWER);
        textMessage.setBody(TestServlet.APPOINTMENT_SECOND_QUESION);
        assertOutgoingTextMessage(textMessage);
    }
    
    /**
     * use an existing session and send yes to it
     * @throws Exception 
     */
    @Test
    public void AnswerSecondAppointmentQuestionMessageTest() 
    throws Exception
    {
        //use existing session to recreate a scenario where the second question is alrady asked
        AcceptAppointmentExistingSessionMessageTest();
        //send Yup to the Appointment question
        TextMessage textMessage = mailAppointmentInteraction("120");//send free for 120 mins
        textMessage.setBody(TestServlet.APPOINTMENT_ACCEPTANCE_RESPONSE);
        assertOutgoingTextMessage(textMessage);
    }

    /**
     * use an existing session and send no to it
     * @throws Exception 
     */
    @Test
    public void RejectAppointmentExistingSessionMessageTest() 
    throws Exception
    {
        //use existing session to recreate a scenario where the first question is alrady asked
        ReceiveAppointmentNewSessionMessageTest();
        
        //send Yup to the Appointment question
        TextMessage textMessage = mailAppointmentInteraction(TestServlet.APPOINTMENT_NO_ANSWER);
        textMessage.setBody(TestServlet.APPOINTMENT_REJECT_RESPONSE);
        assertOutgoingTextMessage(textMessage);
    }
    
    /**
     * test if a URL passed into the question_text is parsed normally
     * @throws Exception
     */
    @Test
    public void QuestionTextWithURLDoesNotCreateIssuesTest() throws Exception
    {
        String textMessage = "How are you doing?";
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterAgent.ADAPTER_TYPE_EMAIL, null, TEST_PUBLIC_KEY,
                                                          localAddressMail, "");
        //create session
        Session.createSession( adapterConfig, remoteAddressEmail );

        //fetch and invoke the receieveMessage method
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( remoteAddressEmail, "Test" );
        String url = TestServlet.TEST_SERVLET_PATH + TestServlet.OPEN_QUESTION_URL_WITH_SPACES + "/"
            + URLEncoder.encode( textMessage, "UTF-8" );
        
        MailServlet mailServlet = new MailServlet();
        mailServlet.startDialog(addressNameMap, null, null, url, "test", "sendDummyMessageTest", adapterConfig,
                                adapterConfig.getOwner());

        Message message = super.getMessageFromDetails( remoteAddressEmail, localAddressMail, textMessage,
            "sendDummyMessageTest" );
        assertOutgoingTextMessage( message );
    }

    /**
     * this is a test to test if the old message block is trimmed off 
     * when an email is sent using the reply button.
     * @throws Exception 
     */
    @Test
    @Ignore
    public void TripOldMessageByReplyRecieveProcessMessageTest() 
    throws Exception
    {
        //use existing session to recreate a scenario where the first question is alrady asked
        ReceiveAppointmentNewSessionMessageTest();
        
        //reply Yup to the Appointment question
        //adding some just text as part of the previous email
        String reply = TestServlet.APPOINTMENT_YES_ANSWER + " \n \n \n2013/9/6 <" + localAddressMail +"> \n \n> U heeft een ongeldig aantal gegeven. " +
        		"Geef een getal tussen 1 en 100 000. \n> \n \n \n \n--  \nKind regards, \nShravan Shetty "; 
        mailAppointmentInteraction( reply );
    }
    
    /**
     * @return
     * @throws Exception
     */
    private TextMessage mailAppointmentInteraction(String message) throws Exception
    {
        MimeMessage mimeMessage = new MimeMessage( javax.mail.Session.getDefaultInstance( new Properties(), null) );
        mimeMessage.setFrom( new InternetAddress( remoteAddressEmail ) );
        MimeMultipart mimeMultipart = getTestMimeMultipart( remoteAddressEmail, localAddressMail, message, null );
        mimeMessage.setContent( mimeMultipart );
        
        //fetch and invoke the receieveMessage method
        MailServlet mailServlet = new MailServlet();

        Method fetchMethodByReflection = fetchMethodByReflection( "receiveMessage", MailServlet.class, 
                                                                  Arrays.asList( MimeMessage.class, String.class ) );
        TextMessage textMessage = (TextMessage) invokeMethodByReflection( fetchMethodByReflection, mailServlet, 
                                                       Arrays.asList( mimeMessage, localAddressMail ));
        //fetch the processMessage function
        Method processMessage = fetchMethodByReflection( "processMessage", TextServlet.class,  TextMessage.class);
        int count = (Integer) invokeMethodByReflection( processMessage, mailServlet, textMessage );
        assertTrue( count == 1 );
        return textMessage;
    }

    private void assertOutgoingTextMessage( TextMessage textMessage ) throws Exception
    {
        javax.mail.Message messageFromDetails = getMessageFromDetails( textMessage.getAddress(),
            textMessage.getLocalAddress(), textMessage.getBody(), "" );
        assertTrue( TestServlet.getLogObject() instanceof javax.mail.Message );
        javax.mail.Message messageLogged = (javax.mail.Message) TestServlet.getLogObject();
        assertArrayEquals( messageFromDetails.getFrom(), messageLogged.getFrom() );
        assertArrayEquals( messageFromDetails.getAllRecipients(), messageLogged.getAllRecipients() );
        assertEquals( messageFromDetails.getSubject(), messageLogged.getSubject().replaceAll( "RE:|null", "" ).trim() );
        assertEquals( messageFromDetails.getContent().toString(), messageLogged.getContent().toString() );
    }
    
    private void assertOutgoingTextMessage(Message message) throws Exception
    {
        assertTrue(TestServlet.getLogObject() instanceof javax.mail.Message);
        javax.mail.Message messageLogged = (javax.mail.Message) TestServlet.getLogObject();
        assertArrayEquals(message.getFrom(), messageLogged.getFrom());
        assertArrayEquals(message.getAllRecipients(), messageLogged.getAllRecipients());
        assertEquals(message.getSubject(), messageLogged.getSubject().replaceAll("RE:|null","").trim());
        assertEquals(message.getContent().toString(), messageLogged.getContent().toString());
    }
}
