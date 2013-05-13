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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.opendatakit.aggregate.ContextFactory;
import org.opendatakit.aggregate.constants.BeanDefs;
import org.opendatakit.aggregate.constants.ErrorConsts;
import org.opendatakit.aggregate.constants.HtmlUtil;
import org.opendatakit.aggregate.constants.common.ExternalServicePublicationOption;
import org.opendatakit.aggregate.constants.common.ExternalServiceType;
import org.opendatakit.aggregate.constants.common.OperationalStatus;
import org.opendatakit.aggregate.constants.externalservice.FusionTableConsts;
import org.opendatakit.aggregate.constants.format.FormatConsts;
import org.opendatakit.aggregate.datamodel.FormElementKey;
import org.opendatakit.aggregate.datamodel.FormElementModel;
import org.opendatakit.aggregate.datamodel.FormElementModel.ElementType;
import org.opendatakit.aggregate.exception.ODKExternalServiceCredentialsException;
import org.opendatakit.aggregate.exception.ODKExternalServiceException;
import org.opendatakit.aggregate.form.IForm;
import org.opendatakit.aggregate.format.Row;
import org.opendatakit.aggregate.format.element.FusionTableElementFormatter;
import org.opendatakit.aggregate.format.header.FusionTableHeaderFormatter;
import org.opendatakit.aggregate.server.ServerPreferencesProperties;
import org.opendatakit.aggregate.submission.Submission;
import org.opendatakit.aggregate.submission.SubmissionSet;
import org.opendatakit.aggregate.submission.SubmissionValue;
import org.opendatakit.aggregate.submission.type.RepeatSubmissionType;
import org.opendatakit.aggregate.task.UploadSubmissions;
import org.opendatakit.common.persistence.CommonFieldsBase;
import org.opendatakit.common.persistence.Datastore;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.persistence.exception.ODKEntityPersistException;
import org.opendatakit.common.persistence.exception.ODKOverQuotaException;
import org.opendatakit.common.security.User;
import org.opendatakit.common.security.common.EmailParser;
import org.opendatakit.common.utils.WebUtils;
import org.opendatakit.common.web.CallingContext;
import org.opendatakit.common.web.constants.BasicConsts;

/**
 * 
 * @author wbrunette@gmail.com
 * @author mitchellsundt@gmail.com
 * 
 */
public class FusionTable extends OAuth2ExternalService implements ExternalService {
  private static final int MAX_INSERT_STRING_LEN = 30000;

  private static final Log logger = LogFactory.getLog(FusionTable.class.getName());

  private static final String FUSION_TABLE_OAUTH2_SCOPE = "https://www.googleapis.com/auth/drive https://www.googleapis.com/auth/fusiontables";

  /**
   * Datastore entity specific to this type of external service
   */
  private final FusionTable2ParameterTable objectEntity;

  /**
   * Datastore entity specific to this type of external service for the repeats
   */
  private final List<FusionTable2RepeatParameterTable> repeatElementEntities = new ArrayList<FusionTable2RepeatParameterTable>();

  /**
   * Cached value of the currently-valid access token.
   */
  private String accessToken = null;

  /**
   * Common base initialization of a FusionTable (both new and existing).
   * 
   * @param entity
   * @param formServiceCursor
   * @param form
   * @param cc
   */
  private FusionTable(FusionTable2ParameterTable entity, FormServiceCursor formServiceCursor,
      IForm form, CallingContext cc) {
    super(form, formServiceCursor, new FusionTableElementFormatter(cc.getServerURL()),
        new FusionTableHeaderFormatter(), cc);
    objectEntity = entity;
  }

