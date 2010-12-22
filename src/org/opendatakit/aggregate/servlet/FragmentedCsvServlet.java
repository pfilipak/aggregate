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

package org.opendatakit.aggregate.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opendatakit.aggregate.CallingContext;
import org.opendatakit.aggregate.ContextFactory;
import org.opendatakit.aggregate.constants.HtmlUtil;
import org.opendatakit.aggregate.constants.ServletConsts;
import org.opendatakit.aggregate.exception.ODKFormNotFoundException;
import org.opendatakit.aggregate.exception.ODKParseException;
import org.opendatakit.aggregate.form.Form;
import org.opendatakit.aggregate.format.table.FragmentedCsvFormatter;
import org.opendatakit.aggregate.query.submission.QueryByDate;
import org.opendatakit.aggregate.submission.Submission;
import org.opendatakit.aggregate.submission.SubmissionKey;
import org.opendatakit.aggregate.submission.SubmissionKeyPart;
import org.opendatakit.common.constants.BasicConsts;
import org.opendatakit.common.constants.HtmlConsts;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;

/**
 * Servlet to generate a CSV file for download, in parts!
 * 
 * @author wbrunette@gmail.com
 * @author mitchellsundt@gmail.com
 * 
 */
public class FragmentedCsvServlet extends ServletUtilBase {

	private static final long serialVersionUID = 9161862118534323521L;

   /**
    * Title for generated webpage
    */
   private static final String TITLE_INFO = "Download CSV Dataset Range";
   
   private static final String WEBSAFE_CURSOR_SEPARATOR = " and ";
   /**
    * URI from base
    */
	public static final String ADDR = "view/csvFragment";

	private static final int DEFAULT_NUM_ENTRIES = 1000;
	
  /**
   * Handler for HTTP Get request that responds with CSV
   * 
   * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest,
   *      javax.servlet.http.HttpServletResponse)
   */
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
	CallingContext cc = ContextFactory.getCallingContext(getServletContext());

    // required common parameters
    // form or form element identity
    // -- a forward-slash separated list of identity and group or repeat names
    // that identifies the Form or FormElement to retrieve.  It is a form
    // if the path has one or two entries, otherwise it is a repeat group.
    SubmissionKey submissionKey = new SubmissionKey(getParameter(req, ServletConsts.FORM_ID));
    
    // optional common parameters
    // for client-side simplicity, if these have "" values, treat them as null
    
    // cursor -- tracks where we resume our record fetch (if missing, we start over)
    String websafeCursorString = getParameter(req, ServletConsts.CURSOR);
    Date dateCode = null;
    String uriAfter = null;
    if ( websafeCursorString != null && websafeCursorString.length() == 0 ) {
    	websafeCursorString = null;
    	dateCode = BasicConsts.EPOCH;
    } else {
    	int idx = websafeCursorString.indexOf(WEBSAFE_CURSOR_SEPARATOR);
    	if ( idx == -1 ) {
    		errorBadParam(resp);
    		return;
    	}
    	String dateString = websafeCursorString.substring(0,idx);
    	uriAfter = websafeCursorString.substring(idx + WEBSAFE_CURSOR_SEPARATOR.length());
    	try {
    		dateCode = new Date(Long.valueOf(dateString));
    	} catch (NumberFormatException e) {
    		errorBadParam(resp);
    		return;
    	}
    }
    
    // number of records to fetch
    String numEntriesStr = getParameter(req, ServletConsts.NUM_ENTRIES);
    if ( numEntriesStr != null && numEntriesStr.length() == 0 ) {
    	numEntriesStr = null;
    }
    
    PrintWriter out = resp.getWriter();
    
