package com.almende.dialog.aws;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import org.apache.commons.fileupload.FileItem;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

public class AWSClient{

    AmazonS3 client = null;
    String bucketName = null;
    
    public AWSClient() {
    }
    
    public void init(String bucketName, String accessKey, String accessKeySecret) {
        if(accessKey!= null && accessKeySecret!=null) {
            BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, accessKeySecret);
            this.client = new AmazonS3Client(awsCreds);
            this.bucketName = bucketName;
            
            if(!client.doesBucketExist( bucketName )) {
                client.createBucket( bucketName );
            }
        }
    }
    
    public boolean uploadFileParts(FileItem fileData, String keyName) {
        try {
            S3Object s3Object = new S3Object();
    
            ObjectMetadata omd = new ObjectMetadata();
            omd.setContentType(fileData.getContentType());
            omd.setContentLength(fileData.getSize());
            omd.setHeader("filename", keyName);
    
            ByteArrayInputStream bis = new ByteArrayInputStream(fileData.get());
    
            s3Object.setObjectContent(bis);
            client.putObject(new PutObjectRequest(bucketName, keyName, bis, omd));
            s3Object.close();
            
            return true;
        } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which " +
                        "means your request made it " +
                    "to Amazon S3, but was rejected with an error response" +
                    " for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
            return false;
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which " +
                        "means the client encountered " +
                    "an internal error while trying to " +
                    "communicate with S3, " +
                    "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
            return false;
        } catch (Exception e) {
            System.out.println("Exception uploading file"); 
            return false;
        }
    }
    
    public boolean uploadFile(File file, String keyName) {
        try {
            System.out.println("Uploading a new object to S3 from a file\n");
            client.putObject(new PutObjectRequest(bucketName, keyName, file));
            return true;
         } catch (AmazonServiceException ase) {
            System.out.println("Caught an AmazonServiceException, which " +
                        "means your request made it " +
                    "to Amazon S3, but was rejected with an error response" +
                    " for some reason.");
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
            return false;
        } catch (AmazonClientException ace) {
            System.out.println("Caught an AmazonClientException, which " +
                        "means the client encountered " +
                    "an internal error while trying to " +
                    "communicate with S3, " +
                    "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
            return false;
        }
    }
    
    public InputStream getFile(String keyName) {
        S3Object s3object = client.getObject(new GetObjectRequest(bucketName, keyName));
        System.out.println("Content-Type: "  + s3object.getObjectMetadata().getContentType());
        return s3object.getObjectContent();
    }
}
