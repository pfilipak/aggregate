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

package org.opendatakit.aggregate.externalservice;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.opendatakit.aggregate.ContextFactory;
import org.opendatakit.aggregate.constants.BeanDefs;
import org.opendatakit.aggregate.constants.ServletConsts;
import org.opendatakit.aggregate.constants.common.ExternalServicePublicationOption;
import org.opendatakit.aggregate.constants.common.ExternalServiceType;
import org.opendatakit.aggregate.constants.common.OperationalStatus;
import org.opendatakit.aggregate.constants.externalservice.ExternalServiceConsts;
import org.opendatakit.aggregate.constants.externalservice.SpreadsheetConsts;
import org.opendatakit.aggregate.datamodel.FormElementKey;
import org.opendatakit.aggregate.datamodel.FormElementModel;
import org.opendatakit.aggregate.exception.ODKExternalServiceCredentialsException;
import org.opendatakit.aggregate.exception.ODKExternalServiceException;
import org.opendatakit.aggregate.exception.ODKFormNotFoundException;
import org.opendatakit.aggregate.form.IForm;
import org.opendatakit.aggregate.form.MiscTasks;
import org.opendatakit.aggregate.form.MiscTasks.TaskType;
import org.opendatakit.aggregate.format.Row;
import org.opendatakit.aggregate.format.element.LinkElementFormatter;
import org.opendatakit.aggregate.format.header.GoogleSpreadsheetHeaderFormatter;
import org.opendatakit.aggregate.servlet.FormMultipleValueServlet;
import org.opendatakit.aggregate.submission.Submission;
import org.opendatakit.aggregate.submission.SubmissionSet;
import org.opendatakit.aggregate.submission.SubmissionValue;
import org.opendatakit.aggregate.submission.type.RepeatSubmissionType;
import org.opendatakit.aggregate.task.WorksheetCreator;
import org.opendatakit.common.persistence.CommonFieldsBase;
import org.opendatakit.common.persistence.Datastore;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.persistence.exception.ODKEntityNotFoundException;
import org.opendatakit.common.persistence.exception.ODKEntityPersistException;
import org.opendatakit.common.persistence.exception.ODKOverQuotaException;
import org.opendatakit.common.security.User;
import org.opendatakit.common.web.CallingContext;
import org.opendatakit.common.web.constants.BasicConsts;

import com.google.gdata.client.authn.oauth.OAuthException;
import com.google.gdata.client.authn.oauth.OAuthHmacSha1Signer;
import com.google.gdata.client.docs.DocsService;
import com.google.gdata.client.spreadsheet.CellQuery;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.Link;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.batch.BatchOperationType;
import com.google.gdata.data.batch.BatchStatus;
import com.google.gdata.data.batch.BatchUtils;
import com.google.gdata.data.docs.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.CustomElementCollection;
import com.google.gdata.data.spreadsheet.ListEntry;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ServiceException;

/**
 * 
 * @author wbrunette@gmail.com
 * @author mitchellsundt@gmail.com
 * 
 */
public class GoogleSpreadsheet extends OAuthExternalService implements ExternalService {
  private static final String UTF_8_ENCODING = "UTF-8";

  private static final Log logger = LogFactory.getLog(GoogleSpreadsheet.class.getName());

  /**
   * Datastore entity specific to this type of external service
   */
  private final GoogleSpreadsheetParameterTable objectEntity;

  /**
   * Datastore entity specific to this type of external service for the repeats
   */
  private final List<GoogleSpreadsheetRepeatParameterTable> repeatElementEntities = new ArrayList<GoogleSpreadsheetRepeatParameterTable>();

  private final SpreadsheetService spreadsheetService;

