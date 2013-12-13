package com.almende.util;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DB;
import com.sun.jersey.api.client.Client;


public class ParallelInit {
	public static Boolean loadingRequest=true;
	
	public static Client client = null;
	public static boolean clientActive = false;
	public static Thread conThread = new ClientConThread();

	public static DB datastore = null;
	public static boolean datastoreActive = false;
	public static Thread datastoreThread = new DatastoreThread();
	
	public static ObjectMapper om = new ObjectMapper();
	
	public static boolean startThreads(){
		synchronized(conThread){
			if (!clientActive && !conThread.isAlive()) conThread.start();
		}
		synchronized(datastoreThread){
			if (!datastoreActive && !datastoreThread.isAlive()) datastoreThread.start();
		}
		synchronized(loadingRequest){
			if (loadingRequest) {
				loadingRequest=false;
				return true;
			}
		}
		return false;
	}
	public static Client getClient(){
		startThreads();
		while (!clientActive){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		return client;
	}
	public static DB getDatastore(){
		startThreads();
		while (!datastoreActive){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		return datastore;
	}
	public static ObjectMapper getObjectMapper(){
		startThreads();
		om.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
		return om;
	}
}


