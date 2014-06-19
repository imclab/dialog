package com.almende.dialog.util;

import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.joda.time.DateTime;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.AdapterType;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.model.ddr.DDRType;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * Helper functions for creating DDR records and processing of them
 * @author Shravan
 */
public class DDRUtils
{
    private static final Logger log = Logger.getLogger( DDRUtils.class.getSimpleName() );
    protected static final com.almende.dialog.Logger dialogLog = new com.almende.dialog.Logger();
    //create a single static connection for publishing ddrs
    private static ConnectionFactory rabbitMQConnectionFactory;
    private static final String PUBLISH_QUEUE_NAME = "DDR_PUBLISH_QUEUE";
    
    /** creates a DDR Record for this adapterid and owner. <br>
     * Preconditions: <br>
     * Expects to find a {@link DDRType} of Type {@link DDRTypeCategory#ADAPTER_PURCHASE} <br>
     * @param config AdapterConfig having a non-null {@link AdapterConfig#getConfigId()} and {@link AdapterConfig#getOwner()} 
     * @throws Exception
     */
    public static DDRRecord createDDRRecordOnAdapterPurchase( AdapterConfig config, boolean publishCharges) throws Exception
    {
        DDRType adapterPurchaseDDRType = DDRType.getDDRType( DDRTypeCategory.ADAPTER_PURCHASE );
        if ( adapterPurchaseDDRType != null )
        {
            log.info( String.format( "Applying charges for account: %s and adapter: %s with address: %s",
                config.getOwner(), config.getConfigId(), config.getMyAddress() ) );
            if ( config.getConfigId() != null && config.getOwner() != null )
            {
                DDRRecord ddrRecord = new DDRRecord( adapterPurchaseDDRType.getTypeId(), config.getConfigId(),
                    config.getOwner(), 1 );
                ddrRecord.setStart( TimeUtils.getServerCurrentTimeInMillis() );
                ddrRecord.createOrUpdate();
                //publish charges
                if (publishCharges) {
                    Double ddrCost = calculateDDRCost(ddrRecord);
                    publishDDREntryToQueue(config.getOwner(), ddrCost);
                }
                return ddrRecord;
            }
        }
        log.warning( String.format( "Not charging this adapter purchase with address: %s adapterid: %s anything!!",
            config.getMyAddress(), config.getConfigId() ) );
        return null;
    }
    
    /**
     * creates a ddr record based on the adapterId and accoutnId from config. picks the quantify from the toAddress map 
     * @param config
     * @param unitType
     * @param toAddress
     * @throws Exception
     */
    public static DDRRecord createDDRRecordOnOutgoingCommunication( AdapterConfig config, Map<String, String> toAddress )
    throws Exception
    {
        return createDDRRecordOnCommunication( config, DDRTypeCategory.OUTGOING_COMMUNICATION_COST, toAddress,
            CommunicationStatus.SENT );
    }
    
    
    /**
     * creates a ddr record based on the adapterId and accoutnId from config and the quantity 
     * @param config
     * @param toAddress
     * @param quantity no of units to be charged. Typically for the SMS, it depends on the length of the SMS text and 
     * the address list
     * @throws Exception
     */
    public static DDRRecord createDDRRecordOnOutgoingCommunication( AdapterConfig config, String toAddress, int quantity )
    throws Exception
    {
        HashMap<String, String> toAddressMap = new HashMap<String, String>();
        toAddressMap.put( toAddress, "" );
        return createDDRRecordOnCommunication( config, DDRTypeCategory.OUTGOING_COMMUNICATION_COST, toAddressMap,
            CommunicationStatus.SENT, quantity );
    }
    
    /**
     * creates a ddr record for incoming communication charges
     * @param config
     * @param fromAddress
     * @param quantity
     * @throws Exception
     */
    public static DDRRecord createDDRRecordOnIncomingCommunication( AdapterConfig config, String fromAddress, int quantity )
    throws Exception
    {
        HashMap<String, String> fromAddressMap = new HashMap<String, String>();
        fromAddressMap.put( fromAddress, "" );
        return createDDRRecordOnCommunication( config, DDRTypeCategory.INCOMING_COMMUNICATION_COST, fromAddressMap,
            CommunicationStatus.RECEIEVED, quantity );
    }
    
    
    /**
     * creates a ddr record based on the adapterId and accoutnId from config and the quantity based 
     * on the number of recepients in the toAddress 
     * @param config
     * @param unitType
     * @param toAddress 
     * the address list
     * @throws Exception
     */
    public static DDRRecord createDDRRecordOnOutgoingCommunication( AdapterConfig config, UnitType unitType,
        Map<String, String> toAddress) throws Exception
    {
        return createDDRRecordOnCommunication( config, DDRTypeCategory.OUTGOING_COMMUNICATION_COST, toAddress,
            CommunicationStatus.SENT, toAddress.size() );
    }
    
