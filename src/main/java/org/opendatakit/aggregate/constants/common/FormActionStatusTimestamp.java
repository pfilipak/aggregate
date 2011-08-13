/*
 * Copyright (C) 2011 University of Washington
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

package org.opendatakit.aggregate.constants.common;

import java.io.Serializable;
import java.util.Date;

public class FormActionStatusTimestamp implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6347744032355543871L;
	
	private FormActionStatus status;
	private Date timestamp;
	
	public FormActionStatusTimestamp() {
	}

	public FormActionStatusTimestamp(FormActionStatus status, Date timestamp) {
		this.status = status;
		this.timestamp = timestamp;
	}

	public FormActionStatus getStatus() {
		return status;
	}

	public void setStatus(FormActionStatus status) {
		this.status = status;
	}
	
	public Date getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}
}