  /**
   * Continuation of the creation of a brand new FusionTable. Needed because
   * entity must be passed into two objects in the constructor.
   * 
   * @param entity
   * @param form
   * @param externalServiceOption
   * @param cc
   * @throws ODKEntityPersistException
   * @throws ODKOverQuotaException
   * @throws ODKDatastoreException
   */
  private FusionTable(FusionTable2ParameterTable entity, IForm form,
      ExternalServicePublicationOption externalServiceOption, CallingContext cc)
      throws ODKEntityPersistException, ODKOverQuotaException, ODKDatastoreException {
    this(entity, createFormServiceCursor(form, entity, externalServiceOption,
        ExternalServiceType.GOOGLE_FUSIONTABLES, cc), form, cc);

    // and create records for all the repeat elements (but without any actual
    // table ids)...
    Datastore ds = cc.getDatastore();
    User user = cc.getCurrentUser();
    FusionTable2RepeatParameterTable frpt = FusionTable2RepeatParameterTable.assertRelation(cc);

    for (FormElementModel repeatGroupElement : form.getRepeatGroupsInModel()) {
      FusionTable2RepeatParameterTable t = ds.createEntityUsingRelation(frpt, user);
      t.setUriFusionTable(objectEntity.getUri());
      t.setFormElementKey(repeatGroupElement.constructFormElementKey(form));
      repeatElementEntities.add(t);
    }
    persist(cc);
  }

  /**
   * Reconstruct a FusionTable definition from its persisted representation in
   * the datastore.
   * 
   * @param formServiceCursor
   * @param form
   * @param cc
   * @throws ODKDatastoreException
   */
  public FusionTable(FormServiceCursor formServiceCursor, IForm form, CallingContext cc)
      throws ODKDatastoreException {

    this(retrieveEntity(FusionTable2ParameterTable.assertRelation(cc), formServiceCursor, cc),
        formServiceCursor, form, cc);

    repeatElementEntities.addAll(FusionTable2RepeatParameterTable.getRepeatGroupAssociations(
        objectEntity.getUri(), cc));
  }

  /**
   * Create a brand new FusionTable
   * 
   * @param form
   * @param externalServiceOption
   * @param ownerUserEmail
   *          -- user that should be granted ownership of the fusionTable
   *          artifact(s)
   * @param cc
   * @throws ODKEntityPersistException
   * @throws ODKOverQuotaException
   * @throws ODKDatastoreException
   */
  public FusionTable(IForm form, ExternalServicePublicationOption externalServiceOption,
      String ownerEmail, CallingContext cc) throws ODKEntityPersistException,
      ODKOverQuotaException, ODKDatastoreException {
    this(newFusionTableEntity(ownerEmail, cc), form, externalServiceOption, cc);
  }

  /**
   * Helper function to create a FusionTable parameter table (missing the
   * not-yet-created tableId).
   * 
   * @param ownerEmail
   * @param cc
   * @return
   * @throws ODKDatastoreException
   */
  private static final FusionTable2ParameterTable newFusionTableEntity(String ownerEmail,
      CallingContext cc) throws ODKDatastoreException {
    Datastore ds = cc.getDatastore();
    User user = cc.getCurrentUser();

    FusionTable2ParameterTable t = ds.createEntityUsingRelation(
        FusionTable2ParameterTable.assertRelation(cc), user);
    t.setOwnerEmail(ownerEmail);
    return t;
  }

  @Override
  protected String getOwnership() {
    return objectEntity.getOwnerEmail().substring(EmailParser.K_MAILTO.length());
  }

  protected String getAccessToken(boolean forceRefresh, CallingContext cc)
      throws ODKExternalServiceCredentialsException {
    try {
      if (accessToken == null && !forceRefresh) {
        accessToken = ServerPreferencesProperties.getServerPreferencesProperty(cc,
            ServerPreferencesProperties.GOOGLE_FUSION_TABLE_OAUTH2_ACCESS_TOKEN);
      }

      if (accessToken == null || forceRefresh) {
        accessToken = getOAuth2AccessToken(FUSION_TABLE_OAUTH2_SCOPE, cc);
        ServerPreferencesProperties.setServerPreferencesProperty(cc,
            ServerPreferencesProperties.GOOGLE_FUSION_TABLE_OAUTH2_ACCESS_TOKEN, accessToken);
      }
      return accessToken;
    } catch (Exception e) {
      throw new ODKExternalServiceCredentialsException("Unable to obtain OAuth2 access token: "
          + e.toString());
    }
  }

