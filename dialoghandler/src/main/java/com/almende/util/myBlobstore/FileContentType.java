package com.almende.util.myBlobstore;

import java.io.Serializable;

import com.almende.util.twigmongo.annotations.Id;

public class FileContentType implements Serializable {
	private static final long	serialVersionUID	= 3786461126487686318L;
	
	@Id
	public String				uuid;
	public String				contentType;
	public String				fileName;
	
	public FileContentType(String uuid, String contentType, String fieldName) {
		this.uuid = uuid;
		this.contentType = contentType;
		this.fileName = fieldName;
	}
	
	public FileContentType() {
		this(null, null, null);
	}
}