  /**
   * Common base constructor that initializes final values.
   * 
   * @param form
   * @param fpObject
   * @param cc
   * @throws ODKExternalServiceException 
   */
  private GoogleSpreadsheet(IForm form, GoogleSpreadsheetParameterTable gsObject, 
      FormServiceCursor formServiceCursor, CallingContext cc) throws ODKExternalServiceException {
    super(form, formServiceCursor, 
        new LinkElementFormatter(cc.getServerURL(), FormMultipleValueServlet.ADDR, true, true, true, true),
        new GoogleSpreadsheetHeaderFormatter(true, true, true),
        cc);
    spreadsheetService = new SpreadsheetService(ServletConsts.APPLICATION_NAME);
    objectEntity = gsObject;
    try {
      // TODO: REMOVE after bug is fixed
      // http://code.google.com/p/gdata-java-client/issues/detail?id=103
      spreadsheetService.setProtocolVersion(SpreadsheetService.Versions.V1);
      spreadsheetService.setConnectTimeout(SpreadsheetConsts.SERVER_TIMEOUT);
      try {
        spreadsheetService.setOAuthCredentials(getOAuthParams(), new OAuthHmacSha1Signer());
      } catch (OAuthException e) {
        logOAuthException(logger, e);
      }
    } catch (ODKExternalServiceCredentialsException e) {
      if ( fsc.getOperationalStatus().equals(OperationalStatus.ACTIVE) ) {
        fsc.setOperationalStatus(OperationalStatus.BAD_CREDENTIALS);
        try {
          persist(cc);
        } catch (Exception e1) {
          logger.error("Unable to persist bad credentials status" + e1.toString());
          throw new ODKExternalServiceException("unable to persist bad credentials status", e1);
        }
      }
      throw e;
    }
  }

  private GoogleSpreadsheet(IForm form, GoogleSpreadsheetParameterTable entity, 
      ExternalServicePublicationOption externalServiceOption, CallingContext cc) throws ODKDatastoreException, ODKOverQuotaException, ODKExternalServiceException {
    this(form, entity, createFormServiceCursor(form, entity, externalServiceOption, ExternalServiceType.GOOGLE_SPREADSHEET, cc), cc);
  }
  
  public GoogleSpreadsheet(FormServiceCursor fsc, IForm form, CallingContext cc)
      throws ODKEntityNotFoundException, ODKDatastoreException, ODKOverQuotaException, ODKExternalServiceException, ODKFormNotFoundException {
    this(form, retrieveEntity(GoogleSpreadsheetParameterTable.assertRelation(cc), fsc, cc), fsc, cc);
   
    repeatElementEntities.addAll(GoogleSpreadsheetRepeatParameterTable.getRepeatGroupAssociations(
        objectEntity.getUri(), cc));
    
  }

  public GoogleSpreadsheet(IForm form, String name,
      ExternalServicePublicationOption externalServiceOption, CallingContext cc)
      throws ODKDatastoreException, ODKOverQuotaException, ODKExternalServiceException, ODKEntityPersistException {
    this(form, newEntity(GoogleSpreadsheetParameterTable.assertRelation(cc), cc), externalServiceOption, cc);
    
    objectEntity.setSpreadsheetName(name);
    persist(cc);
  }

  public void authenticateAndCreate(OAuthToken authToken, CallingContext cc)
      throws ODKExternalServiceException, ODKDatastoreException {

    objectEntity.setAuthToken(authToken.getToken());
    objectEntity.setAuthTokenSecret(authToken.getTokenSecret());

    // setup service
    DocsService service = new DocsService(ServletConsts.APPLICATION_NAME);
    service.setConnectTimeout(SpreadsheetConsts.SERVER_TIMEOUT);
    try {
      service.setOAuthCredentials(getOAuthParams(), new OAuthHmacSha1Signer());
    } catch (OAuthException e) {
      logOAuthException(logger, e);
    }

    boolean newlyCreated = false;
    if ( fsc.getOperationalStatus() != OperationalStatus.BAD_CREDENTIALS ) {
      newlyCreated = true;

      // create spreadsheet
      com.google.gdata.data.docs.SpreadsheetEntry createdEntry = new SpreadsheetEntry();
      createdEntry.setTitle(new PlainTextConstruct(getSpreadsheetName()));
  
      com.google.gdata.data.docs.SpreadsheetEntry updatedEntry;
      try {
        updatedEntry = service.insert(new URL(SpreadsheetConsts.DOC_FEED), createdEntry);
      } catch (IOException e) {
        // try one more time
        try {
          updatedEntry = service.insert(new URL(SpreadsheetConsts.DOC_FEED), createdEntry);
        } catch (AuthenticationException e1) {
          e1.printStackTrace();
          throw new ODKExternalServiceCredentialsException(e1);
        } catch (Exception e1) {
      	  e1.printStackTrace();
          throw new ODKExternalServiceException(e1);
        }
      } catch (AuthenticationException e) {
        e.printStackTrace();
        throw new ODKExternalServiceCredentialsException(e);
      } catch (Exception e) {
        e.printStackTrace();
        throw new ODKExternalServiceException(e);
      }
  
      // get key
      String spreadKey = updatedEntry.getDocId();
  
      objectEntity.setSpreadsheetKey(spreadKey);
    }
    fsc.setOperationalStatus(OperationalStatus.ACTIVE);
    updateReadyValue();
    persist(cc);

    if ( newlyCreated ) {
      try {
        // create worksheet
        WorksheetCreator ws = (WorksheetCreator) cc.getBean(BeanDefs.WORKSHEET_BEAN);
  
        Map<String, String> parameters = new HashMap<String, String>();
  
        parameters.put(ExternalServiceConsts.EXT_SERV_ADDRESS, getSpreadsheetName());
        parameters.put(ServletConsts.EXTERNAL_SERVICE_TYPE, fsc.getExternalServicePublicationOption().name());
  
        MiscTasks m = new MiscTasks(TaskType.WORKSHEET_CREATE, form, parameters, cc);
        m.persist(cc);
  
        CallingContext ccDaemon = ContextFactory.duplicateContext(cc);
        ccDaemon.setAsDaemon(true);
        ws.createWorksheetTask(form, m, 1L, ccDaemon);
      } catch (ODKFormNotFoundException e) {
        e.printStackTrace();
      }
    }

  }
  