  public void initiate(CallingContext cc) throws ODKExternalServiceException, ODKDatastoreException {

    if (fsc.isExternalServicePrepared() == null || !fsc.isExternalServicePrepared()) {

      if (objectEntity.getFusionTableId() == null) {
        String tableId = executeFusionTableCreation(form.getTopLevelGroupElement(), cc);
        objectEntity.setFusionTableId(tableId);
        persist(cc);
      }

      // See which of the repeat groups still need to have their tableId created
      // and define those...
      for (FormElementModel repeatGroupElement : form.getRepeatGroupsInModel()) {
        boolean found = false;
        FormElementKey elementKey = repeatGroupElement.constructFormElementKey(form);
        for (FusionTable2RepeatParameterTable t : repeatElementEntities) {
          if (elementKey.equals(t.getFormElementKey())) {
            // Found the match
            if (found) {
              // already found one match -- it is an error to have 2...
              throw new ODKExternalServiceException(
                  "duplicate row in FusionTable2RepeatParameterTable");
            }
            found = true;
            if (t.getFusionTableId() == null) {
              // create it and persist to remember it...
              String id = executeFusionTableCreation(repeatGroupElement, cc);
              t.setFusionTableId(id);
              persist(cc);
            }
          }
        }
        if (!found) {
          throw new ODKExternalServiceException("missing row in FusionTable2RepeatParameterTable");
        }
      }

      // create a view
      List<RepeatViewInfo> repeatInfo = new ArrayList<RepeatViewInfo>();
      for (FusionTable2RepeatParameterTable ftRepeat : repeatElementEntities) {
        for (FormElementModel repeatGroupElement : form.getRepeatGroupsInModel()) {
          FormElementKey elementKey = repeatGroupElement.constructFormElementKey(form);
          if (elementKey.equals(ftRepeat.getFormElementKey())
              && repeatGroupElement.getParent().equals(form.getTopLevelGroupElement())) {
            repeatInfo.add(new RepeatViewInfo(ftRepeat, repeatGroupElement));
            break;
          }
        }
      }

      if (!repeatInfo.isEmpty()) {
        String viewId = executeFusionTableViewCreation(objectEntity.getFusionTableId(), repeatInfo, form, cc);
        objectEntity.setFusionTableViewId(viewId);
        persist(cc);
      }
      // transfer ownership before marking service as prepared...
      sharePublishedFiles(objectEntity.getOwnerEmail(), cc);

      fsc.setIsExternalServicePrepared(true);
    }
    fsc.setOperationalStatus(OperationalStatus.ACTIVE);
    persist(cc);

    // upload data to external service
    if (!fsc.getExternalServicePublicationOption().equals(
        ExternalServicePublicationOption.STREAM_ONLY)) {

      UploadSubmissions uploadTask = (UploadSubmissions) cc.getBean(BeanDefs.UPLOAD_TASK_BEAN);
      CallingContext ccDaemon = ContextFactory.duplicateContext(cc);
      ccDaemon.setAsDaemon(true);
      uploadTask.createFormUploadTask(fsc, true, ccDaemon);

    }
  }

  public void sharePublishedFiles(String ownerEmail, CallingContext cc)
      throws ODKExternalServiceException, ODKDatastoreException {

    executeDrivePermission(objectEntity.getFusionTableId(), objectEntity.getOwnerEmail(), logger,
        "fusion table", cc);
    if (objectEntity.getFusionTableViewId() != null) {
      executeDrivePermission(objectEntity.getFusionTableViewId(), objectEntity.getOwnerEmail(),
          logger, "fusion table", cc);
    }

    // and share all the nested element keys...
    for (FusionTable2RepeatParameterTable t : repeatElementEntities) {
      String id = t.getFusionTableId();
      if (id != null) {
        executeDrivePermission(id, objectEntity.getOwnerEmail(), logger, "fusion table", cc);
      }
    }
  }

