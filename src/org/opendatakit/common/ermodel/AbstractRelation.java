/**
 * Copyright (C) 2011 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.common.ermodel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opendatakit.common.persistence.CommonFieldsBase;
import org.opendatakit.common.persistence.DataField;
import org.opendatakit.common.persistence.Datastore;
import org.opendatakit.common.persistence.EntityKey;
import org.opendatakit.common.persistence.Query;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.persistence.exception.ODKEntityNotFoundException;
import org.opendatakit.common.persistence.exception.ODKEntityPersistException;
import org.opendatakit.common.security.User;
import org.opendatakit.common.web.CallingContext;

/**
 * Base class for user-defined relations.  The constructors assume that the
 * name of the table is UPPER_CASE only, as are the names of the DataFields
 * in the relation.
 * 
 * @author mitchellsundt@gmail.com
 *
 */
public class AbstractRelation implements Relation {
	
	/**
	 * Useful static method for constructing a persistence layer name
	 * from a camelCase name. This inserts an underscore
	 * before a leading capital letter and toUpper()s the
	 * resulting string.
	 * <ul><li>thisURL => THIS_URL</li>
	 * <li>myFirstObject => MY_FIRST_OBJECT</li></ul>
	 * 
	 * @param name
	 * @return
	 */
	public static final String unCamelCase(String name) {
		StringBuilder b = new StringBuilder();
		boolean lastCap = true;
		for ( int i = 0 ; i < name.length() ; ++i ) {
			char ch = name.charAt(i);
			if ( Character.isUpperCase(ch) ) {
				if ( !lastCap ) {
					b.append('_');
				}
				lastCap = true;
				b.append(ch);
			} else {
				lastCap = false;
				b.append(Character.toUpperCase(ch));
			}
		}
		return b.toString();
	}
	
	/**
	 * Standard constructor.  Use for tables your application knows about and
	 * manipulates directly.  
	 * 
	 * @param tableName must be UPPER_CASE beginning with an upper case letter.  The actual
	 * table name in the datastore will have 3 leading underscores.
	 * @param fields
	 * @param cc
	 * @throws ODKDatastoreException
	 */
	protected AbstractRelation( String tableName, List<DataField> fields, CallingContext cc) throws ODKDatastoreException {
		if ( !tableName.matches(Relation.VALID_UPPER_CASE_NAME_REGEX) || tableName.contains("__") || tableName.startsWith("_") ) {
			throw new IllegalArgumentException("Expected an UPPER_CASE table name beginning with an upper case letter.");
		}
		this.backingTableName = "___" + tableName;
		if ( backingTableName.length() > Relation.MAX_PERSISTENCE_NAME_LENGTH ) {
			throw new IllegalArgumentException("Backing table name is too long: " + backingTableName);
		}
		this.namespace = TableNamespace.EXTENSION;
		initialize(fields, cc);
	}

	/**
	 * Use this constructor to place tableNames in a new namespace.
	 * This is useful if you are dynamically creating tables.  It 
	 * allows those tables to be in a different namespace from the 
	 * tables your app uses to keep track of everything.  Aggregate,
	 * for example, ensures that submission tables start with an
	 * alphabetic character, and that internal tracking tables start 
	 * with a leading underscore ('_').
	 * 
	 * TableNames cannot collide if their namespaces are different.
	 * Namespaces should be short 2-4 character prefixes.  The overall
	 * length of the table names in the database are limited to 
	 * about 64 characters, so you want to use short names.
	 * 
	 * @param namespace must be UPPER_CASE beginning with an upper case letter.
	 * @param tableName must be UPPER_CASE beginning with an upper case letter.  The actual
	 * table name in the datastore will be composed of 2 leading underscores, 
	 * the namespace string, 2 underscores, and this tableName string.
	 * @param fields
	 * @param cc
	 * @throws ODKDatastoreException
	 */
	protected AbstractRelation(String namespace, String tableName, List<DataField> fields, CallingContext cc) throws ODKDatastoreException {
		if ( !namespace.matches(Relation.VALID_UPPER_CASE_NAME_REGEX) || namespace.contains("__") || namespace.startsWith("_") ) {
			throw new IllegalArgumentException("Expected an UPPER_CASE namespace name beginning with an upper case letter.");
		}
		if ( !tableName.matches(Relation.VALID_UPPER_CASE_NAME_REGEX) || tableName.contains("__") || tableName.startsWith("_") ) {
			throw new IllegalArgumentException("Expected an UPPER_CASE table name beginning with an upper case letter.");
		}
		this.backingTableName = "__" + namespace + "__" + tableName;
		if ( backingTableName.length() > Relation.MAX_PERSISTENCE_NAME_LENGTH ) {
			throw new IllegalArgumentException("Backing table name is too long: " + backingTableName);
		}
		this.namespace = TableNamespace.EXTENSION;
		initialize(fields, cc);
	}

