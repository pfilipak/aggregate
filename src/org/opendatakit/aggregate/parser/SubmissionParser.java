/*
 * Copyright (C) 2009 Google Inc. 
 * Copyright (C) 2010 University of Washington.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.aggregate.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.binary.Base64;
import org.opendatakit.aggregate.CallingContext;
import org.opendatakit.aggregate.constants.ParserConsts;
import org.opendatakit.aggregate.constants.ServletConsts;
import org.opendatakit.aggregate.datamodel.FormElementModel;
import org.opendatakit.aggregate.exception.ODKConversionException;
import org.opendatakit.aggregate.exception.ODKFormNotFoundException;
import org.opendatakit.aggregate.exception.ODKIncompleteSubmissionData;
import org.opendatakit.aggregate.exception.ODKParseException;
import org.opendatakit.aggregate.exception.ODKIncompleteSubmissionData.Reason;
import org.opendatakit.aggregate.form.Form;
import org.opendatakit.aggregate.submission.Submission;
import org.opendatakit.aggregate.submission.SubmissionField;
import org.opendatakit.aggregate.submission.SubmissionSet;
import org.opendatakit.aggregate.submission.type.BlobSubmissionType;
import org.opendatakit.aggregate.submission.type.RepeatSubmissionType;
import org.opendatakit.common.constants.BasicConsts;
import org.opendatakit.common.constants.HtmlConsts;
import org.opendatakit.common.persistence.EntityKey;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Parsers submission xml and saves to datastore
 * 
 * @author wbrunette@gmail.com
 * @author mitchellsundt@gmail.com
 * 
 */
public class SubmissionParser {

	/**
	 * form Id of submission
	 */
	private String formId;

	private Form form;

	/**
	 * Root of XML submission
	 */
	private Element root;

	/**
	 * Submission object created from xml submission
	 */
	private Submission submission;

	/**
	 * Data items obtained from a multipart submission
	 */
	private MultiPartFormData submissionFormItems;

	private final CallingContext cc;

	private EntityKey topLevelTableKey = null;

	/**
	 * Construct an ODK submission by processing XML submission to extract
	 * values.  The submission is persisted to the database before returning.
	 * 
	 * @param inputStreamXML
	 *            xml submission input stream
	 * @param cc
	 *            the CallingContext of this request
	 * @throws IOException
	 * @throws ODKFormNotFoundException
	 *             thrown if a form is not found with a matching ODK ID
	 * @throws ODKParseException
	 * @throws ODKIncompleteSubmissionData
	 * @throws ODKConversionException
	 * @throws ODKDatastoreException
	 */
	public SubmissionParser(InputStream inputStreamXML, CallingContext cc)
		throws IOException, ODKFormNotFoundException,
			ODKParseException, ODKIncompleteSubmissionData,
			ODKConversionException, ODKDatastoreException {
		this.cc = cc;
		constructorHelper(inputStreamXML);
	}

	/**
	 * Construct an ODK submission by processing XML submission to extract
	 * values.  The submission is persisted to the database before returning.
	 * 
	 * @param submissionFormParser
	 *            multipart data submission that includes XML submission &
	 *            possibly other data
	 * @param cc
	 *            the CallingContext of this request
	 * @throws IOException
	 * @throws ODKFormNotFoundException
	 *             thrown if a form is not found with a matching ODK ID
	 * @throws ODKParseException
	 * @throws ODKIncompleteSubmissionData
	 * @throws ODKConversionException
	 * @throws ODKDatastoreException
	 */
	public SubmissionParser(MultiPartFormData submissionFormParser,
			CallingContext cc) throws IOException,
			ODKFormNotFoundException, ODKParseException,
			ODKIncompleteSubmissionData, ODKConversionException,
			ODKDatastoreException {
		this.cc = cc;
		if (submissionFormParser == null) {
			// TODO: review best error handling strategy
			throw new IOException("DID NOT GET A MULTIPARTFORMPARSER");
		}
		submissionFormItems = submissionFormParser;
		MultiPartFormItem submission = submissionFormItems
				.getFormDataByFieldName(ServletConsts.XML_SUBMISSION_FILE);
		if (submission == null) {
			// TODO: review best error handling strategy
			throw new IOException("DID NOT GET A SUBMISSION");
		}

		InputStream inputStreamXML = new ByteArrayInputStream(submission.getStream().toByteArray());
		try {
			constructorHelper(inputStreamXML);
		} finally {
			inputStreamXML.close();
		}
	}