  public Boolean getReady() {
    return objectEntity.getReady();
  }

  public void updateReadyValue() {
    boolean ready = (objectEntity.getSpreadsheetName()!= null) 
        && (objectEntity.getSpreadsheetKey() != null)
        && (objectEntity.getAuthToken() != null) 
        && (objectEntity.getAuthTokenSecret() != null);
    objectEntity.setReady(ready);
  }

  public String getSpreadsheetName() {
    return objectEntity.getSpreadsheetName();
  }

  public void setAuthToken(OAuthToken authToken) {
    objectEntity.setAuthToken(authToken.getToken());
    objectEntity.setAuthTokenSecret(authToken.getTokenSecret());
  }

  protected OAuthToken getAuthToken() {
    return new OAuthToken(objectEntity.getAuthToken(), objectEntity.getAuthTokenSecret());
  }

  public void generateWorksheets(CallingContext cc) throws ODKDatastoreException, IOException,
      ServiceException {

    // retrieve pre-existing worksheets
    URL url = new URL(SpreadsheetConsts.SPREADSHEETS_FEED
        + URLEncoder.encode(objectEntity.getSpreadsheetKey(), UTF_8_ENCODING));
    com.google.gdata.data.spreadsheet.SpreadsheetEntry entry = spreadsheetService.getEntry(url,
        com.google.gdata.data.spreadsheet.SpreadsheetEntry.class);
    List<WorksheetEntry> preExistingWorksheets = entry.getWorksheets();

    // create top level worksheet
    List<String> headers = headerFormatter.generateHeaders(form, form.getTopLevelGroupElement(), null);
    WorksheetEntry topLevelWorksheet = executeCreateWorksheet(entry, form.getFormId(), headers);
    objectEntity.setTopLevelWorksheetId(extractWorksheetId(topLevelWorksheet));

    // delete pre-existing worksheets
    for (WorksheetEntry worksheet : preExistingWorksheets) {
      worksheet.getSelf().delete();
    }

    // get relation prototype for creating repeat parameter table entries
    GoogleSpreadsheetRepeatParameterTable repeatPrototype = GoogleSpreadsheetRepeatParameterTable
        .assertRelation(cc);

    // create repeat worksheets
    Datastore ds = cc.getDatastore();
    User user = cc.getCurrentUser();
    for (FormElementModel repeatGroupElement : form.getRepeatGroupsInModel()) {
      // create the worksheet
      headers = headerFormatter.generateHeaders(form, repeatGroupElement, null);
      WorksheetEntry repeatWorksheet = executeCreateWorksheet(entry,
          repeatGroupElement.getElementName(), headers);

      // add the worksheet id to the repeat element table -- NOTE: the added
      // entry is not actually persisted here
      GoogleSpreadsheetRepeatParameterTable t = ds.createEntityUsingRelation(repeatPrototype, user);
      t.setUriGoogleSpreadsheet(objectEntity.getUri());
      t.setFormElementKey(repeatGroupElement.constructFormElementKey(form));
      t.setWorksheetId(extractWorksheetId(repeatWorksheet));
      repeatElementEntities.add(t);
    }

    // persist the changes we have made to the repeat element table (changes
    // from calling executeCreateWorksheet)
    fsc.setIsExternalServicePrepared(true); // we have completed worksheet
    // creation...
    persist(cc);
  }