  @Override
  protected void insertData(Submission submission, CallingContext cc)
      throws ODKExternalServiceException {
    try {
      FusionTableFormattedSubmission formattedSubmission = new FusionTableFormattedSubmission(
          submission, cc);

      executeStmt(POST, FUSION_TABLE_QUERY_API,
          formattedSubmission.getFormattedStringForTable(objectEntity.getFusionTableId()), null, cc);

      // upload repeat value
      for (FusionTable2RepeatParameterTable tableId : repeatElementEntities) {
        executeStmt(POST, FUSION_TABLE_QUERY_API,
            formattedSubmission.getFormattedStringForTable(tableId.getFusionTableId()), null, cc);
      }

    } catch (ODKExternalServiceCredentialsException e) {
      fsc.setOperationalStatus(OperationalStatus.BAD_CREDENTIALS);
      try {
        persist(cc);
      } catch (Exception e1) {
        e1.printStackTrace();
        throw new ODKExternalServiceException(
            "Unable to set OperationalStatus to Bad credentials: " + e1);
      }
      throw e;
    } catch (ODKExternalServiceException e) {
      e.printStackTrace();
      throw e;
    } catch (Exception e) {
      e.printStackTrace();
      throw new ODKExternalServiceException(e);
    }
  }

  @Override
  public void sendSubmissions(List<Submission> submissions, boolean streaming, CallingContext cc)
      throws ODKExternalServiceException {

    Date lastDateSent = null;
    String lastKeySent = null;
    Datastore ds = cc.getDatastore();
    User user = cc.getCurrentUser();
    try {

      SubmissionBatcher batcher = new SubmissionBatcher(submissions, MAX_INSERT_STRING_LEN, cc);

      while (batcher.processSubmissionsForBatch()) {

        String tmpStr = batcher.getBatchInsertString(objectEntity.getFusionTableId());
        //System.out.println(tmpStr);
        executeStmt(POST, FUSION_TABLE_QUERY_API, tmpStr, null, cc);

        // upload repeat value
        for (FusionTable2RepeatParameterTable tableId : repeatElementEntities) {
          executeStmt(POST, FUSION_TABLE_QUERY_API,
              batcher.getBatchInsertString(tableId.getFusionTableId()), null, cc);
        }

        // See QueryByDateRange
        // -- we are querying by the markedAsCompleteDate
        // update the last one proccessed
        Submission lastSubmission = batcher.getLastProccessedSubmission();
        lastDateSent = lastSubmission.getMarkedAsCompleteDate();
        lastKeySent = lastSubmission.getKey().getKey();
        if (streaming) {
          fsc.setLastStreamingCursorDate(lastDateSent);
          fsc.setLastStreamingKey(lastKeySent);
        } else {
          fsc.setLastUploadCursorDate(lastDateSent);
          fsc.setLastUploadKey(lastKeySent);
        }
        ds.putEntity(fsc, user);
      }

    } catch (ODKExternalServiceCredentialsException e) {
      fsc.setOperationalStatus(OperationalStatus.BAD_CREDENTIALS);
      try {
        persist(cc);
      } catch (Exception e1) {
        e1.printStackTrace();
        throw new ODKExternalServiceException(
            "Unable to set OperationalStatus to Bad credentials: " + e1);
      }
      throw e;
    } catch (ODKExternalServiceException e) {
      e.printStackTrace();
      throw e;
    } catch (Exception e) {
      e.printStackTrace();
      throw new ODKExternalServiceException(e);
    }

  }

  @Override
  public boolean canBatchSubmissions() {
    return true;
  }

