/*
 * Copyright (C) 2013 University of Washington
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

package org.opendatakit.aggregate.client.odktables;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * This object represents a Row on the client side of the code. It is based
 * almost entirely from a copy/paste of
 * org.opendatakit.aggregate.odktables.entity.Row.java
 * <p>
 * It is the client-side analogue of that Row object.
 * @author sudar.sam@gmail.com
 *
 */
public class RowClient implements Serializable {

  /**
	 *
	 */
	private static final long serialVersionUID = -3396839962551194663L;

  private String rowId;

  private String rowEtag;

  private boolean deleted;

  private String createUser;

  private String lastUpdateUser;

  private ScopeClient filterScope;

  private String uriUser;

  private String formId;

  private String instanceName;

  private String locale;

  private String timestamp;

  private Map<String, String> values;

  /**
   * Construct a row for insertion.
   *
   * @param rowId
   * @param values
   */
  public static RowClient forInsert(String rowId, Map<String, String> values) {
    RowClient row = new RowClient();
    row.rowId = rowId;
    row.values = values;
    row.filterScope = ScopeClient.EMPTY_SCOPE;
    return row;
  }

  /**
   * Construct a row for updating.
   *
   * @param rowId
   * @param rowEtag
   * @param values
   */
  public static RowClient forUpdate(String rowId, String rowEtag,
      Map<String, String> values) {
    RowClient row = new RowClient();
    row.rowId = rowId;
    row.rowEtag = rowEtag;
    row.values = values;
    return row;
  }

  public RowClient() {
    this.rowId = null;
    this.rowEtag = null;
    this.deleted = false;
    this.createUser = null;
    this.lastUpdateUser = null;
    this.filterScope = null;
    this.values = new HashMap<String, String>();
    this.uriUser = null;
    this.formId = null;
    this.instanceName = null;
    this.locale = null;
    this.timestamp = null;
  }

  public String getRowId() {
    return this.rowId;
  }

  public String getRowEtag() {
    return this.rowEtag;
  }

  public boolean isDeleted() {
    return this.deleted;
  }

  public String getCreateUser() {
    return createUser;
  }

  public String getLastUpdateUser() {
    return lastUpdateUser;
  }

  public ScopeClient getFilterScope() {
    return filterScope;
  }

  public Map<String, String> getValues() {
    return this.values;
  }

  public void setRowId(final String rowId) {
    this.rowId = rowId;
  }

  public void setRowEtag(final String rowEtag) {
    this.rowEtag = rowEtag;
  }

  public void setDeleted(final boolean deleted) {
    this.deleted = deleted;
  }

  public void setCreateUser(String createUser) {
    this.createUser = createUser;
  }

  public void setLastUpdateUser(String lastUpdateUser) {
    this.lastUpdateUser = lastUpdateUser;
  }

  public void setFilterScope(ScopeClient filterScope) {
    this.filterScope = filterScope;
  }

  public void setValues(final Map<String, String> values) {
    this.values = values;
  }

  public String getUriUser() {
    return this.uriUser;
  }

  public String getFormId() {
    return this.formId;
  }

  public String getInstanceName() {
    return this.instanceName;
  }

  public String getLocale() {
    return this.locale;
  }

  public String getTimestamp() {
    return this.timestamp;
  }

  public void setUriUser(String uriUser) {
    this.uriUser = uriUser;
  }

  public void setFormId(String formId) {
    this.formId = formId;
  }

  public void setInstanceName(String instanceName) {
    this.instanceName = instanceName;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  /**
   * Expects a string as generated by {@link WebUtils#iso8601Date(Date)}.
   * @param timestamp
   */
  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((createUser == null) ? 0 : createUser.hashCode());
    result = prime * result + (deleted ? 1231 : 1237);
    result = prime * result + ((filterScope == null) ? 0 : filterScope.hashCode());
    result = prime * result + ((lastUpdateUser == null) ? 0 : lastUpdateUser.hashCode());
    result = prime * result + ((rowEtag == null) ? 0 : rowEtag.hashCode());
    result = prime * result + ((rowId == null) ? 0 : rowId.hashCode());
    result = prime * result + ((values == null) ? 0 : values.hashCode());
    return result;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (!(obj instanceof RowClient))
      return false;
    RowClient other = (RowClient) obj;
    if (createUser == null) {
      if (other.createUser != null)
        return false;
    } else if (!createUser.equals(other.createUser))
      return false;
    if (deleted != other.deleted)
      return false;
    if (filterScope == null) {
      if (other.filterScope != null)
        return false;
    } else if (!filterScope.equals(other.filterScope))
      return false;
    if (lastUpdateUser == null) {
      if (other.lastUpdateUser != null)
        return false;
    } else if (!lastUpdateUser.equals(other.lastUpdateUser))
      return false;
    if (rowEtag == null) {
      if (other.rowEtag != null)
        return false;
    } else if (!rowEtag.equals(other.rowEtag))
      return false;
    if (rowId == null) {
      if (other.rowId != null)
        return false;
    } else if (!rowId.equals(other.rowId))
      return false;
    if (values == null) {
      if (other.values != null)
        return false;
    } else if (!values.equals(other.values))
      return false;
    return true;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Row [rowId=");
    builder.append(rowId);
    builder.append(", rowEtag=");
    builder.append(rowEtag);
    builder.append(", deleted=");
    builder.append(deleted);
    builder.append(", createUser=");
    builder.append(createUser);
    builder.append(", lastUpdateUser=");
    builder.append(lastUpdateUser);
    builder.append(", filterScope=");
    builder.append(filterScope);
    builder.append(", values=");
    builder.append(values);
    builder.append("]");
    return builder.toString();
  }
}