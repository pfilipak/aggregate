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
package org.opendatakit.aggregate.format.element;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opendatakit.aggregate.constants.HtmlUtil;
import org.opendatakit.aggregate.constants.ServletConsts;
import org.opendatakit.aggregate.constants.format.FormTableConsts;
import org.opendatakit.aggregate.constants.format.FormatConsts;
import org.opendatakit.aggregate.constants.format.KmlConsts;
import org.opendatakit.aggregate.datamodel.FormElementModel;
import org.opendatakit.aggregate.format.RepeatCallbackFormatter;
import org.opendatakit.aggregate.format.Row;
import org.opendatakit.aggregate.servlet.BinaryDataServlet;
import org.opendatakit.aggregate.submission.SubmissionKey;
import org.opendatakit.aggregate.submission.SubmissionRepeat;
import org.opendatakit.aggregate.submission.type.BlobSubmissionType;
import org.opendatakit.aggregate.submission.type.GeoPoint;
import org.opendatakit.common.constants.BasicConsts;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;

/**
 * 
 * @author wbrunette@gmail.com
 * @author mitchellsundt@gmail.com
 * 
 */
public class KmlElementFormatter implements ElementFormatter {
   
  private String baseWebServerUrl;
  
  /**
   * include GPS accuracy data
   */
  private boolean includeAccuracy;
  
  RepeatCallbackFormatter callbackFormatter;
  
  /**
   * Construct a KML Element Formatter
   * @param webServerUrl base url for the web app (e.g., localhost:8080/ODKAggregatePlatform)
   * @param includeGpsAccuracy
   *          include GPS accuracy data
   */
  public KmlElementFormatter(String webServerUrl, boolean includeGpsAccuracy, RepeatCallbackFormatter formatter) {
    baseWebServerUrl = webServerUrl;
    includeAccuracy = includeGpsAccuracy;
    callbackFormatter = formatter;
  }
  
  @Override
  public void formatUid(String uri, String propertyName, Row row) {
    // unneeded so unimplemented
  }
  
  @Override
  public void formatBinary(BlobSubmissionType blobSubmission, String propertyName, Row row)
      throws ODKDatastoreException {
    if(blobSubmission == null || (blobSubmission.getAttachmentCount() == 0)) {
      row.addFormattedValue(null);
      return;
    }
    
    SubmissionKey key = blobSubmission.getValue();
    Map<String, String> properties = new HashMap<String, String>();
    properties.put(ServletConsts.BLOB_KEY, key.toString());
    String url = HtmlUtil.createHrefWithProperties(HtmlUtil.createUrl(baseWebServerUrl) + BinaryDataServlet.ADDR, properties, FormTableConsts.VIEW_LINK_TEXT);
    generateDataElement(url, propertyName, row);
  }

  @Override
  public void formatBoolean(Boolean bool, String propertyName, Row row) {
    generateDataElement(bool, propertyName, row);
  }

  @Override
  public void formatChoices(List<String> choices, String propertyName, Row row) {
	StringBuilder b = new StringBuilder();
	boolean first = true;
	for ( String s : choices ) {
		if ( !first ) {
			b.append(" ");
		}
		first = false;
		b.append(s);
	}
    generateDataElement(b.toString(), propertyName, row);
  }

  @Override
  public void formatDate(Date date, String propertyName, Row row) {
    generateDataElement(date, propertyName, row);
  }

  @Override
  public void formatDecimal(BigDecimal dub, String propertyName, Row row) {
    generateDataElement(dub, propertyName, row);
  }

  @Override
  public void formatGeoPoint(GeoPoint coordinate, String propertyName, Row row) {
    String preName = propertyName + FormatConsts.HEADER_CONCAT;
    generateDataElement(coordinate.getLatitude(), preName + GeoPoint.LATITUDE, row);
    generateDataElement(coordinate.getLongitude(), preName + GeoPoint.LONGITUDE, row);
    generateDataElement(coordinate.getAltitude(), preName + GeoPoint.ALTITUDE, row);

    if (includeAccuracy) {
      generateDataElement(coordinate.getAccuracy(), preName + GeoPoint.ACCURACY, row);
    }
    
  }

  @Override
  public void formatLong(Long longInt, String propertyName, Row row) {
    generateDataElement(longInt, propertyName, row);
  }

  @Override
  public void formatRepeats(SubmissionRepeat repeat, FormElementModel repeatElement, Row row)
      throws ODKDatastoreException {
    callbackFormatter.processRepeatedSubmssionSetsIntoRow(repeat.getSubmissionSets(), repeatElement, row);
  }

  @Override
  public void formatString(String string, String propertyName, Row row) {
    generateDataElement(string, propertyName, row);
  }

  private void generateDataElement(Object value, String name, Row row){
    String valueAsString = BasicConsts.EMPTY_STRING;
    if(value != null) {
      valueAsString = value.toString();
    }
    row.addFormattedValue(String.format(KmlConsts.KML_DATA_ITEM_TEMPLATE, name, valueAsString));
  }
    
}
