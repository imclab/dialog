package com.almende.dialog;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.After;
import org.junit.Before;
import org.w3c.dom.Document;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.DDRRecordAgent;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.almende.util.ParallelInit;
import com.almende.util.TypeUtil;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.meterware.servletunit.ServletRunner;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

/**
 * Test framework to be inherited by all test classes
 * @author Shravan
 */
public class TestFramework
{
	//TODO!
//    private final LocalServiceTestHelper helper = new LocalServiceTestHelper( new LocalDatastoreServiceTestConfig() );
    protected static final String localAddressMail      = "info@dialog-handler.appspotmail.com";
    protected static final String localAddressChat      = "info@dialog-handler.appspotchat.com";
    protected static final String remoteAddressEmail    = "info@askcs.com";
    protected static final String localAddressBroadsoft 	= "0854881000";
    protected static final String remoteAddressVoice    = "0614765800";
    protected static final String TEST_PUBLIC_KEY    	= "agent1@ask-cs.com";
    protected static final String TEST_PRIVATE_KEY 		= "test_private_key";
    
    public static ThreadLocal<ServletRunner> servletRunner = new ThreadLocal<ServletRunner>();
    
    @Before
    public void setup() {

        TestServlet.TEST_SERVLET_PATH = "http://localhost:8082/dialoghandler/unitTestServlet";
        new ParallelInit(true);
        ParallelInit.getDatastore();
        if (ParallelInit.datastore != null) {
            ParallelInit.datastore.dropDatabase();
        }
        servletRunner.remove();
        if (servletRunner.get() == null) {
            servletRunner.set(setupTestServlet());
        }
        DialogAgent dialogAgent = new DialogAgent();
        dialogAgent.setDefaultProviderSettings(AdapterType.SMS, AdapterProviders.CM);
        dialogAgent.setDefaultProviderSettings(AdapterType.CALL, AdapterProviders.BROADSOFT);
    }
    
    @After
    public void tearDown()
    {
        if(ParallelInit.datastore != null)
        {
            ParallelInit.datastore.dropDatabase();
        }
        servletRunner.remove();
        TestServlet.clearLogObject();
    }
    
    public static String fetchResponse( String httpMethods, String url, String payload )
    {
        String result = "";
        Client client = ParallelInit.getClient();
        WebResource webResource = client.resource( url );
        try
        {
            result = webResource.type( "text/plain" ).get( String.class );
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }
        return result;
    }
    
    public String createSessionKey(AdapterConfig adapterConfig, String responder) {

        return adapterConfig.getAdapterType() + "|" + adapterConfig.getMyAddress() + "|" +
               PhoneNumberUtils.formatNumber(responder, null);
    }
    
    public Session createSession(String sessionKey) {

        return Session.getOrCreateSession(sessionKey);
    }
    
    public AdapterConfig createBroadsoftAdapter() throws Exception {

        return createAdapterConfig(AdapterType.CALL.toString(), AdapterProviders.BROADSOFT, TEST_PUBLIC_KEY,
                                   localAddressBroadsoft, "");
    }
    
    public AdapterConfig createTwilioAdapter() throws Exception {

        return createAdapterConfig(AdapterType.CALL.toString(), AdapterProviders.TWILIO, TEST_PUBLIC_KEY,
                                   localAddressBroadsoft, "");
    }
    