    try {
    	int numEntriesToFetch = 0;
    	try {
    		if ( numEntriesStr == null ) {
    			numEntriesToFetch = DEFAULT_NUM_ENTRIES;
    		} else {
    			numEntriesToFetch = Integer.parseInt(numEntriesStr);
    		}
    	} catch (NumberFormatException e) {
    		throw new ODKParseException("Invalid number of entries parameter", e);
    	}
    	
    	// Pick apart the odkId to identify any specific element references
    	// Element references have [@key="..."] clauses on the element name.
    	// At most one [@key="..."] clause should appear prior to the last element.
    	// That clause will be assumed to be the parent key for the last element's relation.
    	//
    	// e.g., 
    	//  myDataForm/data/repeat1   -- access all rows of repeat1 values.
    	//  myDataForm/data[@key="abc"]/repeat1 -- access all repeat1 values with parent data "abc"
    	//  myDataForm/data/repeat1[@key="abc"] -- access the repeat1 record "abc"
    	//
    	// odkPath ends up being: [ "myDataForm", "data", "repeat1" ]
    	// elementReference is either null, or the key to the record
    	// elementParentKey is the parent key that the record must have.
    	List<SubmissionKeyPart> submissionKeyParts = submissionKey.splitSubmissionKey();
//    	Key elementReference = null;
//    	Key elementParentKey = null;
    	
        if (submissionKeyParts != null && submissionKeyParts.size() > 2 && numEntriesToFetch > 0) {
        	// repeating groups...
        	// reworked from formmultiplevalueservlet.java
        	Form form = Form.retrieveForm(submissionKeyParts.get(0).toString(), cc);


            QueryByDate query = new QueryByDate(form, dateCode, false, true, true,
                    numEntriesToFetch, cc);
            List<Submission> submissions = query.getResultSubmissions();
            List<Submission> activeList = new ArrayList<Submission>();
            Submission lastSubmission = null;
            for ( Submission s : submissions ) {
            	if ( uriAfter != null ) {
            		if ( s.getKey().getKey().compareTo(uriAfter) <= 0 ) continue;
            		uriAfter = null;
            	}
            	activeList.add(s);
            	lastSubmission = s;
            }

        	if ( lastSubmission != null ) {
        		websafeCursorString = Long.toString(lastSubmission.getLastUpdateDate().getTime()) +
        		" and " + lastSubmission.getKey().getKey();
        	}

        	resp.setContentType("text/xml; charset=UTF-8");
        	resp.setCharacterEncoding("UTF-8");

        	FragmentedCsvFormatter fmt = new FragmentedCsvFormatter(form, submissionKeyParts, getServerURL(req), websafeCursorString, out);
        	fmt.processSubmissions(submissions);
        } else if( submissionKeyParts != null &&
        		((submissionKeyParts.size() == 2 && submissionKeyParts.get(1).getAuri() == null) ||
        		 (submissionKeyParts.size() == 1)) &&
        		numEntriesToFetch > 0) {
        	// top-level form has no parent...
        	// top-level form can be referenced either by just "form-identity" or by "form-identity/top-level-tag"
	    	// reworked from formxmlservlet.java
        	Form form = Form.retrieveForm(submissionKeyParts.get(0).toString(), cc);

            QueryByDate query = new QueryByDate(form, dateCode, false, true, true,
                    numEntriesToFetch, cc);
            List<Submission> submissions = query.getResultSubmissions();
            List<Submission> activeList = new ArrayList<Submission>();
            Submission lastSubmission = null;
            for ( Submission s : submissions ) {
            	if ( uriAfter != null ) {
            		if ( s.getKey().getKey().compareTo(uriAfter) <= 0 ) continue;
            		uriAfter = null;
            	}
            	activeList.add(s);
            	lastSubmission = s;
            }

            if ( lastSubmission != null ) {
        		websafeCursorString = Long.toString(lastSubmission.getLastUpdateDate().getTime()) +
        		" and " + lastSubmission.getKey().getKey();
        	}

            resp.setContentType("text/xml; charset=UTF-8");
        	resp.setCharacterEncoding("UTF-8");

        	FragmentedCsvFormatter fmt = new FragmentedCsvFormatter(form, submissionKeyParts, getServerURL(req), websafeCursorString, out);
        	fmt.processSubmissions(submissions);
        	
        } else {
            beginBasicHtmlResponse(TITLE_INFO, resp, req, true); // header info
            String requestPath = HtmlUtil.createUrl(getServerURL(req)) + ADDR;
            out.write(HtmlUtil.wrapWithHtmlTags(HtmlConsts.H3, "Parameters are not correctly specified."));
            out.write(HtmlConsts.TABLE_OPEN);
            out.write(HtmlUtil.wrapWithHtmlTags(HtmlConsts.TABLE_ROW,
            					HtmlUtil.wrapWithHtmlTags(HtmlConsts.TABLE_DATA,"Parameter")
              					+ HtmlUtil.wrapWithHtmlTags(HtmlConsts.TABLE_DATA,"Description")
            			)
            		   + HtmlUtil.wrapWithHtmlTags(HtmlConsts.TABLE_ROW,
             				    HtmlUtil.wrapWithHtmlTags(HtmlConsts.TABLE_DATA,ServletConsts.FORM_ID)
             				    + HtmlUtil.wrapWithHtmlTags(HtmlConsts.TABLE_DATA,"Required for accessing all data associated with a form.  This is a path rooted at the Form Identity displayed in the forms list.")
             		   )
             		   + HtmlUtil.wrapWithHtmlTags(HtmlConsts.TABLE_ROW,
            				    HtmlUtil.wrapWithHtmlTags(HtmlConsts.TABLE_DATA,ServletConsts.NUM_ENTRIES)
            				    + HtmlUtil.wrapWithHtmlTags(HtmlConsts.TABLE_DATA,"Optional.  The number of rows of data to return in a result csv.  If you are having transmission issues, you may need to reduce the number of records you fetch.  The default is 1000.")
            		   )
              		   + HtmlUtil.wrapWithHtmlTags(HtmlConsts.TABLE_ROW,
              				    HtmlUtil.wrapWithHtmlTags(HtmlConsts.TABLE_DATA,ServletConsts.CURSOR)
              				    + HtmlUtil.wrapWithHtmlTags(HtmlConsts.TABLE_DATA,"Optional.  Required for accessing subsequent blocks of data.  Supplied as the <cursor> value from the previous web request.")
              		   )
            		   );
            out.write(HtmlConsts.TABLE_CLOSE);

            String formIdentity = "widgets";
            out.write(HtmlUtil.wrapWithHtmlTags(HtmlConsts.P, "To download a csv fragment for the non-repeating elements of a form, append the Form Identifier and the number of entries to fetch to this url, e.g., "));
            out.write(HtmlUtil.wrapWithHtmlTags(HtmlConsts.PRE, requestPath + "?" + ServletConsts.FORM_ID + "=" + formIdentity + "&" + ServletConsts.NUM_ENTRIES + "=1000" ));

            out.write(HtmlUtil.wrapWithHtmlTags(HtmlConsts.P, "The " + ServletConsts.FORM_ID + " parameter supports an xpath-like specification of repeat groups within a form (e.g., widgets/widgets/repeat_a) and primary key restrictions on the last or next-to-last element in the path."));
            out.write(HtmlUtil.wrapWithHtmlTags(HtmlConsts.UL, 
            		HtmlUtil.wrapWithHtmlTags(HtmlConsts.LI,
            				HtmlUtil.wrapWithHtmlTags(HtmlConsts.PRE, requestPath + "?" + ServletConsts.FORM_ID + "=widgets/widgets/repeat_a") 
            				+ " returns all repeat_a rows.") +
            		HtmlUtil.wrapWithHtmlTags(HtmlConsts.LI,
            				HtmlUtil.wrapWithHtmlTags(HtmlConsts.PRE, requestPath + "?" + ServletConsts.FORM_ID + "=widgets/widgets[@key=\"aaaa\"]/repeat_a") 
            				+ " returns all repeat_a rows for the widgets record identified by key \"aaaa\".") +
            		HtmlUtil.wrapWithHtmlTags(HtmlConsts.LI,
            				HtmlUtil.wrapWithHtmlTags(HtmlConsts.PRE, requestPath + "?" + ServletConsts.FORM_ID + "=widgets/widgets/repeat_a[@key=\"bbb\"]")
            				+ " returns the repeat_a row identified by key \"bbb\".")));
            out.write(HtmlUtil.wrapWithHtmlTags(HtmlConsts.P, "The data returned is a text/xml document as follows:"));
            out.write(HtmlUtil.wrapWithHtmlTags(HtmlConsts.PRE,
            				"&lt;entries&gt;\n"+
            				"  &lt;cursor&gt;...&lt;/cursor&gt; &lt;!-- only present if additional records may be fetched --&gt;\n"+
            				"  &lt;header&gt;...&lt;/header&gt; &lt;!-- csv -- property names --&gt;\n"+
            				"  &lt;result&gt;...&lt;/result&gt; &lt;!-- csv -- values -- repeats 0 or more times --&gt;\n"+
            				"&lt;/entries&gt;\n"));
            out.write(HtmlUtil.wrapWithHtmlTags(HtmlConsts.P, "The returned form data includes an additional property (as the right-most column): KEY.  The KEY value is the URL for this item on the Aggregate server."));
            out.write(HtmlUtil.wrapWithHtmlTags(HtmlConsts.P, "The returned repeated group data within a form includes two additional properties (as the next-to-right-most and right-most columns): PARENT_KEY and KEY.  The PARENT_KEY value is the URL for the parent item of this repeated group on the Aggregate server; the KEY value is the URL for this repeated group item on the Aggregate server."));
            

            resp.setStatus(400);
            finishBasicHtmlResponse(resp);
        }	
    } catch (ODKFormNotFoundException e) {
        odkIdNotFoundError(resp);
    } catch (ODKParseException e) {
    	errorBadParam(resp);
	} catch (ODKDatastoreException e) {
		errorRetreivingData(resp);
	}
  }
}