    /**
     * creates a ddr record based on the adapterId and accoutnId from config and the quantity 
     * @param config
     * @param unitType
     * @param toAddress
     * @param quantity no of units to be charged. Typically for the SMS, it depends on the length of the SMS text and 
     * the address list
     * @throws Exception
     */
    public static DDRRecord createDDRRecordOnOutgoingCommunication( AdapterConfig config, UnitType unitType,
        Map<String, String> toAddress, int quantity ) throws Exception
    {
        return createDDRRecordOnCommunication( config, DDRTypeCategory.OUTGOING_COMMUNICATION_COST, toAddress,
            CommunicationStatus.SENT, quantity );
    }
    
    public static DDRRecord createDDRRecordOnIncomingCommunication( AdapterConfig config, String fromAddress )
    throws Exception
    {
        Map<String, String> fromAddresses = new HashMap<String, String>();
        fromAddresses.put( fromAddress, "" );
        return createDDRRecordOnCommunication( config, DDRTypeCategory.INCOMING_COMMUNICATION_COST, fromAddresses,
            CommunicationStatus.RECEIEVED );
    }
    
    /**
     * updates the DDR record matching the adapter, category and the status
     * @param config
     * @param category
     * @param status
     * @param remoteID
     * @param startTime actual call start timestamp
     * @param answerTime actual call answer timestamp
     * @param releaseTime actual call hangup timestamp
     * @throws Exception
     */
    public static DDRRecord updateDDRRecordOnCallStops( String ddrRecordId, String accountId,
        Long startTime, Long answerTime, Long releaseTime ) throws Exception
    {
        DDRRecord ddrRecord = DDRRecord.getDDRRecord( ddrRecordId, accountId );
        if ( ddrRecord != null )
        {
            ddrRecord.setStart( answerTime ); //initialize the startTime to the answerTime
            Long duration = null;
            if(ddrRecord.getStart()!= null) {
                duration = ( releaseTime != null ? releaseTime : TimeUtils.getServerCurrentTimeInMillis() )
                                                - ddrRecord.getStart();
            }
            else {
                ddrRecord.setStart( startTime); //if no answerTime i.e call not picked up, set to startTime
                duration = 0L;
            }
            ddrRecord.setDuration( duration > 0L ? duration : 0 );
            ddrRecord.setStatus( CommunicationStatus.FINISHED );
            ddrRecord.createOrUpdate();
        }
        return ddrRecord;
    }
    