	/**
	 * Helper Constructor an ODK submission by processing XML submission to
	 * extract values
	 * 
	 * @param inputStreamXML
	 *            xml submission input stream
	 * 
	 * @throws IOException
	 * @throws ODKFormNotFoundException
	 *             thrown if a form is not found with a matching ODK ID
	 * @throws ODKParseException
	 * @throws ODKIncompleteSubmissionData
	 * @throws ODKConversionException
	 * @throws ODKDatastoreException
	 */
	private void constructorHelper(InputStream inputStreamXML)
			throws IOException, ODKFormNotFoundException, ODKParseException,
			ODKIncompleteSubmissionData, ODKConversionException,
			ODKDatastoreException {
		try {
			DocumentBuilder builder = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder();
			Document doc = builder.parse(inputStreamXML);
			root = doc.getDocumentElement();
			printNode(root);

			// check for odk id
			formId = root.getAttribute(ParserConsts.FORM_ID_ATTRIBUTE_NAME);

			// if odk id is not present use namespace
			if (formId.equalsIgnoreCase(BasicConsts.EMPTY_STRING)) {
				String schema = root
						.getAttribute(ParserConsts.NAMESPACE_ATTRIBUTE);

				// TODO: move this into FormDefinition?
				if (schema == null) {
					throw new ODKIncompleteSubmissionData(Reason.ID_MISSING);
				}
				
				formId = schema;
			}

		} catch (ParserConfigurationException e) {
			throw new IOException(e);
		} catch (SAXException e) {
			e.printStackTrace();
			throw new IOException(e);
		}

		// need to escape all slashes... for xpath processing...
		formId = formId.replaceAll(ParserConsts.FORWARD_SLASH, ParserConsts.FORWARD_SLASH_SUBSTITUTION);
		
		String fullyQualifiedId = Form.extractWellFormedFormId(formId);

		form = Form.retrieveForm(fullyQualifiedId, cc);
		
		String modelVersionString = root.getAttribute(ParserConsts.MODEL_VERSION_ATTRIBUTE_NAME);
		String uiVersionString = root.getAttribute(ParserConsts.UI_VERSION_ATTRIBUTE_NAME);
		Long modelVersion = null;
		Long uiVersion = null;
		if ( modelVersionString != null && modelVersionString.length() > 0 ) {
			modelVersion = Long.valueOf(modelVersionString);
		}
		if ( uiVersionString != null && uiVersionString.length() > 0 ) {
			uiVersion = Long.valueOf(uiVersionString);
		}
		
		submission = new Submission(modelVersion, uiVersion, form.getFormDefinition(), cc);

		topLevelTableKey = submission.getKey();

		FormElementModel formRoot = form.getTopLevelGroupElement();
		boolean uploadAllBinaries = processSubmissionElement(formRoot, root, submission);

		// TODO: figure out if we actually have all the binary content uploaded...
		submission.setIsComplete(true);
		// save the elements inserted into the top-level submission
		try {
			submission.persist(cc);
		} catch (Exception e) {
			List<EntityKey> keys = new ArrayList<EntityKey>();
			submission.recursivelyAddEntityKeys(keys);
			keys.add(submission.getKey());
			try {
				cc.getDatastore().deleteEntities(keys, cc.getCurrentUser());
			} catch ( Exception ex) {
				// ignore... we are rolling back...
			}
			throw new ODKDatastoreException("Unable to persist data", e);
		}
	}

