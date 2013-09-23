package com.almende.dialog.adapter;

import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.test.TestServlet;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CMServletTest extends TestFramework
{
    private static final String simpleQuestion = "How are you?";

    @Test
    public void outBoundBroadcastCallSenderNameNotNullTest() throws Exception
    {
        String remoteAddressVoice2 = "+31614753658";
        String senderName = "TestUser";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig( "CM", TEST_PUBLIC_KEY, "", "" );

        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( remoteAddressVoice, "testUser1" );
        addressNameMap.put( remoteAddressVoice2, "testUser2" );

        outBoundSMSCallXMLTest(addressNameMap, adapterConfig, simpleQuestion, senderName);
        assertXMLGeneratedFromOutBoundCall(addressNameMap, adapterConfig, simpleQuestion, senderName);
    }
    
    @Test
    public void outBoundSMSCallSenderNameNotNullTest() throws Exception
    {
        String senderName = "TestUser";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig( "CM", TEST_PUBLIC_KEY, "", "" );

        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put( remoteAddressVoice, null );
        outBoundSMSCallXMLTest(addressMap, adapterConfig, simpleQuestion, senderName );
        assertXMLGeneratedFromOutBoundCall(addressMap, adapterConfig, simpleQuestion, senderName);
    }

    /**
     * tests if an outbound call works when the sender name is null.
     * In this case it should pick up the adapter.Myaddress as the senderName. <br>
     * @return 
     */
    @Test
    public void outBoundSMSCallSenderNameNullTest() throws Exception
    {
        String myAddress = "ASK";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig( "CM", TEST_PUBLIC_KEY, myAddress, TEST_PRIVATE_KEY );
        
        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put( remoteAddressVoice, null );
        outBoundSMSCallXMLTest( addressMap, adapterConfig, simpleQuestion, null );
        assertXMLGeneratedFromOutBoundCall(addressMap, adapterConfig, simpleQuestion, myAddress);
    }

    /**
     * test if a "hi" TextMessage is generated and processed properly by SMS servlet
     *  as a new session
     * @throws Exception
     */
    @Test
    public void ReceiveAppointmentNewSessionMessageTest() throws Exception
    {
        String initialAgentURL = TestServlet.TEXT_SERVLET_PATH + "?appointment=start";
        //create mail adapter
        AdapterConfig adapterConfig = createAdapterConfig( "SMS", TEST_PUBLIC_KEY, localAddressBroadsoft, initialAgentURL );
        //create session
        getOrCreateSession(adapterConfig, remoteAddressVoice);
        TextMessage textMessage = smsAppointmentInteraction("hi");
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(textMessage.getAddress(), textMessage.getRecipientName());
        String expectedQuestion = TestServlet.getResponseQuestionWithOptionsInString( TestServlet.getJsonAppointmentQuestion() );
        assertXMLGeneratedFromOutBoundCall(addressNameMap, adapterConfig, expectedQuestion, textMessage.getLocalAddress());
    }

    /**
     * test if a "Yup" TextMessage is generated and processed properly by SMS servlet
     *  as a new session
     * @throws Exception
     */
    @Test
    public void ReceiveAppointmentExistingSessionYesMessageTest() throws Exception
    {
        //initiate session with a new message
        ReceiveAppointmentNewSessionMessageTest();

        //respond with a "yes" message
        TextMessage textMessage = smsAppointmentInteraction("Yup");
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(textMessage.getAddress(), textMessage.getRecipientName());
        String expectedQuestion = TestServlet.getResponseQuestionWithOptionsInString( TestServlet.getJsonAppointmentYesQuestion() );

        //fetch already created adapter
        AdapterConfig adapterConfig = AdapterConfig.findAdapters("SMS", localAddressBroadsoft, null).iterator().next();
        assertXMLGeneratedFromOutBoundCall(addressNameMap, adapterConfig, expectedQuestion, textMessage.getLocalAddress());
    }

    /**
     * test if a "Nope" TextMessage is generated and processed properly by SMS servlet
     *  as a new session
     * @throws Exception
     */
    @Test
    public void ReceiveAppointmentExistingSessionNoMessageTest() throws Exception
    {
        //initiate session with a new message
        ReceiveAppointmentNewSessionMessageTest();

        //respond with a "yes" message
        TextMessage textMessage = smsAppointmentInteraction("Nope");
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(textMessage.getAddress(), textMessage.getRecipientName());
        String expectedQuestion = TestServlet.getResponseQuestionWithOptionsInString( TestServlet.getJsonAppointmentNoQuestion() );

        //fetch already created adapter
        AdapterConfig adapterConfig = AdapterConfig.findAdapters("SMS", localAddressBroadsoft, null).iterator().next();
        assertXMLGeneratedFromOutBoundCall(addressNameMap, adapterConfig, expectedQuestion, textMessage.getLocalAddress());
    }

    /**
     * test if an open question is asked by the SMS servlet when a "Yup" is answerd
     *  as a new session
     * @throws Exception
     */
    @Test
    public void ReceiveAppointmentExistingSessionFreeMessageTest() throws Exception
    {
        //initiate session with a new message
        ReceiveAppointmentExistingSessionYesMessageTest();

        //respond with a "yes" message
        TextMessage textMessage = smsAppointmentInteraction("30");
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(textMessage.getAddress(), textMessage.getRecipientName());
        String expectedQuestion = TestServlet.getResponseQuestionWithOptionsInString( TestServlet.getJsonAppointmentFreeQuestion() );

        //fetch already created adapter
        AdapterConfig adapterConfig = AdapterConfig.findAdapters("SMS", localAddressBroadsoft, null).iterator().next();
        assertXMLGeneratedFromOutBoundCall(addressNameMap, adapterConfig, expectedQuestion, textMessage.getLocalAddress());
    }

    /**
     * @return
     * @throws Exception
     */
    private TextMessage smsAppointmentInteraction(String message) throws Exception
    {
        HashMap<String, String> data = new HashMap<String, String>();
        data.put("receiver", localAddressBroadsoft);
        data.put("sender", remoteAddressVoice);
        data.put("message", message);
        //fetch and invoke the receieveMessage method
        MBSmsServlet smsServlet = new MBSmsServlet();
        Method fetchMethodByReflection = fetchMethodByReflection( "receiveMessage", MBSmsServlet.class, HashMap.class );
        TextMessage textMessage = (TextMessage) invokeMethodByReflection( fetchMethodByReflection, smsServlet, data);

        //fetch the processMessage function
        Method processMessage = fetchMethodByReflection( "processMessage", TextServlet.class,  TextMessage.class);

        int count = (Integer) invokeMethodByReflection( processMessage, smsServlet, textMessage );
        assertTrue( count == 1 );
        return textMessage;
    }

    private void outBoundSMSCallXMLTest(Map<String, String> addressNameMap, AdapterConfig adapterConfig,
                                            String simpleQuestion, String senderName)
    throws Exception
    {
        DialogAgent dialogAgent = new DialogAgent();
        if(addressNameMap.size() > 1)
        {
            dialogAgent.outboundCallWithMap( addressNameMap, senderName, TestServlet.TEXT_SERVLET_PATH + "?simpleComment="+ simpleQuestion, 
                                         null, adapterConfig.getConfigId(), TEST_PUBLIC_KEY, "" );
        }
        else
        {
            dialogAgent.outboundCall( addressNameMap.keySet().iterator().next(), senderName, TestServlet.TEXT_SERVLET_PATH + "?simpleComment="+ simpleQuestion, 
                                      null, adapterConfig.getConfigId(), TEST_PUBLIC_KEY, "" );
        }
    }

    private void assertXMLGeneratedFromOutBoundCall(Map<String, String> addressNameMap, AdapterConfig adapterConfig, String simpleQuestion, String senderName) throws Exception {
        //fetch the xml generated
        Document builder = getXMLDocumentBuilder( logObject.get().toString() );
        NodeList messageNodeList = builder.getElementsByTagName( "MESSAGES" );
        NodeList customerNodeList = builder.getElementsByTagName( "CUSTOMER" );
        NodeList userNodeList = builder.getElementsByTagName( "USER" );
        NodeList childMessageNodeList = builder.getElementsByTagName( "MSG" );
        assertTrue( messageNodeList.getLength() == 1 );
        assertTrue( customerNodeList.getLength() == 1 );
        assertTrue( userNodeList.getLength() == 1 );
        assertEquals(addressNameMap.size(), childMessageNodeList.getLength());
        //fetch customerInfo from adapter
        String[] customerInfo = adapterConfig.getAccessToken().split("\\|");
        assertEquals(customerInfo[0], customerNodeList.item(0).getAttributes().getNamedItem("ID").getNodeValue());

        assertEquals(customerInfo[1], userNodeList.item(0).getAttributes().getNamedItem("LOGIN").getNodeValue());

        int addressCount = 0;
        for(String address : addressNameMap.keySet())
        {
            Node msgNode = childMessageNodeList.item(addressCount);
            NodeList childNodes = msgNode.getChildNodes();
            for (int childNodeCount = 0; childNodeCount < childNodes.getLength(); childNodeCount++)
            {
                Node childNode = childNodes.item(childNodeCount);
                if(childNode.getNodeName().equals("CONCATENATIONTYPE"))
                {
                    assertEquals("TEXT", childNode.getFirstChild().getNodeValue());
                }
                else if(childNode.getNodeName().equals("FROM"))
                {
                    assertEquals(senderName, childNode.getFirstChild().getNodeValue());
                }
                else if(childNode.getNodeName().equals("BODY"))
                {
                    assertEquals(simpleQuestion, childNode.getFirstChild().getNodeValue());
                }
                else if(childNode.getNodeName().equals("TO"))
                {
                    assertEquals(address.replaceFirst("\\+31", "0"), childNode.getFirstChild().getNodeValue());
                }
            }
            addressCount++;
        }
    }
}