    /**
     * create a ddr for an adapter being created and charge a monthly fee for
     * example
     * 
     * @param adapterConfig
     * @return
     * @throws Exception
     */
    public static DDRRecord createDDRForSubscription(AdapterConfig adapterConfig, boolean publishCharges) throws Exception {

        DDRType subscriptionDDRType = DDRType.getDDRType(DDRTypeCategory.SUBSCRIPTION_COST);
        DDRRecord newestDDRRecord = null;
        if (subscriptionDDRType != null) {
            List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(subscriptionDDRType.getTypeId(),
                                                             AdapterType.getByValue(adapterConfig.getAdapterType()),
                                                             adapterConfig.getConfigId(), null, null);
            //fetch the ddr based on the details
            List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(adapterConfig.getConfigId(), adapterConfig.getOwner(),
                                                                 null, subscriptionDDRType.getTypeId(), null);
            DateTime serverCurrentTime = TimeUtils.getServerCurrentTime();
            newestDDRRecord = fetchNewestDdrRecord(ddrRecords);
            //flag for creating new ddrRecord
            boolean createNewDDRRecord = false;
            if (newestDDRRecord == null) {
                createNewDDRRecord = true;
            }
            else {
                //pick the first ddrPrice that is valid for now
                for (DDRPrice ddrPrice : ddrPrices) {
                    if (ddrPrice.isValidForTimestamp(serverCurrentTime.getMillis())) {
                        //check if the ddr already belong to the unitType
                        switch (ddrPrice.getUnitType()) {
                            case SECOND: //check if ddr belongs to this second
                                if (serverCurrentTime.minusSeconds(1).getMillis() > newestDDRRecord.getStart()) {
                                    createNewDDRRecord = true;
                                }
                                break;
                            case MINUTE: //check if ddr belongs to minute
                                if (serverCurrentTime.minusMinutes(1).getMillis() > newestDDRRecord.getStart()) {
                                    createNewDDRRecord = true;
                                }
                                break;
                            case HOUR: //check if ddr belongs to this hour
                                if (serverCurrentTime.minusHours(1).getMillis() > newestDDRRecord.getStart()) {
                                    createNewDDRRecord = true;
                                }
                                break;
                            case DAY: //check if ddr belongs to today
                                if (serverCurrentTime.minusDays(1).getMillis() > newestDDRRecord.getStart()) {
                                    createNewDDRRecord = true;
                                }
                                break;
                            case MONTH: //check if ddr belongs to this month
                                if (serverCurrentTime.minusMonths(1).getMillis() > newestDDRRecord.getStart()) {
                                    createNewDDRRecord = true;
                                }
                                break;
                            case YEAR: //check if ddr belongs to this year
                                if (serverCurrentTime.minusYears(1).getMillis() > newestDDRRecord.getStart()) {
                                    createNewDDRRecord = true;
                                }
                                break;
                            default:
                                throw new Exception("DDR cannot be created for Subsciption for UnitType: " +
                                                    ddrPrice.getUnitType().name());
                        }
                        //create a new ddrRecord if not record found for the time
                        if (createNewDDRRecord) {
                            break;
                        }
                    }
                }
            }
            //create new ddrRecord
            if (createNewDDRRecord) {
                newestDDRRecord = new DDRRecord(subscriptionDDRType.getTypeId(), adapterConfig.getConfigId(),
                                                adapterConfig.getOwner(), 1);
                newestDDRRecord.setStart(serverCurrentTime.getMillis());
                newestDDRRecord.createOrUpdate();
                //publish charges if needed
                if(publishCharges) {
                    Double ddrCost = calculateDDRCost(newestDDRRecord);
                    publishDDREntryToQueue(newestDDRRecord.getAccountId(), ddrCost);
                }
            }
            return newestDDRRecord;
        }
        else {
            throw new Exception("No Subscription DDRType found");
        }
    }
    
    public static void publishDDREntryToQueue( String accountId, Double totalCost ) throws Exception
    {
        if ( totalCost != null && totalCost > 0.0 )
        {
            try
            {
                log.info( String.format( "Publishing costs: %s for account: %s", totalCost , accountId ) );
                rabbitMQConnectionFactory = rabbitMQConnectionFactory != null ? rabbitMQConnectionFactory
                                                                             : new ConnectionFactory();
                rabbitMQConnectionFactory.setHost( "localhost" );
                Connection connection = rabbitMQConnectionFactory.newConnection();
                Channel channel = connection.createChannel();
                //create a message
                HashMap<String, String> message = new HashMap<String, String>();
                message.put( "accountId", accountId );
                message.put( "cost", String.valueOf( totalCost ) );
                channel.queueDeclare( PUBLISH_QUEUE_NAME, false, false, false, null );
                channel.basicPublish( "", PUBLISH_QUEUE_NAME, null, ServerUtils.serialize( message ).getBytes() );
                channel.close();
                connection.close();
            }
            catch ( Exception e )
            {
                log.severe( "Error seen: " + e.getLocalizedMessage() );
            }
        }
    }
    
    /**
     * creates a ddr record based on the input parameters and quantity is the size of the addresses map
     * @param config
     * @param category
     * @param unitType
     * @param addresses
     * @param status
     * @throws Exception
     */
    public static DDRRecord createDDRRecordOnCommunication( AdapterConfig config, DDRTypeCategory category,
        Map<String, String> addresses, CommunicationStatus status ) throws Exception
    {
        return createDDRRecordOnCommunication( config, category, addresses, status, addresses.size() );
    }
    