	/**
	 * This is primarily for accessing the existing tables of form submissions or 
	 * the Aggregate internal data model.  If you aren't accessing those, you 
	 * should not be using this constructor.
	 * 
	 * @param type
	 * @param tableName
	 * @param fields
	 * @param cc
	 * @throws ODKDatastoreException 
	 */
	protected AbstractRelation( TableNamespace type, String tableName, List<DataField> fields, CallingContext cc) throws ODKDatastoreException {
		Datastore ds = cc.getDatastore();
		User user = cc.getCurrentUser();

		if ( !tableName.matches(Relation.VALID_UPPER_CASE_NAME_REGEX) ) {
			throw new IllegalArgumentException("Expected an UPPER_CASE table name.");
		}

		if ( tableName.length() > Relation.MAX_PERSISTENCE_NAME_LENGTH ) {
			throw new IllegalArgumentException("Backing table name is too long: " + tableName);
		}
		
		switch ( type ) {
		case SUBMISSIONS:
			// submissions tables never start with a leading underscore.
			if ( tableName.charAt(0) == '_' ) {
				throw new IllegalArgumentException("Invalid Table namespace for tableName: " + tableName);
			}
			backingTableName = tableName;
			namespace = TableNamespace.SUBMISSIONS;
			// don't proceed if the table doesn't exist
			if ( !ds.hasRelation(ds.getDefaultSchemaName(), tableName, user)) {
				throw new IllegalArgumentException("Submissions table does not exist");
			}
			break;
		case INTERNALS:
			// internal tables to Aggregate start with an underscore 
			// followed by an alphanumeric character.
			if ( tableName.charAt(0) != '_' ||
				 tableName.charAt(1) == '_' ) {
				throw new IllegalArgumentException("Invalid Table namespace for tableName: " + tableName);
			}
			backingTableName = tableName;
			namespace = TableNamespace.INTERNALS;
			// don't proceed if the table doesn't exist
			if ( !ds.hasRelation(ds.getDefaultSchemaName(), tableName, user)) {
				throw new IllegalArgumentException("Submissions table does not exist");
			}
			break;
		case EXTENSION:
			// extensions start with at least two underscores...
			if ( tableName.charAt(0) != '_' ||
				 tableName.charAt(1) != '_' ) {
				throw new IllegalArgumentException("Invalid Table namespace for tableName: " + tableName);
			}
			backingTableName = tableName;
			namespace = TableNamespace.EXTENSION;
			break;
		default:
			throw new IllegalStateException("Unexpected TableNamespace value");
		}
		initialize(fields, cc);
	}
	
	/**
	 * Create a new entity.  This entity does not exist in the database
	 * until you put() it there.
	 * 
	 * @param cc
	 * @return
	 */
	public Entity newEntity(CallingContext cc) {
		Datastore ds = cc.getDatastore();
		User user = cc.getCurrentUser();
		
		return new EntityImpl(ds.createEntityUsingRelation(prototype, user));
	}
	