	/**
	 * Get submission object from parse
	 * 
	 * @return submission
	 */
	public Submission getSubmission() {
		return submission;
	}

	/**
	 * 
	 * Helper function to process submission by taking the form element and
	 * extracting the corresponding value from the XML submission. Recursively
	 * applies itself to children of the form element.
	 * 
	 * @param node
	 *            form data model of the group or repeat group being parsed.
	 * @param currentSubmissionElement
	 *            xml document element that marks the start of this submission set.
	 * @param submissionSet
	 *            the submission set to add the submission values to.
	 * @throws ODKParseException
	 * @throws ODKIncompleteSubmissionData
	 * @throws ODKConversionException
	 * @throws ODKDatastoreException
	 */
	private boolean processSubmissionElement(FormElementModel node,
			Element currentSubmissionElement, SubmissionSet submissionSet)
			throws ODKParseException, ODKIncompleteSubmissionData,
			ODKConversionException, ODKDatastoreException {

		if (node == null || currentSubmissionElement == null) {
			return true;
		}

		// the element name of the fdm is the tag name...
		String submissionTag = node.getElementName();
		if (submissionTag == null) {
			return true;
		}

		// verify that the xml matches the node we are processing...
		if (!currentSubmissionElement.getNodeName().equals(submissionTag)) {
			throw new ODKParseException(
					"Xml document element tag: " + currentSubmissionElement.getNodeName() +
					" does not match the xform data model tag name: " + submissionTag);
		}
		
		// get the structure under the fdm tag name...
		List<Element> elements = getElements(currentSubmissionElement);
		if (elements.size() == 0) {
			return true; // the group is not relevant...
		}
		// and for each of these, they should be fields under the given fdm
		// and values within the submissionSet
		boolean complete = true;
		for (Element e : elements) {
			FormElementModel m = node.findElementByName(e.getNodeName());
			if (m == null) {
				continue;
				//throw new ODKParseException();
			}
			switch (m.getElementType()) {
			case GROUP:
				// need to recurse on these elements keeping the same
				// submissionSet...
				complete = complete && 
					processSubmissionElement(m, e, submissionSet);
				break;
			case REPEAT:
				// get the field that will hold the repeats...
				RepeatSubmissionType repeats = 
					(RepeatSubmissionType) submissionSet.getElementValue(m);
				// Create a submission set for a new instance...
				long l = repeats.getNumberRepeats()+1L;
				SubmissionSet repeatableSubmissionSet = new SubmissionSet(
						submissionSet, l, m, form.getFormDefinition(),
						topLevelTableKey, cc);
				// populate the instance's submission set with values from e...
				complete = complete && 
					processSubmissionElement(m, e, repeatableSubmissionSet);
				// add the instance to the repeat group...
				repeats.addSubmissionSet(repeatableSubmissionSet);
				break;
			case STRING:
			case JRDATETIME:
			case JRDATE:
			case JRTIME:
			case INTEGER:
			case DECIMAL:
			case BOOLEAN:
			case SELECT1: // identifies SelectChoice table
			case SELECTN: // identifies SelectChoice table
				String value = getSubmissionValue(e);
				((SubmissionField<?>) submissionSet.getElementValue(m))
						.setValueFromString(value);
				break;
			case GEOPOINT:
				value = getSubmissionValue(e);
				((SubmissionField<?>) submissionSet.getElementValue(m))
						.setValueFromString(value);
				break;
			case BINARY: // identifies BinaryContent table
				value = getSubmissionValue(e);
				SubmissionField<?> submissionElement = ((SubmissionField<?>) submissionSet
						.getElementValue(m));
				complete = complete && 
					processBinarySubmission(m, submissionElement, value);
				break;
			}
		}
		return complete;
	}