    /**
     * calculates the cost for the given ddrRecord based on the linked DDRPrice.
     * DDRPrice with the most recent {@link DDRPrice#getEndTime() endTime} is
     * chosen if the {@link DDRRecord#getStart() startTime} doesnt fall between
     * {@link DDRPrice#getStartTime() startTime} and
     * {@link DDRPrice#getEndTime() endTime}
     * @param ddrRecord
     * @param includeServiceCosts includes the service cost for this ddr too, if true. The service cost is 
     * ignored if it is lesser than the communiciation cost
     * @return
     * @throws Exception
     */
    public static Double calculateCommunicationDDRCost( DDRRecord ddrRecord, Boolean includeServiceCosts ) throws Exception
    {
        double result = 0.0;
        AdapterConfig config = null;
        if ( ddrRecord != null)
        {
            DDRType ddrType = ddrRecord.getDdrType();
            config = ddrRecord.getAdapter();
            List<DDRPrice> communicationDDRPrices = null;
            if(AdapterType.getByValue( config.getAdapterType()).equals( AdapterType.CALL ))
            {
                String toAddress = null;
                //for a calling ddr record, it must always have one address in the toList
                if( ddrRecord.getToAddress().keySet().size() == 1)
                {
                    toAddress = ddrRecord.getToAddress().keySet().iterator().next();
                    PhoneNumberType numberType = PhoneNumberUtils.getPhoneNumberType( toAddress );
                    String keyword = null;
                    if(!numberType.equals(PhoneNumberType.UNKNOWN))
                    {
                        PhoneNumber phoneNumber = PhoneNumberUtils.getPhoneNumberProto( toAddress, null );
                        keyword = phoneNumber.getCountryCode() + "|" + numberType.name().toUpperCase(); 
                    }
                    communicationDDRPrices = DDRPrice.getDDRPrices(ddrType.getTypeId(),
                                                                   AdapterType.getByValue(config.getAdapterType()),
                                                                   config.getConfigId(), null, keyword);
                }
                else
                {
                    log.severe( "Multiple addresses found in the toAddress field for CALL: "
                        + ddrRecord.getToAddressString() );
                }
            }
            else
            {
                communicationDDRPrices = DDRPrice.getDDRPrices( ddrType.getTypeId(),
                    AdapterType.getByValue( config.getAdapterType() ), config.getConfigId(), null, null );
            }
            if ( communicationDDRPrices != null && !communicationDDRPrices.isEmpty() && config.getConfigId() != null
                && config.getOwner() != null )
            {
                //use the ddrPrice that has the most recent start date and matches the keyword based on the 
                //to address, if it is mobile or landline
                DDRPrice selectedDDRPrice = null;
                boolean isDDRPriceInTimerange = false;
                for ( DDRPrice ddrPrice : communicationDDRPrices )
                {
                    //pick a price whose start and endTimestamp falls in that of the ddrRecords
                    if ( ddrRecord.getStart() != null && ddrPrice.isValidForTimestamp( ddrRecord.getStart() ) )
                    {
                        selectedDDRPrice = ddrPrice;
                        isDDRPriceInTimerange = true;
                        break;
                    }
                    //TODO: should check for other mechanisms that fetch the closet offer to the ddrRecord timestamp
                    selectedDDRPrice = ddrPrice; //else pick the last ddrPrice in the list
                }
                if (!isDDRPriceInTimerange) {
                    log.warning(String.format("No DDRPrice date range match for DDRRecord: %s. In turn fetched: %s of type: %s with price: %s",
                                              ddrRecord.getId(), selectedDDRPrice.getId(),
                                              selectedDDRPrice.getUnitType(), selectedDDRPrice.getPrice()));
                }
                result = calculateDDRCost( ddrRecord, selectedDDRPrice );
            }
        }
        //check if service costs are to be included, only include it if there is any communication costs
        if(includeServiceCosts != null && includeServiceCosts && result > 0.0)
        {
            AdapterType adapterType = config != null ? AdapterType.getByValue( config.getAdapterType() ) : null;
            String adapterId = config != null ? config.getConfigId() : null;
            DDRPrice ddrPriceForDialogService = fetchDDRPrice(DDRTypeCategory.SERVICE_COST, adapterType,
                                                                       adapterId, UnitType.PART, null);
            Double serviceCost = ddrPriceForDialogService != null ? ddrPriceForDialogService.getPrice() : 0.0;
            //add the service cost if the communication cost is lesser than the service cost
            if ( result < serviceCost )
            {
                result += serviceCost;
            }
        }
        return getCeilingAtPrecision(result, 3);
    }
    