	/**
	 * Create a new entity.  This entity does not exist in the database
	 * until you put() it there.
	 * 
	 * @param uri  the primary key for this new entity.  The key must be 
	 *        a string less than 80 characters long.  It should be in a
	 *        URI-style format -- meaning that it has a namespace identifier
	 *        followed by a colon, followed by a string in that namespace.
	 *        The default is a uri in the UUID namespace.  You can construct
	 *        one of these UUID uris using CommonFieldsBase.newUri().
	 *        
	 *        Those are of the form:
	 *          "uuid:371adf05-3cea-4e11-b56c-3b3a1ec25761"
	 * @param cc
	 * @return
	 */
	public Entity newEntity(String uri, CallingContext cc) {
		Datastore ds = cc.getDatastore();
		User user = cc.getCurrentUser();
		
		if ( uri == null ) {
			throw new IllegalArgumentException("uri cannot be null");
		}
		
		EntityImpl ei = new EntityImpl(ds.createEntityUsingRelation(prototype, user));
		ei.backingObject.setStringField(ei.backingObject.primaryKey, uri);
		return ei;
	}
	
	/**
	 * Fetch the entity with the given primary key (uri).
	 * 
	 * @param uri
	 * @param cc
	 * @return
	 * @throws ODKEntityNotFoundException
	 */
	public Entity getEntity(String uri, CallingContext cc) throws ODKEntityNotFoundException {
		Datastore ds = cc.getDatastore();
		User user = cc.getCurrentUser();
		
		return new EntityImpl(ds.getEntity(prototype, uri, user));
	}
	
	/**
	 * Search for the entities having dataField values in the given relation to the specified value.
	 * 
	 * @param dataField
	 * @param op  e.g., EQUALS, LESS_THAN, etc.
	 * @param value
	 * @param cc
	 * @return
	 * @throws ODKDatastoreException
	 */
	@Override
	public List<Entity> getEntities( DataField dataField, Query.FilterOperation op, Object value, CallingContext cc) throws ODKDatastoreException {

		if ( !prototype.getFieldList().contains(dataField) ) {
			throw new IllegalArgumentException("Unrecognized data field: " + dataField.getName());
		}
		
		Datastore ds = cc.getDatastore();
		User user = cc.getCurrentUser();
		
		Query q = ds.createQuery(prototype, user);
		q.addFilter(dataField, op, value);
		
		List<? extends CommonFieldsBase> list = q.executeQuery(0);
		List<Entity> eList = new ArrayList<Entity>();
		for ( CommonFieldsBase b : list ) {
			eList.add( new EntityImpl( (RelationImpl) b ) );
		}
		
		return eList;
	}
	
	/**
	 * Insert or update the datastore with the values from this entity.
	 * 
	 * @param e
	 * @param cc
	 * @throws ODKEntityPersistException
	 */
	public void putEntity(Entity e, CallingContext cc) throws ODKEntityPersistException {
		Datastore ds = cc.getDatastore();
		User user = cc.getCurrentUser();
		
		EntityImpl ei = verifyEntityType(e);
		ds.putEntity(ei.backingObject, user);
	}
	
	/**
	 * Delete the given entity from the datastore.
	 * 
	 * @param e
	 * @param cc
	 * @throws ODKDatastoreException if the deletion fails.
	 */
	public void deleteEntity(Entity e, CallingContext cc) throws ODKDatastoreException {
		Datastore ds = cc.getDatastore();
		User user = cc.getCurrentUser();
		
		EntityImpl ei = verifyEntityType(e);
		ds.deleteEntity(new EntityKey(prototype, ei.backingObject.getUri()), user);
	}

