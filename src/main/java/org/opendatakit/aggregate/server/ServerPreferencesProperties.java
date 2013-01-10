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

package org.opendatakit.aggregate.server;

import java.util.List;

import org.opendatakit.aggregate.client.preferences.PreferenceSummary;
import org.opendatakit.common.persistence.CommonFieldsBase;
import org.opendatakit.common.persistence.DataField;
import org.opendatakit.common.persistence.Datastore;
import org.opendatakit.common.persistence.Query;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.persistence.exception.ODKEntityNotFoundException;
import org.opendatakit.common.persistence.exception.ODKEntityPersistException;
import org.opendatakit.common.persistence.exception.ODKOverQuotaException;
import org.opendatakit.common.security.User;
import org.opendatakit.common.web.CallingContext;

public class ServerPreferencesProperties extends CommonFieldsBase {

  private static final String TABLE_NAME = "_server_preferences_properties";

  private static final DataField KEY = new DataField("KEY",
      DataField.DataType.STRING, true, 128L);

  private static final DataField VALUE = new DataField("VALUE",
      DataField.DataType.STRING, true, 40960L);

  // these values are set in the ServiceAccountPrivateKeyUploadServlet
  // and used everywhere else when requesting access
  public static final String GOOGLE_API_CLIENT_ID = "GOOGLE_CLIENT_ID";
  public static final String GOOGLE_API_SERVICE_ACCOUNT_EMAIL = "GOOGLE_SERVICE_ACCOUNT_EMAIL";
  public static final String PRIVATE_KEY_FILE_CONTENTS = "PRIVATE_KEY_FILE_CONTENTS";

  // other keys...
  private static final String SITE_KEY = "SITE_KEY";
  private static final String GOOGLE_FUSION_TABLE_KEY = "GOOG_FUSION_TABLES_OAUTH2_KEY";
  private static final String GOOGLE_SPREADSHEET_TABLE_KEY = "GOOG_SPREADSHEET_OAUTH2_KEY";
  private static final String GOOGLE_MAP_KEY = "GOOG_MAPS_API_KEY";
  private static final String GOOGLE_API_CLIENT_SECRET = "GOOGLE_CLIENT_SECRET";
  public static final String GOOGLE_API_OAUTH2_CODE = "GOOGLE_OAUTH2_CODE";
  public static final String GOOGLE_API_OAUTH2_ACCESS_TOKEN = "GOOGLE_OAUTH2_ACCESS_TOKEN";
  public static final String GOOGLE_API_OAUTH2_REFRESH_TOKEN = "GOOGLE_OAUTH2_REFRESH_TOKEN";
  private static final String ODK_TABLES_ENABLED = "ODK_TABLES_ENABLED";

  /**
   * Construct a relation prototype.
   * 
   * @param databaseSchema
   * @param tableName
   */
  private ServerPreferencesProperties(String schemaName) {
    super(schemaName, TABLE_NAME);
    fieldList.add(KEY);
    fieldList.add(VALUE);
  }

  public static PreferenceSummary getPreferenceSummary(CallingContext cc) throws ODKEntityNotFoundException, ODKOverQuotaException {
    return new PreferenceSummary(getGoogleMapApiKey(cc), getGoogleApiClientId(cc), getOdkTablesEnabled(cc));
  }

  /**
   * Construct an empty entity. Only called via {@link #getEmptyRow(User)}
   * 
   * @param ref
   * @param user
   */
  private ServerPreferencesProperties(ServerPreferencesProperties ref, User user) {
    super(ref, user);
  }

  @Override
  public ServerPreferencesProperties getEmptyRow(User user) {
    return new ServerPreferencesProperties(this, user);
  }

  public static String getSiteKey(CallingContext cc) throws ODKEntityNotFoundException, ODKOverQuotaException {
    String value = getServerPreferencesProperty(cc, SITE_KEY);
    if ( value == null ) {
      // synthesize a new one...
      value = CommonFieldsBase.newUri();
      setServerPreferencesProperty(cc, SITE_KEY, value);
    }
    return value;
  }

  public static void setSiteKey(CallingContext cc, String siteKey) throws ODKEntityNotFoundException, ODKOverQuotaException {
    setServerPreferencesProperty(cc, SITE_KEY, siteKey);
  }

  public static String getGoogleMapApiKey(CallingContext cc) throws ODKEntityNotFoundException, ODKOverQuotaException {
    String value = getServerPreferencesProperty(cc, GOOGLE_MAP_KEY);
    return value;
  }

  public static void setGoogleMapApiKey(CallingContext cc, String googleMapsApiKey) throws ODKEntityNotFoundException, ODKOverQuotaException {
    setServerPreferencesProperty(cc, GOOGLE_MAP_KEY, googleMapsApiKey);
  }

  public static String getGoogleApiClientId(CallingContext cc) throws ODKEntityNotFoundException, ODKOverQuotaException {
    String value = getServerPreferencesProperty(cc, GOOGLE_API_CLIENT_ID);
    return value;
  }

  public static String getGoogleApiClientSecret(CallingContext cc) throws ODKEntityNotFoundException, ODKOverQuotaException {
    String value = getServerPreferencesProperty(cc, GOOGLE_API_CLIENT_SECRET);
    return value;
  }
  