    /**
     * calculates the cost associated with this DDRRecord bsaed on the linked DDRPrice. This does not 
     * add the servicecosts. Use the {@link DDRUtils#calculateCommunicationDDRCost(DDRRecord, Boolean)} for it. <br>
     * DDRPrice with the most recent {@link DDRPrice#getEndTime() endTime} is
     * chosen if the {@link DDRRecord#getStart() startTime} doesnt fall between
     * {@link DDRPrice#getStartTime() startTime} and
     * {@link DDRPrice#getEndTime() endTime}
     * @param ddrRecord
     * @return cost incurred for this ddrRecord
     * @throws Exception
     */
    public static Double calculateDDRCost(DDRRecord ddrRecord) throws Exception {

        DDRType ddrType = ddrRecord.getDdrType();
        AdapterConfig adapter = ddrRecord.getAdapter();
        if (ddrType != null) {
            AdapterType adapterType = adapter != null ? AdapterType.getByValue(adapter.getAdapterType()) : null;
            String adapterId = adapter != null ? adapter.getConfigId() : null;
            switch (ddrType.getCategory()) {
                case ADAPTER_PURCHASE:
                case SERVICE_COST: {
                    List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(ddrType.getTypeId(), adapterType, adapterId,
                                                                     UnitType.PART, null);
                    return !ddrPrices.isEmpty() ? ddrPrices.iterator().next().getPrice() : 0.0;
                }
                case INCOMING_COMMUNICATION_COST:
                case OUTGOING_COMMUNICATION_COST:
                    return calculateCommunicationDDRCost(ddrRecord, false);
                case SUBSCRIPTION_COST:
                case OTHER: {
                    List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(ddrType.getTypeId(), adapterType, adapterId, null,
                                                                     null);
                    return !ddrPrices.isEmpty() ? ddrPrices.iterator().next().getPrice() : 0.0;
                }
                default:
                    String errorMessage = String.format("No DDRTypes found for this DDRRecord id: %s and ddrTypeId: %s",
                                                        ddrRecord.getId(), ddrRecord.getDdrTypeId());
                    log.severe(errorMessage);
                    throw new Exception(errorMessage);
            }
        }
        else {
            String errorMessage = String.format("No DDRTypes found for this DDRRecord id: %s and ddrTypeId: %s",
                                                ddrRecord.getId(), ddrRecord.getDdrTypeId());
            log.severe(errorMessage);
            throw new Exception(errorMessage);
        }
    }
    
    /**
     * returns the first DDRPrice based on the category and the unitType.
     * @param category returns null if this is null or a DDRType is not found for this category.
     * @param adapterType
     * @param adapterId
     * @param unitType
     * @param keyword
     * @return returns the first DDRPrice based on the category and the unitType.
     * @throws Exception
     */
    public static DDRPrice fetchDDRPrice( DDRTypeCategory category, AdapterType adapterType, String adapterId,
        UnitType unitType, String keyword ) throws Exception
    {
        DDRType ddrType = DDRType.getDDRType( category );
        if ( ddrType != null )
        {
            List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices( ddrType.getTypeId(), adapterType, adapterId, unitType,
                keyword );
            return ddrPrices != null && !ddrPrices.isEmpty() ? ddrPrices.iterator().next() : null;
        }
        return null;
    }
    