	/**
	 * This is just a convenience method.  It may fail midway through 
	 * saving the list of entities.
	 * 
	 * @param eList
	 * @param cc
	 * @throws ODKEntityPersistException
	 */
	public void putEntities(List<Entity> eList, CallingContext cc) throws ODKEntityPersistException {
		Datastore ds = cc.getDatastore();
		User user = cc.getCurrentUser();

		List<RelationImpl> backingObjects = new ArrayList<RelationImpl>();
		for ( Entity e : eList ) {
			EntityImpl ei = verifyEntityType(e);
			backingObjects.add(ei.backingObject);
		}
		ds.putEntities(backingObjects, user);
	}
	
	/**
	 * This is just a convenience function.  It can fail after
	 * having deleted only some of the entities.
	 * 
	 * @param eList
	 * @param cc
	 * @throws ODKDatastoreException
	 */
	public void deleteEntities(List<Entity> eList, CallingContext cc) throws ODKDatastoreException {
		Datastore ds = cc.getDatastore();
		User user = cc.getCurrentUser();

		List<EntityKey> keys = new ArrayList<EntityKey>();
		for ( Entity e : eList ) {
			EntityImpl ei = verifyEntityType(e);
			keys.add(new EntityKey(ei.backingObject, ei.backingObject.getUri()));
		}
		ds.deleteEntities(keys, user);
	}

	/**
	 * This deletes all records in your table and drops it from the 
	 * datastore.  The deletion step is non-optimal for MySQL/Postgresql,
	 * but is required for Google BigTables, as that has no concept of 
	 * dropping a relation.  
	 * 
	 * @param cc
	 * @throws ODKDatastoreException
	 */
	public void dropRelation(CallingContext cc) throws ODKDatastoreException {
		Datastore ds = cc.getDatastore();
		User user = cc.getCurrentUser();

		Query q = ds.createQuery(prototype, user);
		List<?> pkList = q.executeDistinctValueForDataField(prototype.primaryKey);
		List<EntityKey> keys = new ArrayList<EntityKey>();
		for ( Object key : pkList ) {
			keys.add(new EntityKey(prototype, (String) key));
		}
		ds.deleteEntities(keys, user);
		ds.dropRelation(prototype, user);
		prototype = null;
	}
	
	/**
	 * Retrieve the DataField that matches the given fieldName.
	 * Useful when working with a dynamically-constructed table.
	 * 
	 * @param fieldName
	 * @return
	 */
	@Override
	public DataField getDataField(String fieldName) {
		DataField f = nameMap.get(fieldName);
		if ( f == null ) {
			throw new IllegalArgumentException("Field name " 
					+ fieldName + " is not a valid field name for this relation");
		}
		return f;
	}
	
	/**
	 * The backing object for the Entity.
	 * 
	 * @author mitchellsundt@gmail.com
	 *
	 */
	private static class RelationImpl extends CommonFieldsBase {

		RelationImpl(String schemaName, String tableName, List<DataField> definedFields) {
			super(schemaName, tableName);
			fieldList.addAll(definedFields);
		}

		private RelationImpl(RelationImpl ref, User user) {
			super(ref, user);
		}
		
		@Override
		public CommonFieldsBase getEmptyRow(User user) {
			return new RelationImpl(this, user);
		}
	};

	/**
	 * Implementation of the Entity interface.
	 * 
	 * @author mitchellsundt@gmail.com
	 *
	 */
	public class EntityImpl implements Entity {

		@Override
		public String getUri() {
			return backingObject.getUri();
		}

		@Override
		public String getCreatorUriUser() {
			return backingObject.getCreatorUriUser();
		}

		@Override
		public Date getCreationDate() {
			return backingObject.getCreationDate();
		}

		@Override
		public Date getLastUpdateDate() {
			return backingObject.getLastUpdateDate();
		}

		@Override
		public String getLastUpdateUriUser() {
			return backingObject.getLastUpdateUriUser();
		}
		
		@Override
		public void setBoolean(DataField fieldName, Boolean value) {
			backingObject.setBooleanField(verify(fieldName), value);
		}
		