  private String extractWorksheetId(WorksheetEntry entry) throws IOException, ServiceException {
    String[] urlElements = entry.getSelf().getId().split("/");
    String worksheetId = urlElements[urlElements.length - 1];
    return worksheetId;
  }

  private WorksheetEntry executeCreateWorksheet(
      com.google.gdata.data.spreadsheet.SpreadsheetEntry entry, String title, List<String> headers)
      throws IOException, ServiceException {

    // create the worksheet
    WorksheetEntry uncreatedWorksheet = new WorksheetEntry();
    uncreatedWorksheet.setTitle(new PlainTextConstruct(title));
    uncreatedWorksheet.setRowCount(2);
    uncreatedWorksheet.setColCount(headers.size());
    URL worksheetFeedUrl = entry.getWorksheetFeedUrl();
    logger.info("WorksheetFeedUrl: " + worksheetFeedUrl.toString() );
    WorksheetEntry createdWorksheet = spreadsheetService.insert(worksheetFeedUrl,
        uncreatedWorksheet);
    logger.info("CellFeedUrl: " + createdWorksheet.getCellFeedUrl().toString() );

    // update the cells of the worksheet with the proper headers
    // first query the worksheet for the cells we need to change
    CellQuery query = new CellQuery(createdWorksheet.getCellFeedUrl());
    query.setMinimumRow(1);
    query.setMaximumRow(1);
    query.setMinimumCol(1);
    query.setMaximumCol(headers.size());
    query.setReturnEmpty(true);
    CellFeed existingCellFeed = spreadsheetService.query(query, CellFeed.class);
    // create the new cell feed based on our headers
    CellFeed batchRequest = new CellFeed();
    List<CellEntry> cells = existingCellFeed.getEntries();
    int index = 0;
    for (CellEntry cell : cells) {
      String header = headers.get(index);
      cell.changeInputValueLocal(header);
      BatchUtils.setBatchId(cell, Integer.toString(index));
      BatchUtils.setBatchOperationType(cell, BatchOperationType.UPDATE);
      batchRequest.getEntries().add(cell);
      index++;
    }
    // submit the cell feed update as a batch operation
    Link batchLink = existingCellFeed.getLink(Link.Rel.FEED_BATCH, Link.Type.ATOM);
    URL batchLinkUrl = new URL(batchLink.getHref());
    logger.info("BatchLinkUrl: " + batchLinkUrl.toString());
    CellFeed batchResponse = spreadsheetService.batch(batchLinkUrl, batchRequest);

    // Check the results
    for (CellEntry cellEntry : batchResponse.getEntries()) {
      String batchId = BatchUtils.getBatchId(cellEntry);
      if (!BatchUtils.isSuccess(cellEntry)) {
        BatchStatus status = BatchUtils.getBatchStatus(cellEntry);
        System.out.printf("%s failed (%s) %s", batchId, status.getReason(), status.getContent());
        // TODO: throw exception?
      }
    }

    return createdWorksheet;
  }