	private boolean processBinarySubmission(FormElementModel m,
			SubmissionField<?> submissionElement, String value)
			throws ODKDatastoreException {
		
		// check to see if we received a multipart submission
		if (submissionFormItems == null) {
			// TODO: problem, only accept a base64 encoded in a direct XML post
			byte[] receivedBytes = Base64.decodeBase64(value.getBytes());
			// TODO: problem since we don't know how to tell what type of
			// binary without content type, defaulting to JPG
			submissionElement.setValueFromByteArray(receivedBytes,
					HtmlConsts.RESP_TYPE_IMAGE_JPEG, Long
							.valueOf(receivedBytes.length), null);
		} else {
			// attempt to find binary data in multi-part form submission
			// first searching by file name, then field name
			MultiPartFormItem binaryData = submissionFormItems
					.getFormDataByFileName(value);
			if (binaryData == null) {
				binaryData = submissionFormItems.getFormDataByFieldName(value);
			}
			// after completing the search now check if found anything and
			// value, otherwise output error
			if (binaryData != null) {
				// determine the filename, if any...
				String fileName = binaryData.getFilename();
				if ( fileName == null || fileName.length() == 0 ) {
					fileName = null;
				}
				byte[] byteArray = binaryData.getStream().toByteArray();
				submissionElement.setValueFromByteArray(byteArray, binaryData
						.getContentType(), binaryData.getContentLength(), fileName);
			} else {
				return (((BlobSubmissionType) submissionElement).getAttachmentCount() >= 1);
			}
		}
		return true;
	}

	private List<Element> getElements(Element rootNode) {
		List<Element> elements = new ArrayList<Element>();

		NodeList nodeList = rootNode.getChildNodes();

		for (int i = 0; i < nodeList.getLength(); ++i) {
			Node node = nodeList.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				elements.add((Element) node);
			}
		}
		return elements;
	}

	/**
	 * Extracts value from the XML submission element by getting value from the
	 * text node
	 * 
	 * @param element
	 *            element that has text child node that will contain the value
	 * 
	 * @return value contained in the XML submission
	 */
	private String getSubmissionValue(Element element) {
		// could not find element, return null
		if (element == null) {
			return null;
		}

		NodeList childNodeList = element.getChildNodes();
		// get element value
		for (int i = 0; i < childNodeList.getLength(); i++) {
			Node node = childNodeList.item(i);
			if (node.getNodeType() == Node.TEXT_NODE) {
				String value = node.getNodeValue().trim();
				if (value.length() > 0) {
					return value;
				} // else go to next node
			}
		} // else has no value so continue

		return null;
	}

	/**
	 * Recursive function that prints the nodes from an XML tree
	 * 
	 * @param node
	 *            xml node to be recursively printed
	 */
	private void printNode(Element node) {
		System.out.println(ParserConsts.NODE_FORMATTED + node.getTagName());
		if (node.hasAttributes()) {
			NamedNodeMap attributes = node.getAttributes();
			for (int i = 0; i < attributes.getLength(); i++) {
				Node attr = attributes.item(i);
				System.out.println(ParserConsts.ATTRIBUTE_FORMATTED
						+ attr.getNodeName() + BasicConsts.EQUALS
						+ attr.getNodeValue());
			}
		}
		if (node.hasChildNodes()) {
			NodeList children = node.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);
				if (child.getNodeType() == Node.ELEMENT_NODE) {
					printNode((Element) child);
				} else if (child.getNodeType() == Node.TEXT_NODE) {
					String value = child.getNodeValue().trim();
					if (value.length() > 0) {
						System.out
								.println(ParserConsts.VALUE_FORMATTED + value);
					}
				}
			}
		}

	}

	/**
	 * Get the form Id of submission
	 * 
	 * @return form Id
	 */
	public String getSubmissionFormId() {
		return formId;
	}

	public Form getForm() {
		return form;
	}
}
