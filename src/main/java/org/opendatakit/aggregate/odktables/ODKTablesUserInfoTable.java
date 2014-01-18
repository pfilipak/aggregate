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

package org.opendatakit.aggregate.odktables;

import java.util.List;
import java.util.Set;

import org.opendatakit.common.persistence.CommonFieldsBase;
import org.opendatakit.common.persistence.DataField;
import org.opendatakit.common.persistence.Datastore;
import org.opendatakit.common.persistence.Query;
import org.opendatakit.common.persistence.Query.FilterOperation;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.persistence.exception.ODKEntityPersistException;
import org.opendatakit.common.persistence.exception.ODKOverQuotaException;
import org.opendatakit.common.security.SecurityUtils;
import org.opendatakit.common.security.User;
import org.opendatakit.common.security.common.GrantedAuthorityName;
import org.opendatakit.common.security.spring.RegisteredUsersTable;
import org.opendatakit.common.web.CallingContext;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * This table holds the ODK Tables-specific settings for a user.
 * <p>
 * In particular, it holds the mapping between the internal ODK Aggregate
 * uriUser and the external ODK Tables-specific USER_ID_EXTERNAL
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class OdkTablesUserInfoTable extends CommonFieldsBase {

  /**
   * The name of the table into which this data is persisted.
   */
  private static final String TABLE_NAME = "_odktables_user_info";

  /**
   * URI_USER is the PK of the user in the RegisteredUsersTable
   */
  private static final DataField URI_USER = new DataField("URI_USER", DataField.DataType.STRING,
      true);

  /**
   * This is the either the user's username (with username: prefix) or email
   * address (with mailto: prefix). (whichever is not null) It is configured as
   * the synchronizing account on the device (devices never see the internal URI
   * (PK) held in ODK Aggregate).
   */

  private static final DataField ODK_TABLES_USER_ID = new DataField("ODK_TABLES_USER_ID",
      DataField.DataType.STRING, true);

  /**
   * This is the phone number of for user's phone.
   */

  private static final DataField PHONE_NUMBER = new DataField("PHONE_NUMBER",
      DataField.DataType.STRING, true);

  /**
   * Additional bearer code that should be sent on authentication line to
   * confirm that the user is still allowed to access the server. This enables
   * alternative authentication for email accounts when the internet is not
   * reachable.
   *
   * TODO: wire this up
   */
  private static final DataField X_BEARER_CODE = new DataField("X_BEARER_CODE",
      DataField.DataType.STRING, true);

  /**
   * TODO: permissions and permission groups granted to this user
   */

  /**
   * Construct a relation prototype. It will load a table into the data store
   * layer.
   *
   * @param databaseSchema
   * @param tableName
   */
  private OdkTablesUserInfoTable(String schemaName) {
    super(schemaName, TABLE_NAME);
    fieldList.add(URI_USER);
    fieldList.add(ODK_TABLES_USER_ID);
    fieldList.add(PHONE_NUMBER);
    fieldList.add(X_BEARER_CODE);
  }

  /**
   * Construct an empty entity. Only called via {@link #getEmptyRow(User)}
   *
   * @param ref
   * @param user
   */
  private OdkTablesUserInfoTable(OdkTablesUserInfoTable ref, User user) {
    super(ref, user);
  }

  /**
   * I'm pretty sure this is returning the prototype, or the empty row.
   *
   */
  @Override
  public CommonFieldsBase getEmptyRow(User user) {
    return new OdkTablesUserInfoTable(this, user);
  }

  /**
   * I copied this from ServerPreferences. I believe this is how you actually
   * add the data into the table, making it "persist."
   *
   * @param cc
   *          so you have information about the call
   * @throws ODKEntityPersistException
   * @throws ODKOverQuotaException
   */
  public void persist(CallingContext cc) throws ODKEntityPersistException, ODKOverQuotaException {
    Datastore ds = cc.getDatastore();
    User user = cc.getCurrentUser();

    ds.putEntity(this, user);
  }

  /**
   * This is the actual prototype. This is the canonical empty row for this
   * table that essentially serves as the schema. Therefore the table is nothing
   * without this relation being initiated.
   *
   * For that reason, it is important to always access the prototype by calling
   * assertRelation() before trying to manipulate the table. Otherwise you might
   * end up with a table that is empty and shapeless.
   */
  private static OdkTablesUserInfoTable relation = null;

  /**
   * This must be called to ensure that the datamodel for the table has been
   * initiated.
   *
   * @param cc
   *          calling context that allows the datastore and user to be
   *          determined
   * @return the prototype, eg the canonical empty row for the table
   * @throws ODKDatastoreException
   */
  public static synchronized final OdkTablesUserInfoTable assertRelation(CallingContext cc)
      throws ODKDatastoreException {
    if (relation == null) {
      OdkTablesUserInfoTable relationPrototype;
      Datastore ds = cc.getDatastore();
      User user = cc.getUserService().getDaemonAccountUser();
      relationPrototype = new OdkTablesUserInfoTable(ds.getDefaultSchemaName());
      ds.assertRelation(relationPrototype, user); // may throw exception...
      // at this point, the prototype has become fully populated
      relation = relationPrototype; // set static variable only upon success...
    }
    return relation;
  }

  public static final OdkTablesUserInfoTable getUserData(String uriUser, CallingContext cc)
      throws ODKDatastoreException {
    OdkTablesUserInfoTable prototype = OdkTablesUserInfoTable.assertRelation(cc);
    Datastore ds = cc.getDatastore();
    // query for the users
    Query query = ds.createQuery(prototype, "OdkTablesUserInfoTable.getUserData",
        cc.getCurrentUser());
    query.addFilter(URI_USER, FilterOperation.EQUAL, uriUser);
    List<? extends CommonFieldsBase> results = query.executeQuery();

    if (results.size() == 0) {
      User user = cc.getCurrentUser();
      if (user.getUriUser().equals(uriUser)) {
        // we can add this user silently if they have permissions...
        Set<GrantedAuthority> auths = user.getGroups();
        if (auths.contains(new SimpleGrantedAuthority(GrantedAuthorityName.ROLE_SYNCHRONIZE_TABLES.name()))
            || auths.contains(new SimpleGrantedAuthority(GrantedAuthorityName.ROLE_ADMINISTER_TABLES.name())) ) {
          // create a record
          OdkTablesUserInfoTable odkTablesUserInfo = ds.createEntityUsingRelation(prototype,
              cc.getCurrentUser());
          RegisteredUsersTable t;
          t = RegisteredUsersTable.getUserByUri(user.getUriUser(), ds, user);
          odkTablesUserInfo.setUriUser(user.getUriUser());
          String externalUID = null;
          if (user.getEmail() != null) {
            externalUID = user.getEmail();
          } else if (t.getUsername() != null) {
            externalUID = SecurityUtils.USERNAME_COLON + t.getUsername();
          }
          odkTablesUserInfo.setOdkTablesUserId(externalUID);
          odkTablesUserInfo.persist(cc);
          return odkTablesUserInfo;
        }
      }
      return null;
    }
    if (results.size() == 1) {
      return (OdkTablesUserInfoTable) results.get(0);
    }

    throw new ODKDatastoreException("Unexpected state: " + results.size()
        + " OdkTablesUserInfoTable records matching " + uriUser);
  }

  /**
   * Get the aggregate userid.
   */
  public String getUriUser() {
    return getStringField(URI_USER);
  }

  /**
   * Get the external userid
   */
  public String getOdkTablesUserId() {
    return getStringField(ODK_TABLES_USER_ID);
  }

  /**
   * Get the Phone Number
   */
  public String getPhoneNumber() {
    return getStringField(PHONE_NUMBER);
  }

  /**
   * Get the X-Bearer-Code
   */
  public String getXBearerCode() {
    return getStringField(X_BEARER_CODE);
  }

  /**
   * Set the uriUser.
   *
   * @ throws IllegalArgumentException if the value cannot be set
   */
  public void setUriUser(String uriUser) {
    if (!setStringField(URI_USER, uriUser)) {
      throw new IllegalArgumentException("overflow uriUser");
    }
  }

  /**
   * Set the ODK Tables user id.
   *
   * @throws IllegalArgumentException
   *           if the value cannot be set, most likely due to overflow.
   */
  public void setOdkTablesUserId(String odkTablesUserId) {
    if (!(odkTablesUserId.startsWith(SecurityUtils.MAILTO_COLON) || odkTablesUserId
        .startsWith(SecurityUtils.USERNAME_COLON))) {
      throw new IllegalArgumentException("ODK Tables User Id does not start with "
          + SecurityUtils.MAILTO_COLON + " or " + SecurityUtils.USERNAME_COLON);
    }
    if (!setStringField(ODK_TABLES_USER_ID, odkTablesUserId)) {
      throw new IllegalArgumentException("overflow external odkTablesUserId");
    }
  }

  /**
   * Set the Phone Number
   *
   * @ throws IllegalArgumentException if the value cannot be set
   */
  public void setPhoneNumber(String phoneNumber) {
    if (!setStringField(PHONE_NUMBER, phoneNumber)) {
      throw new IllegalArgumentException("overflow phoneNumber");
    }
  }

  /**
   * Set the X-Bearer-Code
   *
   * @ throws IllegalArgumentException if the value cannot be set
   */
  public void setXBearerCode(String xBearerCode) {
    if (!setStringField(X_BEARER_CODE, xBearerCode)) {
      throw new IllegalArgumentException("overflow XBearerCode");
    }
  }

}
