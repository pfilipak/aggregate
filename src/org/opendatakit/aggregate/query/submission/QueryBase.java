/*
 * Copyright (C) 2010 University of Washington
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
package org.opendatakit.aggregate.query.submission;

import java.util.List;

import org.opendatakit.aggregate.CallingContext;
import org.opendatakit.aggregate.datamodel.FormElementModel;
import org.opendatakit.aggregate.exception.ODKFormNotFoundException;
import org.opendatakit.aggregate.exception.ODKIncompleteSubmissionData;
import org.opendatakit.aggregate.form.Form;
import org.opendatakit.aggregate.submission.Submission;
import org.opendatakit.common.persistence.CommonFieldsBase;
import org.opendatakit.common.persistence.Query;
import org.opendatakit.common.persistence.Query.FilterOperation;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;

/**
 * 
 * @author wbrunette@gmail.com
 * @author mitchellsundt@gmail.com
 * 
 */
public abstract class QueryBase {

  protected Query query;
  protected final Form form;
  
  private boolean moreRecords;
  private int fetchLimit;
  
  private int numOfRecords;
  
  protected QueryBase(Form form, int maxFetchLimit) throws ODKFormNotFoundException {
    fetchLimit = maxFetchLimit;
    numOfRecords = 0;
    this.form = form;
  }

  /**
   * CAUTION: the attribute must be in the top-level record!
   * 
   * @param attribute
   * @param op
   * @param value
   */
  public void addFilter(FormElementModel attribute, FilterOperation op,
  						Object value) {
  	query.addFilter(attribute.getFormDataModel().getBackingKey(), op, value);
  }

  public abstract List<Submission> getResultSubmissions(CallingContext cc) throws ODKIncompleteSubmissionData, ODKDatastoreException;

  public boolean moreRecordsAvailable() {
    return moreRecords;
  }
  
  public final Form getForm(){
    return form;
  }
  
  /**
   * Generates a result table that contains all the submission data 
   * of the form specified by the ODK ID
   * 
   * @return
   *    a result table containing submission data
 * @throws ODKDatastoreException 
   *
   * @throws ODKIncompleteSubmissionData 
   */
  protected List<? extends CommonFieldsBase> getSubmissionEntities() throws ODKDatastoreException {

    // retrieve submissions
    List<? extends CommonFieldsBase> submissionEntities = null;
    submissionEntities = query.executeQuery(fetchLimit + 1);
    numOfRecords = submissionEntities.size();
    if(submissionEntities.size() > fetchLimit) {
      moreRecords = true;
      submissionEntities.remove(fetchLimit);
    }    
    return submissionEntities;
  }
  

  public int getNumRecords() {
    return numOfRecords;
  }
  
}
