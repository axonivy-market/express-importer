package com.axonivy.portal.express.importer;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExpressEmail implements Serializable {

	private static final long serialVersionUID = -4263607320804462685L;
	private String recipients;
	private String responseTo;
	private String subject;
	private String content;
	private List<JsonNode> attachments;

	public String getRecipients() {
		return recipients;
	}

	public void setRecipients(String recipients) {
		this.recipients = recipients;
	}

	public String getResponseTo() {
		return responseTo;
	}

	public void setResponseTo(String responseTo) {
		this.responseTo = responseTo;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public List<JsonNode> getAttachments() {
		return attachments;
	}

	public void setAttachments(List<JsonNode> attachments) {
		this.attachments = attachments;
	}

}