  private String executeFusionTableCreation(FormElementModel root, CallingContext cc)
      throws ODKExternalServiceException {

    String resultRequest;
    try {
      String createStmt = createFusionTableStatement(form, root);
      resultRequest = executeStmt(POST, FUSION_TABLE_TABLE_API, createStmt, null, cc);
    } catch (ODKExternalServiceException e) {
      logger.error("Failed to create fusion table: " + e.getMessage());
      e.printStackTrace();
      throw e;
    } catch (Exception e) {
      logger.error("Failed to create fusion table: " + e.getMessage());
      e.printStackTrace();
      throw new ODKExternalServiceException(e);
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> result = mapper.readValue(resultRequest, Map.class);
      String tableId = (String) result.get("tableId");
      if (tableId != null && tableId.length() > 0) {
        return tableId;
      } else {
        throw new ODKExternalServiceException(ErrorConsts.ERROR_OBTAINING_FUSION_TABLE_ID);
      }
    } catch (JsonParseException e) {
      logger.error("Failed to create fusion table: " + e.getMessage());
      e.printStackTrace();
      throw new ODKExternalServiceException(e);
    } catch (JsonMappingException e) {
      logger.error("Failed to create fusion table: " + e.getMessage());
      e.printStackTrace();
      throw new ODKExternalServiceException(e);
    } catch (IOException e) {
      logger.error("Failed to create fusion table: " + e.getMessage());
      e.printStackTrace();
      throw new ODKExternalServiceException(e);
    }
  }

  private String createFusionTableStatement(IForm form, FormElementModel rootNode)
      throws JsonGenerationException, JsonMappingException, IOException {

    Map<String, Object> tableResource = new HashMap<String, Object>();
    tableResource.put("kind", "fusiontables#table");
    tableResource.put("name", rootNode.getElementName());
    // round to minutes...
    long nowRounded = 60000L * (System.currentTimeMillis() / 60000L);
    Date d = new Date(nowRounded);
    String timestamp = WebUtils.iso8601Date(d);
    tableResource.put("description",
        form.getViewableName() + " " + timestamp + " - " + rootNode.getElementName());
    tableResource.put("isExportable", true);

    List<String> headers = headerFormatter.generateHeaders(form, rootNode, null);

    // types are in the same order as the headers...
    List<ElementType> types = headerFormatter.getHeaderTypes();

    List<Map<String, Object>> columnResources = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < headers.size(); ++i) {
      String colName = headers.get(i);
      ElementType type = types.get(i);

      Map<String, Object> colResource = new HashMap<String, Object>();
      colResource.put("kind", "fusiontables#column");
      colResource.put("name", colName);
      colResource.put("type", FusionTableConsts.typeMap.get(type).getFusionTypeValue());

      columnResources.add(colResource);
    }
    tableResource.put("columns", columnResources);

    String createStmt = mapper.writeValueAsString(tableResource);

