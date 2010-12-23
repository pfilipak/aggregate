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

package org.opendatakit.aggregate.submission;

import org.opendatakit.aggregate.exception.ODKConversionException;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;

/**
 * Interface for submission field that can be used to store
 * a submission field in the datastore 
 *
 * @author wbrunette@gmail.com
 * 
 * @param <T>
 *  a GAE datastore type
 * @author mitchellsundt@gmail.com
 * 
 */
public interface SubmissionField<T> extends SubmissionValue{
  
	public enum BlobSubmissionOutcome {
		FILE_UNCHANGED,
		NEW_FILE_VERSION,
		COMPLETELY_NEW_FILE
	};
	
  /**
   * Returns whether submission type is constructed from a binary object
   * 
   * @return
   *    true if should be constructed with byte array, false otherwise
   */
  public boolean isBinary();
  
  /**
   * Get the value of submission field
   * 
   * @return
   *    value
   */
  public T getValue();

  /**
   * Parse the value from string format and convert to proper type for
   * submission field
   * 
   * @param value string form of the value
   * @throws ODKConversionException
 * @throws ODKDatastoreException 
   */
  public void setValueFromString(String value) throws ODKConversionException, ODKDatastoreException;

  /**
   * Store the given blob into the submission field.
   * 
   * @param byteArray - data to be stored
   * @param contentType
   * @param contentLength
   * @param unrootedFilePath
   * @return outcome of storage attempt
   * @throws ODKDatastoreException
   */
  public BlobSubmissionOutcome setValueFromByteArray(byte[] byteArray,
		String contentType, Long contentLength, String unrootedFilePath)
		throws ODKDatastoreException;
  
}