    /**
     * calculates the total cost for this ddrRecord and ddr price 
     * @param ddrRecord
     * @param ddrPrice
     * @return the total cost chargeable
     * @throws Exception
     */
    public static Double calculateDDRCost(DDRRecord ddrRecord, DDRPrice ddrPrice) throws Exception
    {
        DDRType ddrType = DDRType.getDDRType( ddrRecord.getDdrTypeId() );
        Double totalCost = null;
        if(ddrType != null && ddrPrice != null )
        {
            switch ( ddrType.getCategory() )
            {
                case INCOMING_COMMUNICATION_COST:
                case OUTGOING_COMMUNICATION_COST:
                {
                    Double totalTime = null;
                    double duration_double = ddrRecord.getDuration() != null ? ddrRecord.getDuration().doubleValue() : 0.0;
                    switch ( ddrPrice.getUnitType() )
                    {
                        case SECOND:
                            totalTime = duration_double / 1000; //in secs
                            break;
                        case MINUTE:
                            totalTime = duration_double / ( 60 * 1000 ); //in mins
                            break;
                        case HOUR:
                            totalTime = duration_double / ( 60 * 60 * 1000 ); //in hrs
                            break;
                        case DAY:
                            totalTime = duration_double / ( 24 * 60 * 60 * 1000 ); //in days
                            break;
                        case MONTH:
                            int monthOfYear = TimeUtils.getServerCurrentTime().getMonthOfYear();
                            int totalDays = Calendar.getInstance( TimeUtils.getServerTimeZone() ).getActualMaximum(
                                monthOfYear );
                            totalTime = duration_double / ( totalDays * 24 * 60 * 60 * 1000 ); //in months
                            break;
                        case PART:
                            totalTime = 1.0;
                            break;
                        default:
                            throw new Exception( "DDR not implemented for this UnitType: " + ddrPrice.getUnitType() );
                    }
                    double noOfComsumedUnits = Math.ceil( totalTime ) / ( (double) ddrPrice.getUnits() );
                    totalCost = ddrRecord.getQuantity() * Math.ceil( noOfComsumedUnits ) * ddrPrice.getPrice();
                    break;
                }
                case ADAPTER_PURCHASE:
                case SERVICE_COST:
                case SUBSCRIPTION_COST:
                    totalCost = ddrPrice.getPrice();
                    break;
                default:
                    throw new Exception( "DDR not implemented for this category: " + ddrType.getCategory() );
            }
        }
        return getCeilingAtPrecision(totalCost, 3);
    }
    
    /**
     * creates a ddr record based on the input parameters
     * @param config picks the adpaterId, owner and myAddress from this 
     * @param category 
     * @param unitType
     * @param addresses
     * @param status
     * @param quantity
     * @throws Exception
     */
    private static DDRRecord createDDRRecordOnCommunication( AdapterConfig config, DDRTypeCategory category,
        Map<String, String> addresses, CommunicationStatus status, int quantity ) throws Exception
    {
        DDRType communicationCostDDRType = DDRType.getDDRType( category );
        if ( communicationCostDDRType != null )
        {
            log.info( String.format( "Applying charges for account: %s and adapter: %s with address: %s",
                config.getOwner(), config.getConfigId(), config.getMyAddress() ) );
            if ( config.getConfigId() != null && config.getOwner() != null )
            {
                DDRRecord ddrRecord = new DDRRecord( communicationCostDDRType.getTypeId(), config.getConfigId(),
                    config.getOwner(), 1 );
                //default the start to the sessionCreationTime. This is expected to be updated with the actual
                //timestamp for voice communication
                //set the ddrRecord time with session creationTime.
                Session session = Session.getSession(config.getAdapterType(), config.getMyAddress(), addresses.keySet()
                                                .iterator().next());
                if (session != null) {
                    ddrRecord.setStart( session.getCreationTimestamp() != null ? Long.parseLong(session.getCreationTimestamp()) : null);
                }
                switch ( status )
                {
                    case SENT:
                        ddrRecord.setFromAddress( config.getMyAddress() );
                        ddrRecord.setToAddress( addresses );
                        break;
                    case RECEIEVED:
                        ddrRecord.setFromAddress( addresses.keySet().iterator().next() );
                        Map<String, String> toAddresses = new HashMap<String, String>();
                        toAddresses.put( config.getMyAddress(), "" );
                        ddrRecord.setToAddress( toAddresses );
                        break;
                    default:
                        throw new Exception("Unknown CommunicationStatus seen: "+ status.name());
                }
                ddrRecord.setQuantity( quantity );
                ddrRecord.setStatus( status );
                ddrRecord.createOrUpdate();
                return DDRRecord.getDDRRecord( ddrRecord.getId(), ddrRecord.getAccountId() );
            }
        }
        log.warning( String.format( "Not charging this communication from: %s adapterid: %s anything!!",
            config.getMyAddress(), config.getConfigId() ) );
        return null;
    }
    