  @Override
  protected void insertData(Submission submission, CallingContext cc)
      throws ODKExternalServiceException {
    if (getReady()) {
      try {
        // upload base submission values
        List<String> headers = headerFormatter.generateHeaders(form,
            form.getTopLevelGroupElement(), null);
        WorksheetEntry topLevelWorksheet = getWorksheet(objectEntity.getTopLevelWorksheetId());
        executeInsertData(submission, headers, topLevelWorksheet, cc);

        // upload repeat values
        for (GoogleSpreadsheetRepeatParameterTable tableId : repeatElementEntities) {
          FormElementKey elementKey = tableId.getFormElementKey();
          FormElementModel element = FormElementModel.retrieveFormElementModel(form, elementKey);
          headers = headerFormatter.generateHeaders(form, element, null);

          List<SubmissionValue> values = submission.findElementValue(element);
          for (SubmissionValue value : values) {
            if (value instanceof RepeatSubmissionType) {
              RepeatSubmissionType repeat = (RepeatSubmissionType) value;
              if (repeat.getElement().equals(element)) {
                for (SubmissionSet set : repeat.getSubmissionSets()) {
                  WorksheetEntry repeatWorksheet = getWorksheet(tableId.getWorksheetId());
                  executeInsertData(set, headers, repeatWorksheet, cc);
                }
              }
            } else {
              System.out
                  .println("ERROR: How did a non Repeat Submission Type get in the for loop?");
            }
          }
        }
      } catch ( AuthenticationException e) {
        logger.error("Unable to insert data into spreadsheet " + objectEntity.getSpreadsheetName() + " exception: " + e.getMessage());
        e.printStackTrace();
        fsc.setOperationalStatus(OperationalStatus.BAD_CREDENTIALS);
        try {
          persist(cc);
        } catch (Exception e1) {
          e1.printStackTrace();
          throw new ODKExternalServiceException("Unable to set OperationalStatus to Bad Credentials: " + e.toString(), e1);
        }
        e.printStackTrace();
        throw new ODKExternalServiceCredentialsException(e);
      } catch (Exception e) {
    	  e.printStackTrace();
    	  logger.error("Unable to insert data into spreadsheet " + objectEntity.getSpreadsheetName() + " exception: " + e.getMessage());
        throw new ODKExternalServiceException(e);
      }
    }
  }

  /**
   * Inserts the data in the given submissionSet as a new entry (i.e. a new row)
   * in the given worksheet, including only the data specified by headers.
   * 
   * @param submissionSet
   *          the set of data from a single submission
   * @param headers
   *          a list of headers corresponding to the headers in worksheet. Only
   *          the data in submissionSet corresponding to these headers will be
   *          submitted.
   * @param worksheet
   *          the worksheet representing the worksheet in a Google Spreadsheet.
   * @throws ODKDatastoreException
   *           if there was a problem in the datastore
   * @throws IOException
   *           if there was a problem communicating over the internet with the
   *           Google Spreadsheet
   * @throws ServiceException
   *           if there was a problem with the GData service
   */
  private void executeInsertData(SubmissionSet submissionSet, List<String> headers,
      WorksheetEntry worksheet, CallingContext cc) throws ODKDatastoreException, IOException,
      ServiceException {
    ListEntry newEntry = new ListEntry();
    CustomElementCollection values = newEntry.getCustomElements();

    Row row = submissionSet.getFormattedValuesAsRow(null, formatter, true, cc);
    List<String> formattedValues = row.getFormattedValues();

    String rowString = null;
    String headerString = null;
    for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
      headerString = headers.get(colIndex);
      rowString = formattedValues.get(colIndex);
      values.setValueLocal(headerString, (rowString == null) ? BasicConsts.SPACE : rowString);
    }

    URL listFeedUrl = worksheet.getListFeedUrl();
    logger.info("listFeedUrl: " + listFeedUrl.toString());
    spreadsheetService.insert(listFeedUrl, newEntry);
  }

  public WorksheetEntry getWorksheet(String worksheetId) throws IOException, ServiceException {
    URL url = new URL(SpreadsheetConsts.WORKSHEETS_FEED
        + URLEncoder.encode(objectEntity.getSpreadsheetKey(), UTF_8_ENCODING) + SpreadsheetConsts.FEED_PERMISSIONS
        + URLEncoder.encode(worksheetId, UTF_8_ENCODING));
    WorksheetEntry worksheetEntry = spreadsheetService.getEntry(url, WorksheetEntry.class);
    return worksheetEntry;
  }
    
  /**
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof GoogleSpreadsheet)) {
      return false;
    }
    GoogleSpreadsheet other = (GoogleSpreadsheet) obj;
    return (objectEntity == null ? (other.objectEntity == null)
        : (other.objectEntity != null && objectEntity.equals(other.objectEntity)))
        && (fsc == null ? (other.fsc == null) : (other.fsc != null && fsc.equals(other.fsc)));
  }



  @Override
  public String getDescriptiveTargetString() {
    return getSpreadsheetName();
  }

  protected CommonFieldsBase retrieveObjectEntity() {
    return objectEntity;
  }

  @Override
  protected List<? extends CommonFieldsBase> retrieveRepateElementEntities() {
    return repeatElementEntities;
  }

}