  public static void setGoogleApiClientCredentials(CallingContext cc, String googleClientId, String googleClientSecret) throws ODKEntityNotFoundException, ODKOverQuotaException {
    setServerPreferencesProperty(cc, GOOGLE_API_CLIENT_SECRET, googleClientSecret);
    setServerPreferencesProperty(cc, GOOGLE_API_CLIENT_ID, googleClientId);
  }
  
  public static String getGoogleFusionTableOauthKey(CallingContext cc) throws ODKEntityNotFoundException, ODKOverQuotaException {
    String value = getServerPreferencesProperty(cc, GOOGLE_FUSION_TABLE_KEY);
    return value;
  }

  public static void setGoogleFusionTableOauthKey(CallingContext cc, String googleFusionTableOauthKey) throws ODKEntityNotFoundException, ODKOverQuotaException {
    setServerPreferencesProperty(cc, GOOGLE_FUSION_TABLE_KEY, googleFusionTableOauthKey);
  }

  public static Boolean getOdkTablesEnabled(CallingContext cc) throws ODKEntityNotFoundException, ODKOverQuotaException {
    String value = getServerPreferencesProperty(cc, ODK_TABLES_ENABLED);
    if ( value != null ) {
      return Boolean.valueOf(value);
    }
    // null value should be treated as false
    return false;
  }

  public static void setOdkTablesEnabled(CallingContext cc, Boolean enabled) throws ODKEntityNotFoundException, ODKOverQuotaException {
    setServerPreferencesProperty(cc, ODK_TABLES_ENABLED, enabled.toString());
  }

  public void persist(CallingContext cc) throws ODKEntityPersistException, ODKOverQuotaException {
    Datastore ds = cc.getDatastore();
    User user = cc.getCurrentUser();

    ds.putEntity(this, user);
  }

  private static ServerPreferencesProperties relation = null;

  public static synchronized final ServerPreferencesProperties assertRelation(CallingContext cc)
      throws ODKDatastoreException {
    if (relation == null) {
      ServerPreferencesProperties relationPrototype;
      Datastore ds = cc.getDatastore();
      User user = cc.getUserService().getDaemonAccountUser();
      relationPrototype = new ServerPreferencesProperties(ds.getDefaultSchemaName());
      ds.assertRelation(relationPrototype, user); // may throw exception...
      // at this point, the prototype has become fully populated
      relation = relationPrototype; // set static variable only upon success...
    }
    return relation;
  }

  public static final String getServerPreferencesProperty(CallingContext cc, String keyName)
      throws ODKEntityNotFoundException, ODKOverQuotaException {
    try {
      ServerPreferencesProperties relation = assertRelation(cc);
      Query query = cc.getDatastore().createQuery(relation, "ServerPreferences.getServerPreferences", cc.getCurrentUser());
      query.addFilter(KEY, Query.FilterOperation.EQUAL, keyName);
      // don't care about duplicate entries because we always access the most recent first
      query.addSort(relation.lastUpdateDate, Query.Direction.DESCENDING);
      List<? extends CommonFieldsBase> results = query.executeQuery();
      if (!results.isEmpty()) {
        if (results.get(0) instanceof ServerPreferencesProperties) {
          ServerPreferencesProperties preferences = (ServerPreferencesProperties) results.get(0);
          return preferences.getStringField(VALUE);
        }
      }
      return null;
    } catch (ODKOverQuotaException e) {
      throw e;
    } catch (ODKDatastoreException e) {
      throw new ODKEntityNotFoundException(e);
    }
  }

  public static final void setServerPreferencesProperty(CallingContext cc, String keyName, String value)
      throws ODKEntityNotFoundException, ODKOverQuotaException {
    try {
      ServerPreferencesProperties relation = assertRelation(cc);
      Query query = cc.getDatastore().createQuery(relation, "ServerPreferences.getServerPreferences", cc.getCurrentUser());
      query.addFilter(KEY, Query.FilterOperation.EQUAL, keyName);
      List<? extends CommonFieldsBase> results = query.executeQuery();
      if (!results.isEmpty()) {
        if (results.get(0) instanceof ServerPreferencesProperties) {
          ServerPreferencesProperties preferences = (ServerPreferencesProperties) results.get(0);
          if ( !preferences.setStringField(VALUE, value) ) {
            throw new IllegalStateException("Unexpected truncation of ServerPreferencesProperty: " + keyName + " value");
          }
          preferences.persist(cc);
          return;
        }
        throw new IllegalStateException("Expected ServerPreferencesProperties entity");
      }
      // nothing there -- put the value...
      ServerPreferencesProperties preferences = cc.getDatastore().createEntityUsingRelation(relation,
                                                                          cc.getCurrentUser());
      if ( !preferences.setStringField(KEY, keyName) ) {
        throw new IllegalStateException("Unexpected truncation of ServerPreferencesProperty: " + keyName + " keyName");
      }
      if ( !preferences.setStringField(VALUE, value) ) {
        throw new IllegalStateException("Unexpected truncation of ServerPreferencesProperty: " + keyName + " value");
      }
      preferences.persist(cc);
      return;
    } catch (ODKOverQuotaException e) {
      throw e;
    } catch (ODKDatastoreException e) {
      throw new ODKEntityNotFoundException(e);
    }
  }
}