    return createStmt;
  }

  private String executeFusionTableViewCreation(String parentTableId, List<RepeatViewInfo> repeatInfoList,
      IForm form, CallingContext cc)
      throws ODKExternalServiceException {

    String resultRequest;
    try {
      String createViewStmt = "CREATE VIEW '" + form.getViewableName() + "View' AS ";
      createViewStmt += "(SELECT * FROM " + parentTableId + " AS T1 \n";
      int counter = 2;
      for(RepeatViewInfo repeatInfo : repeatInfoList) {
        createViewStmt += "LEFT OUTER JOIN " + repeatInfo.getFtRepeat().getFusionTableId();
        createViewStmt += " AS T" + counter + " ON T1.'" + repeatInfo.getFemRepeat().getElementName() + "'";
        createViewStmt += " = T" + counter + ".'" + FormatConsts.HEADER_PARENT_UID + "' \n";
        counter++;
      }
      createViewStmt += ")";          
      resultRequest = executeStmt(POST, FUSION_TABLE_QUERY_API, createViewStmt, null, cc);
    } catch (ODKExternalServiceException e) {
      logger.error("Failed to create fusion table VIEW: " + e.getMessage());
      e.printStackTrace();
      throw e;
    } catch (Exception e) {
      logger.error("Failed to create fusion table VIEW: " + e.getMessage());
      e.printStackTrace();
      throw new ODKExternalServiceException(e);
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> result = mapper.readValue(resultRequest, Map.class);
      String kind = (String) result.get("kind");
      if (kind.equals("fusiontables#sqlresponse")) {
        @SuppressWarnings("unchecked")
        ArrayList<Object> columns = (ArrayList<Object>) result.get("columns");
        if (!columns.isEmpty() && columns.get(0).equals("tableid")) {
          @SuppressWarnings("unchecked")
          ArrayList<Object> rows = (ArrayList<Object>) result.get("rows");
          if (!rows.isEmpty()) {
            @SuppressWarnings("unchecked")
            ArrayList<Object> row = (ArrayList<Object>) rows.get(0);
            return (String) row.get(0);
          }
        }
      }
      throw new ODKExternalServiceException("PROBLEM GETTING FT VIEW ID");

    } catch (JsonParseException e) {
      logger.error("Failed to create fusion table VIEW: " + e.getMessage());
      e.printStackTrace();
      throw new ODKExternalServiceException(e);
    } catch (JsonMappingException e) {
      logger.error("Failed to create fusion table VIEW: " + e.getMessage());
      e.printStackTrace();
      throw new ODKExternalServiceException(e);
    } catch (IOException e) {
      logger.error("Failed to create fusion table VIEW: " + e.getMessage());
      e.printStackTrace();
      throw new ODKExternalServiceException(e);
    }
  }

  /**
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FusionTable)) {
      return false;
    }
    FusionTable other = (FusionTable) obj;
    return (objectEntity == null ? (other.objectEntity == null)
        : (other.objectEntity != null && objectEntity.equals(other.objectEntity)))
        && (fsc == null ? (other.fsc == null) : (other.fsc != null && fsc.equals(other.fsc)));
  }

  private String buildHref(String fusionTableId, String linkText) {
    Map<String, String> properties = new HashMap<String, String>();
    if (fusionTableId == null) {
      return linkText + " not yet created";
    }
    if (fusionTableId.toLowerCase().equals(fusionTableId.toUpperCase())) {
      properties.put("dsrcid", fusionTableId);
    } else {
      properties.put("docid", fusionTableId);
    }
    return HtmlUtil.createHrefWithProperties("http://www.google.com/fusiontables/DataSource",
        properties, "View " + linkText, true);
  }

  @Override
  public String getDescriptiveTargetString() {
    List<String> hrefs = new ArrayList<String>();

    hrefs.add(buildHref(objectEntity.getFusionTableId(), "Main Fusion Table"));
    // and share all the nested element keys...
    for (FusionTable2RepeatParameterTable t : repeatElementEntities) {
      String id = t.getFusionTableId();
      if (id != null) {
        String repeatKey = t.getFormElementKey().toString();
        repeatKey = repeatKey.substring(repeatKey.indexOf('/', repeatKey.indexOf('/', 1) + 1) + 1);
        hrefs.add(buildHref(id, "Repeat Group " + repeatKey));
      }
    }
    if (objectEntity.getFusionTableViewId() != null) {
      hrefs.add(buildHref(objectEntity.getFusionTableViewId(), "Outer Join (first repeat only)"));
    }

    StringBuilder b = new StringBuilder();
    b.append("<table>");
    for (String href : hrefs) {
      b.append("<tr><td>").append(href).append("</td></tr>");
    }
    b.append("</table>");
    return b.toString();
  }

  protected CommonFieldsBase retrieveObjectEntity() {
    return objectEntity;
  }

  @Override
  protected List<? extends CommonFieldsBase> retrieveRepeatElementEntities() {
    return repeatElementEntities;
  }

  /**
   * Private class that turns a submission into the formatted strings to be
   * batched
   * 
   */

  private class FusionTableFormattedSubmission {

    private final Submission submission;

    private final Map<String, String> formattedSubmissionStrings;

    FusionTableFormattedSubmission(Submission submission, CallingContext cc)
        throws ODKExternalServiceException, ODKDatastoreException {
      this.submission = submission;
      this.formattedSubmissionStrings = new HashMap<String, String>();

      List<String> headers = headerFormatter.generateHeaders(form, form.getTopLevelGroupElement(),
          null);
      String tmpStr = formatInsertStmt(objectEntity.getFusionTableId(), submission, headers, cc);
      formattedSubmissionStrings.put(objectEntity.getFusionTableId(), tmpStr);

      // upload repeat value
      for (FusionTable2RepeatParameterTable tableId : repeatElementEntities) {
        FormElementKey elementKey = tableId.getFormElementKey();
        FormElementModel element = FormElementModel.retrieveFormElementModel(form, elementKey);
        headers = headerFormatter.generateHeaders(form, element, null);

        List<SubmissionValue> values = submission.findElementValue(element);
        for (SubmissionValue value : values) {
          if (value instanceof RepeatSubmissionType) {
            RepeatSubmissionType repeat = (RepeatSubmissionType) value;
            if (repeat.getElement().equals(element)) {
              StringBuilder repeatTmpStr = new StringBuilder();
              for (SubmissionSet set : repeat.getSubmissionSets()) {
                repeatTmpStr.append(formatInsertStmt(tableId.getFusionTableId(), set, headers, cc));
                repeatTmpStr.append(BasicConsts.SEMI_COLON);
              }

              // remove last semi-colon
              if (repeatTmpStr.length() > 0) {
                int lastChar = repeatTmpStr.length() - 1;
                if (repeatTmpStr.charAt(lastChar) == ';') {
                  repeatTmpStr.deleteCharAt(lastChar);
                }
              }

              // put concatenated insert strings in the amp
              formattedSubmissionStrings.put(tableId.getFusionTableId(), repeatTmpStr.toString());
            }
          } else {
            System.err.println("ERROR: How did a non Repeat Submission Type get in the for loop?");
          }
        }
      }
    }

    Submission getSubmission() {
      return submission;
    }

    String getFormattedStringForTable(String tableId) {
      return formattedSubmissionStrings.get(tableId);
    }

    private String formatInsertStmt(String tableId, SubmissionSet set, List<String> headers,
        CallingContext cc) throws ODKDatastoreException {
      Row row = set.getFormattedValuesAsRow(null, formatter, true, cc);

      return FusionTableConsts.INSERT_STMT + tableId + createCsvString(headers.iterator())
          + FusionTableConsts.VALUES_STMT + createCsvString(row.getFormattedValues().iterator());
    }

    private String createCsvString(Iterator<String> itr) {
      StringBuilder str = new StringBuilder();
      str.append(BasicConsts.SPACE + BasicConsts.LEFT_PARENTHESIS);
      while (itr.hasNext()) {
        String cur = itr.next();
        str.append(BasicConsts.SINGLE_QUOTE);
        if (cur != null) {
          String tmp = cur.replaceAll(FusionTableConsts.SINGLE_QUOTE,
              FusionTableConsts.HTML_ESCAPED_SINGLE_QUOTE);
          str.append(tmp);
        }
        str.append(BasicConsts.SINGLE_QUOTE);
        if (itr.hasNext()) {
          str.append(FormatConsts.CSV_DELIMITER);
        }
      }
      str.append(BasicConsts.RIGHT_PARENTHESIS + BasicConsts.SPACE);
      return str.toString();
    }

  }

  /**
   * Private class that determines how to break up the submission into batches
   * 
   */
  private class SubmissionBatcher {

    private final LinkedList<FusionTableFormattedSubmission> needToProcess;

    private final LinkedList<FusionTableFormattedSubmission> currentSet;

    private final List<String> tableIds;

    private final Map<String, String> formattedSubmissionStrings;

    private final int maxStringSize;

    private FusionTableFormattedSubmission lastProccessedSubmission;

    SubmissionBatcher(List<Submission> submissions, int maxStringSize, CallingContext cc)
        throws ODKExternalServiceException, ODKDatastoreException {
      this.maxStringSize = maxStringSize;
      this.needToProcess = new LinkedList<FusionTableFormattedSubmission>();
      this.currentSet = new LinkedList<FusionTableFormattedSubmission>();
      this.tableIds = new ArrayList<String>();
      this.formattedSubmissionStrings = new HashMap<String, String>();
      this.lastProccessedSubmission = null;

      // obtain all tableIds
      tableIds.add(objectEntity.getFusionTableId());
      for (FusionTable2RepeatParameterTable tableId : repeatElementEntities) {
        tableIds.add(tableId.getFusionTableId());
      }

      // process submissions to be ready to parse
      for (Submission submission : submissions) {
        needToProcess.add(new FusionTable.FusionTableFormattedSubmission(submission, cc));
      }
    }

    Submission getLastProccessedSubmission() {
      if (lastProccessedSubmission != null) {
        return lastProccessedSubmission.getSubmission();
      }
      return null;
    }

    /**
     * Get string for batch insert, removes the string so not available after
     * the first call
     * 
     * 
     * @param tableId
     * 
     * @return string and remove from batch temp storage
     */
    String getBatchInsertString(String tableId) {
      String tmpStr = formattedSubmissionStrings.get(tableId);
      formattedSubmissionStrings.put(tableId, null);
      return tmpStr;
    }

    private boolean additionalCapacityInBatchStrings() {
      for (String tableId : tableIds) {
        String batchStr = formattedSubmissionStrings.get(tableId);
        if (batchStr != null) {
          if (batchStr.length() > maxStringSize) {
            return false;
          }
        }
      }
      return true;
    }

    /**
     * Processes the next group of submissions producing the strings for group
     * inserts
     * 
     * Throws an exception if all strings have not been removed as assuming if
     * not removed, never got transmitted
     * 
     * @return true if more submissions need to be processed to be sent, false
     *         if all submissions have been processed
     * @throws ODKExternalServiceException
     */
    boolean processSubmissionsForBatch() throws ODKExternalServiceException {
      if (needToProcess.isEmpty()) {
        return false;
      }

      // verify
      for (String tableId : tableIds) {
        if (formattedSubmissionStrings.get(tableId) != null) {
          throw new ODKExternalServiceException(
              "ERROR! - Attempting to get more submission strings before all previous have been transmitted");
        }
      }

      // remove old submission set
      currentSet.clear();
      lastProccessedSubmission = null;

      // process remaining submissions into batch string
      while (additionalCapacityInBatchStrings() && !needToProcess.isEmpty()) {

        FusionTableFormattedSubmission formatSub = needToProcess.remove();

        for (String tableId : tableIds) {
          String formattedSubmissionStr = formatSub.getFormattedStringForTable(tableId);
          String concatStr = formattedSubmissionStrings.get(tableId);
          if (concatStr != null) {
            concatStr += "; " + formattedSubmissionStr;
          } else {
            concatStr = formattedSubmissionStr;
          }
          formattedSubmissionStrings.put(tableId, concatStr);
        }

        currentSet.addLast(formatSub);
        lastProccessedSubmission = formatSub;

      }

      return true;
    }

  }

  private class RepeatViewInfo {
    private final FusionTable2RepeatParameterTable ftRepeat;
    private final FormElementModel femRepeat;

    RepeatViewInfo(FusionTable2RepeatParameterTable ftRepeat, FormElementModel femRepeat) {
      this.ftRepeat = ftRepeat;
      this.femRepeat = femRepeat;
    }

    FusionTable2RepeatParameterTable getFtRepeat() {
      return ftRepeat;
    }

    FormElementModel getFemRepeat() {
      return femRepeat;
    }

  }

}