    public static AdapterConfig createAdapterConfig(String adapterType, AdapterProviders adapterProviders,
        String accountId, String myAddress, String initiatAgentURL) throws Exception {

        AdapterConfig adapterConfig = new AdapterConfig();
        adapterConfig.setAdapterType(adapterType.toLowerCase());
        adapterConfig.setAnonymous(false);
        adapterConfig.setPublicKey(accountId);
        adapterConfig.setMyAddress(myAddress);
        adapterConfig.setAccessToken("1111|blabla");
        adapterConfig.setKeyword("TEST");
        adapterConfig.setInitialAgentURL(initiatAgentURL);
        adapterConfig.setOwner(accountId);
        adapterConfig.addAccount(accountId);
        if (adapterProviders != null) {
            adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, adapterProviders);
        }
        String adapterConfigString = adapterConfig.createConfig(ServerUtils.serialize(adapterConfig)).getEntity()
                                        .toString();
        adapterConfig = ServerUtils.deserialize(adapterConfigString, AdapterConfig.class);
        return adapterConfig;
    }
    
    public static AdapterConfig createAdapterConfig(String adapterType, String owner,
        Collection<String> linkedAccounts, String myAddress, String initiatAgentURL) throws Exception {

        AdapterConfig adapterConfig = new AdapterConfig();
        adapterConfig.setAdapterType(adapterType);
        adapterConfig.setMyAddress(myAddress);
        adapterConfig.setOwner(owner);
        adapterConfig.setAccounts(linkedAccounts);
        adapterConfig.setInitialAgentURL(initiatAgentURL);
        String adapterConfigString = adapterConfig.createConfig(ServerUtils.serialize(adapterConfig)).getEntity()
                                        .toString();
        return ServerUtils.deserialize(adapterConfigString, AdapterConfig.class);
    }
    
    public static AdapterConfig createEmailAdapter( String emailAddress, String password, String name,
        String preferredLanguage, String sendingPort, String sendingHost, String protocol, String receivingHost,
        String receivingProtocol, String accountId, String initialAgentURL ) throws Exception
    {
        String emailAdapterId = new AdapterAgent().createEmailAdapter( emailAddress, password, name, preferredLanguage,
            sendingPort, sendingHost, protocol, receivingProtocol, receivingHost, accountId, initialAgentURL, null );
        return AdapterConfig.getAdapterConfig( emailAdapterId );
    }
    
    public static Method fetchMethodByReflection( String methodName, Class<?> class1, Class<?> parameterType )
    throws Exception
    {
        Collection<Class<?>> argumentList = new ArrayList< Class<?>>();
        argumentList.add( parameterType );
        return fetchMethodByReflection( methodName, class1, argumentList );
    }
    
    public static Method fetchMethodByReflection(String methodName, Class<?> class1, Collection<Class<?>> parameterTypes ) 
    throws Exception
    {
        Method declaredMethod = class1.getDeclaredMethod( methodName, parameterTypes.toArray( new Class[parameterTypes.size()] ) );
        declaredMethod.setAccessible( true );
        return declaredMethod;
    }
    
    public static Object invokeMethodByReflection( Method methodToBeFetched, Object targetObject, Object argObjects ) 
    throws Exception
    {
        Collection<Object> argumentValues = new ArrayList<Object>();
        argumentValues.add( argObjects );
        return invokeMethodByReflection( methodToBeFetched, targetObject, argumentValues );
    }
    
    public static Object invokeMethodByReflection( Method methodToBeFetched, Object targetObject, 
                    Collection<Object> argObjects ) throws Exception
    {
        return methodToBeFetched.invoke( targetObject, argObjects.toArray( new Object[argObjects.size()] ));
    }
    
    public static MimeMultipart getTestMimeMultipart(String from, String to, String body, String stanza) throws MessagingException
    {
        MimeMultipart mimeMultipart = new MimeMultipart();
        if(body != null)
        {
            addHeader( mimeMultipart, "body", body );
        }
        if(from != null)
        {
            addHeader( mimeMultipart, "from", from );
        }
        if(to != null)
        {
            addHeader( mimeMultipart, "to", to );
        }
        if(stanza != null)
        {
            addHeader( mimeMultipart, "stanza", stanza );
        }
        return mimeMultipart;
    }
    
    /**
     * Create a DDRPrice. This will also generate all the default,generic ddrtypes 
     * @param category
     * @param price
     * @param name
     * @param unitType
     * @param adapterType
     * @param adapterId
     * @return
     * @throws Exception
     */
    public static DDRPrice createTestDDRPrice(DDRTypeCategory category, double price, String name, UnitType unitType,
        AdapterType adapterType, String adapterId) throws Exception {

        DDRRecordAgent ddrRecordAgent = new DDRRecordAgent();
        ddrRecordAgent.generateDefaultDDRTypes();
        Object ddrPriceObject = ddrRecordAgent
                                        .createDDRPriceWithNewDDRType(name, category.name(),
                                                                      TimeUtils.getServerCurrentTimeInMillis(),
                                                                      TimeUtils.getCurrentServerTimePlusMinutes(100),
                                                                      price, 0, 10, 1, unitType.name(),
                                                                      adapterType != null ? adapterType.name() : null,
                                                                      adapterId, null);
        TypeUtil<DDRPrice> injector = new TypeUtil<DDRPrice>() {};
        return injector.inject(ddrPriceObject);
    }
    
    private static void addHeader(MimeMultipart mimeMultipart, String key, String value) throws MessagingException
    {
        //value appended to the headerValue having from, to, body etc
        String headerValue = "form-data; name=";
        InternetHeaders internetHeaders = new InternetHeaders( new ByteArrayInputStream( "Content-Disposition".getBytes() ));
        internetHeaders.removeHeader( "Content-Disposition" );
        String appendedHeaderValue = headerValue + "\"" + key + "\"" ; 
        internetHeaders.addHeader( "Content-Disposition", appendedHeaderValue );
        mimeMultipart.addBodyPart( new MimeBodyPart( internetHeaders, value.getBytes()) );
    }
    
    private ServletRunner setupTestServlet()
    {
        ServletRunner servletRunner = new ServletRunner();
        servletRunner.registerServlet( "/unitTestServlet/*", TestServlet.class.getName() );
        return servletRunner;
    }
    
    public static Document getXMLDocumentBuilder(String xmlContent) throws Exception
    {
        DocumentBuilderFactory newInstance = DocumentBuilderFactory.newInstance();
        DocumentBuilder newDocumentBuilder = newInstance.newDocumentBuilder();
        Document parse = newDocumentBuilder.parse( new ByteArrayInputStream(xmlContent.getBytes("UTF-8")) );
        return parse;
    }

    protected javax.mail.Message getMessageFromDetails(String remoteAddress, String localAddress, String messageText, 
        String subject) throws Exception
    {
        MimeMessage mimeMessage = new MimeMessage( javax.mail.Session.getDefaultInstance(new Properties(), null) );
        mimeMessage.setFrom( new InternetAddress( localAddress ) );
        mimeMessage.setSubject(subject);
        mimeMessage.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(remoteAddress));
        mimeMessage.setText(messageText);
        return mimeMessage;
    }
}
