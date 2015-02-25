package com.almende.dialog.util;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import com.almende.dialog.Settings;
import com.almende.dialog.agent.DialogAgent;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


public class ServerUtils
{
    private static final Logger log = Logger.getLogger(DialogAgent.class.getName());
    private static ObjectMapper oMapper = ParallelInit.getObjectMapper();
    
    public static <T> T deserialize( String jsonString, Class<T> DeserializeClass )
    throws Exception
    {
        T deserializedEntity = null;
        if ( jsonString != null && !jsonString.isEmpty() )
        {
            deserializedEntity = oMapper.readValue(jsonString, DeserializeClass);
        }
        return deserializedEntity;
    }
    
    public static <T> T deserialize( String jsonString, boolean throwException, Class<T> DeserializeClass )
    throws Exception
    {
        T deserialized = null;
        try
        {
            deserialized = deserialize( jsonString, DeserializeClass );
        }
        catch ( Exception e )
        {
            if ( throwException )
            {
                throw e;
            }
            else
            {
                log.warning( String.format( "Failed to deserialize %s to class: %s", jsonString,
                    DeserializeClass.getSimpleName() ) );
            }
        }
        return deserialized;
    }

    public static <T> T deserialize( String jsonString, TypeReference<T> type ) throws Exception
    {
        return oMapper.readValue( jsonString, type );
    }

    public static <T> T deserialize( String jsonString, boolean throwException, TypeReference<T> type ) throws Exception
    {
        try
        {
            return oMapper.readValue( jsonString, type );
        }
        catch ( Exception e )
        {
            if(throwException)
            {
                throw e;
            }
            else
            {
                log.severe( e.getLocalizedMessage() );
                return null;
            }
        }
    }

    public static String serializeWithoutException( Object objectToBeSerialized )
    {
        try
        {
            return serialize( objectToBeSerialized );
        }
        catch ( Exception e )
        {
            log.severe( e.getLocalizedMessage() );
            return null;
        }
    }
    
    public static String serialize( Object objectToBeSerialized ) throws Exception
    {
        oMapper.setSerializationInclusion( Include.NON_NULL );
        //oMapper.setSerializationInclusion( Include.NON_EMPTY );
        String result = null;
        if(objectToBeSerialized != null )
        {
            result = oMapper.writeValueAsString( objectToBeSerialized );
        }
        return result;
    }
    
    /**
     * fetches the request data in a string format
     * @return
     * @throws IOException
     */
    public static String getRequestData(HttpServletRequest httpServletRequest) throws IOException
    {
        StringBuffer sb = new StringBuffer();
        BufferedReader reader = httpServletRequest.getReader();
        String line;
        while ( (line = reader.readLine()) != null )
        {
            sb.append( line );
        }
        return new String( sb );
    }

    public static boolean isInDevelopmentEnvironment()
    {
        return Settings.environment() == Settings.Development;
    }

    public static boolean isInProductionEnvironment()
    {
        return Settings.environment() == Settings.Production;
    }

    public static boolean isInUnitTestingEnvironment()
    {
        return (Settings.environment() == null || ParallelInit.isTest);
    }
    
    /**
     * returns the url by adding the queryKey=queryValue based on if a query
     * param is already seen in the url
     * 
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String getURLWithQueryParams(String url, String queryKey, String queryValue)
        throws UnsupportedEncodingException {

        try {
            URIBuilder uriBuilder = new URIBuilder(new URI(url));
            URIBuilder returnResult = new URIBuilder(new URI(url)).removeQuery();
            returnResult.addParameter(queryKey, queryValue);
            for (NameValuePair nameValue : uriBuilder.getQueryParams()) {

                if (!nameValue.getName().equals(queryKey)) {
                    returnResult.addParameter(nameValue.getName(), nameValue.getValue());
                }
            }
            return returnResult.toString();
        }
        catch (Exception e) {
            e.printStackTrace();
            return url;
        }
    }
    
    /**
     * associates the same value corresponding to keys listed in keyCollection
     */
    public static <T> Map<T, T> putCollectionAsKey( Collection<T> keyCollection, T value )
    {
        Map<T, T> mapToBePopulated = new HashMap<T, T>();
        for ( T key : keyCollection )
        {
            mapToBePopulated.put( key, value );
        }
        return mapToBePopulated;
    }
    
    /**
     * Simple check to see if the string is empty or null
     * @param stringToCheck
     * @return
     */
    public static boolean isNullOrEmpty(String stringToCheck) {
        if(stringToCheck == null || stringToCheck.isEmpty()) {
            return true;
        }
        return false;
    }
    
    public static String encodeURLParams(String url) {

        try {
            URL remoteURL = new URL(url);
            return new URI(remoteURL.getProtocol(), remoteURL.getUserInfo(), remoteURL.getHost(), remoteURL.getPort(),
                           remoteURL.getPath(), remoteURL.getQuery(), remoteURL.getRef()).toString();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return url;
    }
}