		@Override
		public Boolean getBoolean(DataField fieldName) {
			return backingObject.getBooleanField(verify(fieldName));
		}
		
		@Override
		public void setDate(DataField fieldName, Date value) {
			backingObject.setDateField(verify(fieldName), value);
		}
		
		@Override
		public Date getDate(DataField fieldName) {
			return backingObject.getDateField(verify(fieldName));
		}
		
		@Override
		public void setDouble(DataField fieldName, Double value) {
			backingObject.setNumericField(verify(fieldName), 
					(value == null) ? null : BigDecimal.valueOf(value));
		}
		
		@Override
		public Double getDouble(DataField fieldName) {
			BigDecimal d = backingObject.getNumericField(verify(fieldName));
			return (d == null) ? null : d.doubleValue();
		}
		
		@Override
		public void setNumeric(DataField fieldName, BigDecimal value) {
			backingObject.setNumericField(verify(fieldName), value);
		}
		
		@Override
		public BigDecimal getNumeric(DataField fieldName) {
			return backingObject.getNumericField(verify(fieldName));
		}

		@Override
		public void setInteger(DataField fieldName, Integer value) {
			backingObject.setLongField(verify(fieldName), 
					(value == null) ? value : Long.valueOf(value));
		}
		
		@Override
		public Integer getInteger(DataField fieldName) {
			Long l = backingObject.getLongField(verify(fieldName));
			return (l == null) ? null : l.intValue();
		}

		@Override
		public void setLong(DataField fieldName, Long value) {
			backingObject.setLongField(verify(fieldName), value);
		}
		
		@Override
		public Long getLong(DataField fieldName) {
			return backingObject.getLongField(verify(fieldName));
		}
		
		@Override
		public void setString(DataField fieldName, String value ) {
			if ( !backingObject.setStringField(verify(fieldName), value)) {
				throw new IllegalArgumentException("Value is too long (" +
						value.length() + ") for field " + fieldName);
			}
		}

		@Override
		public String getString(DataField fieldName) {
			return backingObject.getStringField(verify(fieldName));
		}

		/**
		 * Save this entity into the datastore.
		 * 
		 * @param cc
		 * @throws ODKEntityPersistException
		 */
		@Override
		public void persist(CallingContext cc) throws ODKEntityPersistException {
			Datastore ds = cc.getDatastore();
			User user = cc.getCurrentUser();
			
			ds.putEntity(backingObject, user);
		}
		
		/**
		 * Remove this entity from the datastore.
		 * 
		 * @param cc
		 * @throws ODKDatastoreException
		 */
		@Override
		public void remove(CallingContext cc) throws ODKDatastoreException {
			Datastore ds = cc.getDatastore();
			User user = cc.getCurrentUser();
			
			ds.deleteEntity(new EntityKey( backingObject, 
										   backingObject.getUri()), user);
		}

		/** the actual persistence layer object holding the data values */
		private final RelationImpl backingObject;
		
		/**
		 * Verify the DataField is one defined by this relation.
		 * This is purely for debugging mismatched uses of DataFields.
		 * DataField equality is '==' equivalence.  You must use the 
		 * same DataField as that used when creating the relation.
		 * 
		 * Use {@link Relation.getDataField(String fieldName)} to retrieve the
		 * DataField for a given field name.  
		 * 
		 * @param fieldName
		 * @return
		 */
		private final DataField verify(DataField fieldName) {
			if ( !AbstractRelation.this.fieldSet.contains(fieldName) ) {
				throw new IllegalArgumentException("FieldName: " 
						+ fieldName.getName() 
						+ " is not identical to the one specified in this relation " 
						+ fieldName.toString());
			}
			return fieldName;
		}
		
		/**
		 * Constructor used only be RelationManipulator
		 * 
		 * @param backingObject
		 */
		protected EntityImpl(RelationImpl backingObject) {
			this.backingObject = backingObject;
		}
	}