    /**
     * <b>used only with adapter type: broadsoft. </b> <br>
     * Returns true if a ddr is processed successfully for the given session. else false.
     * @param sessionKey
     * @param pushToQueue pushes the sessionKey to the rabbitMQ queue for postprocessing if a corresponding ddr is not
     * processed successfully
     * @return
     */
    public static boolean stopCostsAtCallHangup( String sessionKey, boolean pushToQueue )
    {
        Session session = Session.getSession(sessionKey);
        AdapterConfig adapterConfig = session.getAdapterConfig();
        boolean result = false;
        //stop costs
        try
        {
            log.info( String.format( "stopping charges for session: %s", ServerUtils.serialize( session ) ) );
                DDRRecord ddrRecord = null;
                //if no ddr is seen for this session. try to fetch it based on the timestamps
                if (session.getDdrRecordId() == null) {
                    ddrRecord = DDRRecord.getDDRRecord(sessionKey);
                }
                if ( session.getStartTimestamp() != null && session.getReleaseTimestamp() != null &&
                                                session.getDirection() != null) {
                ddrRecord = updateDDRRecordOnCallStops(session.getDdrRecordId(),
                                                       adapterConfig.getOwner(),
                                                       Long.parseLong(session.getStartTimestamp()),
                                                       session.getAnswerTimestamp() != null ? Long.parseLong(session
                                                                                       .getAnswerTimestamp()) : null,
                                                       Long.parseLong(session.getReleaseTimestamp()));
                //push session to queue when the call is picked up but no costs are attached or
                //when the ddrRecord is found but no answerTimestamp is seen. (Try to process it again later: when the answer ccxml comes in later on)
                boolean candidateToBePushedToQueue = false; 
                if (ddrRecord == null && session.getAnswerTimestamp() != null) {
                    String errorMessage = String.format("No costs added to communication currently for session: %s, as no ddr record is found",
                                                        session.getKey());
                    log.severe(errorMessage);
                    dialogLog.severe(adapterConfig, errorMessage);
                    candidateToBePushedToQueue = true;
                }
                else if(ddrRecord != null && session.getAnswerTimestamp() == null) {
                    String warningMessage = String.format("No costs added. Looks like a hangup! for session: %s",
                                                        session.getKey());
                    log.warning(warningMessage);
                    candidateToBePushedToQueue = true;
                }
                if (candidateToBePushedToQueue && pushToQueue) { //push the session details to queue
                    session.pushSessionToQueue();
                    return result;
                }
                    
                //publish charges
                Double totalCost = calculateCommunicationDDRCost(ddrRecord, true);
                publishDDREntryToQueue(adapterConfig.getOwner(), totalCost);
                result = true;
            }
            //if answerTimestamp and releastTimestamp is not found, add it to the queue
            else {
                String errorMessage = String.format("No costs added to communication currently for session: %s, as no answerTimestamp or releaseTimestamp is found",
                                                    session.getKey());
                log.severe(errorMessage);
                dialogLog.severe(adapterConfig, errorMessage);
                if (pushToQueue) { //push the session details to queue
                    session.pushSessionToQueue();
                }
            }
        }
        catch ( Exception e )
        {
            String errorMessage = String.format(
                    "Applying charges failed. Direction: %s for adapterId: %s with address: %s remoteId: %s and localId: %s \n Error: %s",
                    session.getDirection(), session.getAdapterID(), adapterConfig.getMyAddress(),
                    session.getRemoteAddress(), session.getLocalAddress(), e.getLocalizedMessage() );
            log.severe( errorMessage );
            dialogLog.severe( session.getAdapterConfig().getConfigId(), errorMessage );
        }
        return result;
    }
    
    private static Double getCeilingAtPrecision(double value, int precision) {

//        return value;
        return Math.round(value * Math.pow(10, precision)) / Math.pow(10, precision);
    }
    
    /**
     * simple method to return the newest DDRRecord from the list
     * @param ddrRecords
     * @return
     */
    private static DDRRecord fetchNewestDdrRecord(Collection<DDRRecord> ddrRecords){
        DDRRecord selecteDdrRecord = null;
        for (DDRRecord ddrRecord : ddrRecords) {
            if (selecteDdrRecord == null ||
                (ddrRecord.getStart() != null && selecteDdrRecord.getStart() < ddrRecord.getStart())) {
                selecteDdrRecord = ddrRecord;
            }
        }
        return selecteDdrRecord;
    }
}