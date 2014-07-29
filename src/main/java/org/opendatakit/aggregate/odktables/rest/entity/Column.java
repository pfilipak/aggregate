/*
 * Copyright (C) 2012-2013 University of Washington
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

package org.opendatakit.aggregate.odktables.rest.entity;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The XML document that represents a column. This is the XML representation of
 * a column definition as stored in the (keep this fully qualified!)
 * {@link org.opendatakit.aggregate.odktables.relation.DbColumnDefinitions}
 * table.
 *
 * Removed all JAXB annotations -- these cause issues on Android 4.2 and earlier.
 *
 * @author dylan price?
 * @author sudar.sam@gmail.com
 *
 */
public class Column implements Serializable {

  /**
	 *
	 */
  private static final long serialVersionUID = -6624997293167731653L;

  /**
   * The fully qualified key for this element. If this is a retained field, then
   * this is the element's database column name. For composite types whose
   * elements are individually retained (e.g., geopoint), this would be the
   * elementName of the geopoint (e.g., 'myLocation' concatenated with '_' and
   * this elementName (e.g., 'myLocation_latitude').
   */
  private String elementKey;

  /**
   * The name by which this element is referred. For composite types whose
   * elements are individually retained (e.g., geopoint), this would be simply
   * 'latitude'
   */
  @JsonProperty(required = false)
  private String elementName;

  /**
   * This must be a name() of one of Tables's ColumnTypes.
   */
  private String elementType;

  @JsonProperty(required = false)
  private String listChildElementKeys;

  @SuppressWarnings("unused")
  private Column() {
  }

  /**
   * Create a column. NB: It needs to be decided if backing name and display
   * name are different in the datastore on the server in the same way they are
   * on the phone, and if they should both be stored in the COLUMN table as
   * adjacent columns, or what exactly. Either way, its implementation should be
   * brought into alignment with ColumnClient, which has both display and
   * backing names when the answer to the above questions is decided.
   *
   * @param tableId
   * @param elementKey
   * @param elementName
   * @param elementType
   * @param listChildElementKeys
   * @param isUnitOfRetention
   */
  public Column(final String elementKey, final String elementName,
      final String elementType, final String listChildElementKeys) {
    this.elementKey = elementKey;
    this.elementName = elementName;
    this.elementType = elementType;
    this.listChildElementKeys = listChildElementKeys;
  }

  public String getElementKey() {
    return this.elementKey;
  }

  public String getElementName() {
    return this.elementName;
  }

  /**
   * Returns the string name of an ODKTables column type.
   */
  public String getElementType() {
    return this.elementType;
  }

  public String getListChildElementKeys() {
    return this.listChildElementKeys;
  }

  @JsonIgnore
  public boolean isUnitOfRetention() {
    String listChild = getListChildElementKeys();
    String type = getElementType();
    if ( "array".equals(type) ) {
      return true;
    }
    if ( listChild == null || listChild.length() == 0 || "[]".equals(listChild) ) {
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return "Column(elementKey=" + this.getElementKey()
        + ", elementName=" + this.getElementName() + ", elementType= " + this.getElementType()
        + ", listChildElementKeys=" + this.getListChildElementKeys() + ")";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((elementKey == null) ? 0 : elementKey.hashCode());
    result = prime * result + ((elementName == null) ? 0 : elementName.hashCode());
    result = prime * result + ((elementType == null) ? 0 : elementType.hashCode());
    result = prime * result
        + ((listChildElementKeys == null) ? 0 : listChildElementKeys.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof Column)) {
      return false;
    }
    Column other = (Column) obj;
    return (elementKey == null ? other.elementKey == null : elementKey.equals(other.elementKey))
        && (elementName == null ? other.elementName == null : elementName.equals(other.elementName))
        && (elementType == null ? other.elementType == null : elementType.equals(other.elementType))
        && (listChildElementKeys == null ? other.listChildElementKeys == null
            : listChildElementKeys.equals(other.listChildElementKeys));
  }
}