	/** the table namespace of this relation */
	@SuppressWarnings("unused")
	private final TableNamespace namespace;
	/** name of the actual backing table in the persistence layer */
	private final String backingTableName;
	/** mapping from UPPER_CASE field names to the actual fields in database */
	private final Map<String,DataField> nameMap = new HashMap<String, DataField>();
	/** set of the actual DataFields in the database */
	private final Set<DataField> fieldSet = new HashSet<DataField>();
	
	RelationImpl prototype = null;
	
	/**
	 * Complete the initialization of the relation with the UPPER_CASE fieldNames.
	 * Note that the fields: _URI, _LAST_UPDATE_DATE, 
	 * _LAST_UPDATE_URI_USER, _CREATION_DATE, _CREATOR_URI_USER
	 * are always present and should not be passed into the fields list.
	 *  
	 * @param fields
	 * @param cc
	 * @throws ODKDatastoreException
	 */
	private void initialize(List<DataField> fields, CallingContext cc) throws ODKDatastoreException {
		
		List<DataField> definedFields = new ArrayList<DataField>();
		for ( DataField f : fields ) {
			String name = f.getName();
			if ( !name.matches(Relation.VALID_UPPER_CASE_NAME_REGEX) ) {
				throw new IllegalArgumentException("Field name is not a valid UPPER_CASE name: " + name);
			}
			if ( name.length() > Relation.MAX_PERSISTENCE_NAME_LENGTH ) {
				throw new IllegalArgumentException("Field name is too long: " + name);
			}
			if ( nameMap.containsKey(name) ) {
				throw new IllegalArgumentException("Field name: " + name + " is already specified!");
			}
			nameMap.put(name, f);
			fieldSet.add(f);
			definedFields.add(f);
		}
		
		// the 5 reserved column names should not be in the DataField list.
		// If you need access the DataField for them, use the Relation.getDataField() API to 
		// obtain them, or just use the Entity.getCreationDate(), etc. APIs.
		if (	nameMap.containsKey(CommonFieldsBase.CREATION_DATE_COLUMN_NAME) ||
				nameMap.containsKey(CommonFieldsBase.CREATOR_URI_USER_COLUMN_NAME) ||
				nameMap.containsKey(CommonFieldsBase.LAST_UPDATE_DATE_COLUMN_NAME) ||
				nameMap.containsKey(CommonFieldsBase.LAST_UPDATE_URI_USER_COLUMN_NAME) ||
				nameMap.containsKey(CommonFieldsBase.URI_COLUMN_NAME) ) {
			throw new IllegalArgumentException("One of the 5 reserved DataField names is "
											+ "errorneously supplied in the DataField list");
		}
		
		Datastore ds = cc.getDatastore();
		User user = cc.getCurrentUser();
		String schema = ds.getDefaultSchemaName();
		RelationImpl candidate = new RelationImpl(schema, backingTableName, definedFields);
		ds.assertRelation(candidate, user);
		prototype = candidate;
	}

	/**
	 * Ensure the entity being manipulated belongs to this 
	 * RelationManipulator.  This is not actually required 
	 * to be the case by the underlying system, but enforcing
	 * this here can prevent some bizarre coding errors.
	 * 
	 * @param e
	 */
	private EntityImpl verifyEntityType(Entity e) {
		if ( e == null ) {
			throw new IllegalArgumentException("null Entity is passed to " +
					"RelationManipulator for table: " + prototype.getTableName());
		}
		EntityImpl ei = (EntityImpl) e;
		if ( !ei.backingObject.sameTable(prototype) ) {
			throw new IllegalArgumentException("Mismatched entity types: Entity table: " 
					+ ei.backingObject.getTableName() + " RelationManipulator table: "
					+ prototype.getTableName());
		}
		return ei;
	}
}
