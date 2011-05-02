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

package org.opendatakit.aggregate.parser;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.javarosa.core.model.DataBinding;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.SubmissionProfile;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.xform.parse.XFormParser;
import org.javarosa.xform.util.IXFormBindHandler;
import org.javarosa.xform.util.XFormUtils;
import org.kxml2.kdom.Element;
import org.opendatakit.aggregate.constants.ParserConsts;
import org.opendatakit.aggregate.datamodel.FormDataModel;
import org.opendatakit.aggregate.datamodel.TopLevelDynamicBase;
import org.opendatakit.aggregate.datamodel.FormDataModel.ElementType;
import org.opendatakit.aggregate.exception.ODKConversionException;
import org.opendatakit.aggregate.exception.ODKFormAlreadyExistsException;
import org.opendatakit.aggregate.exception.ODKIncompleteSubmissionData;
import org.opendatakit.aggregate.exception.ODKParseException;
import org.opendatakit.aggregate.exception.ODKIncompleteSubmissionData.Reason;
import org.opendatakit.aggregate.form.Form;
import org.opendatakit.aggregate.form.FormDefinition;
import org.opendatakit.aggregate.form.FormInfo;
import org.opendatakit.aggregate.form.SubmissionAssociationTable;
import org.opendatakit.aggregate.form.XFormParameters;
import org.opendatakit.aggregate.submission.Submission;
import org.opendatakit.common.constants.BasicConsts;
import org.opendatakit.common.constants.HtmlConsts;
import org.opendatakit.common.datamodel.DynamicBase;
import org.opendatakit.common.datamodel.DynamicCommonFieldsBase;
import org.opendatakit.common.persistence.CommonFieldsBase;
import org.opendatakit.common.persistence.DataField;
import org.opendatakit.common.persistence.Datastore;
import org.opendatakit.common.persistence.EntityKey;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.persistence.exception.ODKEntityPersistException;
import org.opendatakit.common.security.Realm;
import org.opendatakit.common.security.User;
import org.opendatakit.common.web.CallingContext;

/**
 * Parses an XML definition of an XForm based on java rosa types
 * 
 * @author wbrunette@gmail.com
 * @author mitchellsundt@gmail.com
 * 
 */
public class FormParserForJavaRosa {
	
  private static final String NAMESPACE_ODK = "http://www.opendatakit.org/xforms";
  
  static Logger log = Logger.getLogger(FormParserForJavaRosa.class.getName());
	
  private static class XFormBindHandler implements IXFormBindHandler {

	private FormParserForJavaRosa active = null;
	
	private void setFormParserForJavaRosa(FormParserForJavaRosa current) {
		active = current;
	}
	
	@Override
	public void handle(Element element, DataBinding binding) {
		String value = element.getAttributeValue(NAMESPACE_ODK, "length");
		if ( value != null ) {
			element.setAttribute(NAMESPACE_ODK, "length", null);
		}
		
		log.info("Calling handle found value " + ((value == null) ? "null" : value));

		if ( value != null ) {
			Integer iValue = Integer.valueOf(value);
			active.setNodesetStringLength(element.getAttributeValue(null, "nodeset"), iValue);
		}
	}

	@Override
	public void init() {
		log.info("Calling init");
	}

	@Override
	public void postProcess(FormDef arg0) {
		log.info("Calling postProcess");
	}
	  
  }
  
  private static final XFormBindHandler handler;
  
  static {
	  handler = new XFormBindHandler();
	  XFormParser.registerBindHandler(handler);
  }

  private static synchronized final FormDef parseFormDefinition(String xml, FormParserForJavaRosa parser) throws ODKIncompleteSubmissionData {
	    String strippedXML = JRHelperUtil.removeNonJavaRosaCompliantTags(xml);

	    handler.setFormParserForJavaRosa(parser);
	    
	    FormDef formDef = null;
	    try {
	      formDef = XFormUtils.getFormFromInputStream(new ByteArrayInputStream(strippedXML.getBytes()));
	    } catch (Exception e) {
	      throw new ODKIncompleteSubmissionData(e, Reason.BAD_JR_PARSE);
	    } finally {
	      handler.setFormParserForJavaRosa(null);
	    }
	    
	    return formDef;
  }
  
  /**
   * The ODK Id that uniquely identifies the form
   */
  private final XFormParameters rootElementDefn;
  private final TreeElement submissionElement;
  private final XFormParameters submissionElementDefn;
  
  private String fdmSubmissionUri;
  private int elementCount = 0;
  private int phantomCount = 0;

  /**
   * The XForm definition in XML
   */
  private final String xml;
  private final Map<String,Integer> stringLengths = new HashMap<String,Integer>();
  private final Map<FormDataModel,Integer> fieldLengths = new HashMap<FormDataModel,Integer>();
  
  private void setNodesetStringLength(String nodeset, Integer length) {
	  stringLengths.put(nodeset, length);
  }
  
  private Integer getNodesetStringLength(TreeElement e) {
	  List<String> path = new ArrayList<String>();
	  while ( e != null && e.getName() != null ) {
		  path.add(e.getName());
		  e = e.getParent();
	  }
	  Collections.reverse(path);
	  
      StringBuilder b = new StringBuilder();
	  for ( String s : path ) {
		b.append("/");
		b.append(s);
	  }
	
	  String nodeset = b.toString();
	  Integer len = stringLengths.get(nodeset);
	  return len;
  }
  
  /**
   * Extract the form id, version and uiVersion.
   * 
   * @param rootElement - the tree element that is the root submission.
   * @param defaultFormIdValue - used if no "id" attribute found.  This should already be slash-substituted.
   * @return
   */
  private XFormParameters extractFormParameters( TreeElement rootElement, String defaultFormIdValue ) {

	String formIdValue = null;
	String versionString = rootElement.getAttributeValue(null, "version");
	String uiVersionString = rootElement.getAttributeValue(null, "uiVersion");
	
	// search for the "id" attribute
	for (int i = 0; i < rootElement.getAttributeCount(); i++) {
	  String name = rootElement.getAttributeName(i);
	  if (name.equals(ParserConsts.FORM_ID_ATTRIBUTE_NAME)) {
	    formIdValue = rootElement.getAttributeValue(i);
		formIdValue = formIdValue.replaceAll(ParserConsts.FORWARD_SLASH, ParserConsts.FORWARD_SLASH_SUBSTITUTION);
	    break;
	  }
	}
	
	return new XFormParameters((formIdValue == null) ? defaultFormIdValue : formIdValue, 
					(versionString == null) ? null : Long.valueOf(versionString),
					(uiVersionString == null) ? null : Long.valueOf(uiVersionString));
  }

  /**
   * Constructor that parses and xform from the input stream supplied and
   * creates the proper ODK Aggregate Form definition in the gae datastore.
   * 
   * @param formName - title of the form
   * @param formXmlData - Multipart form element defining the xml form...
   * @param inputXml - string containing the Xform definition
   * @param fileName - file name used for a file that specifies the form's XML definition
   * @param uploadedFormItems - Multipart form elements
   * @param datastore
   * @param user
   * @param rootDomain
   * @throws ODKFormAlreadyExistsException
   * @throws ODKIncompleteSubmissionData
   * @throws ODKConversionException
   * @throws ODKDatastoreException
   * @throws ODKParseException
   */
  public FormParserForJavaRosa(String formName, MultiPartFormItem formXmlData, String inputXml, String fileName,
	  MultiPartFormData uploadedFormItems,
      CallingContext cc) throws ODKFormAlreadyExistsException,
      ODKIncompleteSubmissionData, ODKConversionException, ODKDatastoreException,
      ODKParseException {

    if (inputXml == null || formXmlData == null) {
      throw new ODKIncompleteSubmissionData(Reason.MISSING_XML);
    }

    xml = inputXml;
    FormDef formDef = parseFormDefinition(xml, this);

    if (formDef == null) {
        throw new ODKIncompleteSubmissionData("Javarosa failed to construct a FormDef.  Is this an XForm definition?", Reason.BAD_JR_PARSE);
    }
    FormInstance dataModel = formDef.getInstance();
    if ( dataModel == null ) {
    	throw new ODKIncompleteSubmissionData("Javarosa failed to construct a FormInstance.  Is this an XForm definition?", Reason.BAD_JR_PARSE);
    }
    TreeElement rootElement = dataModel.getRoot();

    boolean schemaMalformed = false;
    String schemaValue = dataModel.schema;
    if ( schemaValue != null ) {
	  int idx = schemaValue.indexOf(":");
	  if ( idx != -1 ) {
		  if ( schemaValue.indexOf("/") < idx ) {
			  // malformed...
			  schemaValue = null;
			  schemaMalformed = true;
		  } else {
			  // need to escape all slashes... for xpath processing...
			  schemaValue = schemaValue.replaceAll(ParserConsts.FORWARD_SLASH, ParserConsts.FORWARD_SLASH_SUBSTITUTION);
		  }
	  } else {
		  // malformed...
		  schemaValue = null;
		  schemaMalformed = true;
	  }
    }
    try {
    	rootElementDefn = extractFormParameters( rootElement, schemaValue );
    } catch ( IllegalArgumentException e ) {
    	if ( schemaMalformed ) {
    		throw new ODKIncompleteSubmissionData(
	            "xmlns attribute for the data model is not well-formed: '"
	                + dataModel.schema
	                + "' should be of the form xmlns=\"http://your.domain.org/formId\"\nConsider defining the formId using the 'id' attribute instead of the 'xmlns' attribute (id=\"formId\")",
	                Reason.ID_MALFORMED);
    	} else {
    		throw new ODKIncompleteSubmissionData(
	            "The data model does not have an id or xmlns attribute.  Add an id=\"your.domain.org:formId\" attribute to the top-level instance data element of your form.",
	            Reason.ID_MISSING);
    	}
    }

    // Determine the information about the submission...
    SubmissionProfile p = formDef.getSubmissionProfile();
    if ( p == null || p.getRef() == null ) {
    	submissionElement = rootElement;
    	submissionElementDefn = rootElementDefn;
    } else {
    	submissionElement = formDef.getInstance().resolveReference(p.getRef());
    	try {
    		submissionElementDefn = extractFormParameters( submissionElement, null );
    	} catch ( Exception e ) {
    		throw new ODKIncompleteSubmissionData(
    	            "The non-root submission element in the data model does not have an id attribute.  Add an id=\"your.domain.org:formId\" attribute to the submission element of your form.",
    	            Reason.ID_MISSING);
    	}
    }

    Realm rootDomain = cc.getUserService().getCurrentRealm();
    // And construct the base table prefix candidate from the submissionElementDefn.formId.
    // First, replace all slash substitutions with underscores.
    // Then replace all non-alphanumerics with underscores.
    String persistenceStoreFormId = submissionElementDefn.formId.substring(submissionElementDefn.formId.indexOf(':') + 1);
    persistenceStoreFormId = persistenceStoreFormId.replace(ParserConsts.FORWARD_SLASH_SUBSTITUTION, "_");
    persistenceStoreFormId = persistenceStoreFormId.replaceAll("[^\\p{Digit}\\p{javaUpperCase}\\p{javaLowerCase}]", "_");
    persistenceStoreFormId = persistenceStoreFormId.replaceAll("^_*","");
    // and then try to remove the realm prefix...
    {
    	List<String> alternates = new ArrayList<String>();
    	alternates.addAll(rootDomain.getDomainSet());
    	alternates.add(rootDomain.getRootDomain());
    	// make sure the collection is sorted in longest-string-first order.
    	// we want the longest domain name to 
    	Collections.sort(alternates, new Comparator<String>() {

			@Override
			public int compare(String o1, String o2) {
				if ( o1.length() > o2.length() ) {
					return -1;
				} else if ( o1.length() < o2.length() ) {
					return 1;
				} else {
					return o1.compareTo(o2);
				}
			}
    	});

    	for ( String domain : alternates ) {
    		String mungedDomainName = domain.replaceAll("[^\\p{Digit}\\p{javaUpperCase}\\p{javaLowerCase}]", "_");
    	    if ( persistenceStoreFormId.startsWith(mungedDomainName) ) {
    	    	persistenceStoreFormId = persistenceStoreFormId.substring(mungedDomainName.length());
    	        persistenceStoreFormId = persistenceStoreFormId.replaceAll("^_*","");
    	        break;
    	    }
    	}
    }
    
    // OK -- we removed the organization's domain from what will be the 
    // database the table name prefix.  
    //
    
    // obtain form title either from the xform itself or from user entry
    String title = formDef.getTitle();
    if (title == null) {
      if (formName == null) {
        throw new ODKIncompleteSubmissionData(Reason.TITLE_MISSING);
      } else {
        title = formName;
      }
    }
    // clean illegal characters from title
    title = title.replace(BasicConsts.FORWARDSLASH, BasicConsts.EMPTY_STRING);

    initHelper(uploadedFormItems, formXmlData, inputXml,
    		  title, persistenceStoreFormId, formDef, cc);
  }
  
  enum AuxType { NONE, BC_REF, REF_BLOB, GEO_LAT, GEO_LNG, GEO_ALT, GEO_ACC, LONG_STRING_REF, REF_TEXT };
  
  private String generatePhantomKey( String uriSubmissionFormModel ) {
	  return String.format("elem+%1$s(%2$08d-phantom:%3$08d)", uriSubmissionFormModel,
				  		elementCount, ++phantomCount );
  }
  
  private void setPrimaryKey( FormDataModel m, String uriSubmissionFormModel, AuxType aux ) {
	  String pkString;
	  if ( aux != AuxType.NONE ) {
		  pkString = String.format("elem+%1$s(%2$08d-%3$s)", uriSubmissionFormModel,
				  		elementCount, aux.toString().toLowerCase());
	  } else {
		  ++elementCount;
		  pkString = String.format("elem+%1$s(%2$08d)", uriSubmissionFormModel, elementCount);
	  }
	  m.setStringField(m.primaryKey, pkString);
  }
  
  private void initHelper(MultiPartFormData uploadedFormItems, MultiPartFormItem xformXmlData,  
		  String inputXml, String title, String persistenceStoreFormId, 
		  FormDef formDef, CallingContext cc) throws ODKDatastoreException, ODKFormAlreadyExistsException, ODKParseException {
    
    /////////////////
    // Step 1: create or fetch the Form (FormInfo) submission
    //
    // This allows us to delete the form if upload goes bad...
    // create an empty submission then set values in it...
    Submission formInfo = Form.createOrFetchFormId(rootElementDefn.formId, cc);
	// TODO: the following function throws an exception unless new or identical inputXml
    byte[] xmlBytes;
    try {
		xmlBytes = inputXml.getBytes(HtmlConsts.UTF8_ENCODE);
	} catch (UnsupportedEncodingException e) {
		throw new IllegalStateException("not reachable");
	}
    boolean sameXForm = FormInfo.setXFormDefinition( formInfo, 
    					rootElementDefn.modelVersion, rootElementDefn.uiVersion,
    					title, xmlBytes, cc );
    
    // we will have thrown an exception above if the form file already exists and
    // the form file presented is not exactly identical to the one on record.
    
    FormInfo.setFormDescription( formInfo, null, title, null, null, cc);

    Set<Map.Entry<String,MultiPartFormItem>> fileSet = uploadedFormItems.getFileNameEntrySet();
    for ( Map.Entry<String,MultiPartFormItem> itm : fileSet) {
    	if ( itm.getValue() == xformXmlData ) continue;// ignore the xform -- stored above.
    	FormInfo.setXFormMediaFile(formInfo,
    			rootElementDefn.modelVersion, rootElementDefn.uiVersion,
				itm.getValue(), cc);
    }
    // Determine the information about the submission...
	FormInfo.setFormSubmission( formInfo, submissionElementDefn.formId, 
			submissionElementDefn.modelVersion, submissionElementDefn.uiVersion, cc );
	formInfo.setIsComplete(true);
    formInfo.persist(cc);

    Datastore ds = cc.getDatastore();
    User user = cc.getCurrentUser();
    List<SubmissionAssociationTable> saList = SubmissionAssociationTable.findSubmissionAssociationsForXForm(submissionElementDefn, cc);
    if ( saList.size() > 1 ) {
		throw new IllegalStateException("Logic is not yet in place for cross-form submission sharing");
    }
    SubmissionAssociationTable sa;
    if ( !saList.isEmpty() ) {
    	sa = saList.get(0);
    	fdmSubmissionUri = sa.getUriSubmissionDataModel();
    	// the entry already exists...
    	if ( !sameXForm ) {
    		throw new ODKFormAlreadyExistsException();
    	}
    	// TODO: should do a transaction around persisting the FDM we are about to generate.
    	FormDefinition fd = FormDefinition.getFormDefinition(submissionElementDefn, cc);
    	if ( fd != null ) return;
    } else {
    	fdmSubmissionUri = CommonFieldsBase.newUri();
        String submissionFormIdUri = CommonFieldsBase.newMD5HashUri(submissionElementDefn.formId); // key under which submission is located...

        SubmissionAssociationTable saRelation = SubmissionAssociationTable.assertRelation(cc);
	    sa = ds.createEntityUsingRelation(saRelation, user);
	    sa.setSubmissionFormId(submissionElementDefn.formId);
	    sa.setSubmissionModelVersion(submissionElementDefn.modelVersion);
	    sa.setSubmissionUiVersion(submissionElementDefn.uiVersion);
	    sa.setIsPersistenceModelComplete(false);
	    sa.setIsSubmissionAllowed(false);
	    sa.setUriSubmissionDataModel(fdmSubmissionUri);
	    sa.setUriMd5SubmissionFormId(submissionFormIdUri);
	    sa.setUriMd5FormId(formInfo.getKey().getKey());
	    ds.putEntity(sa, user);
    }
    
    // so we have the formInfo record, but no data model backing it.
    // Find the submission associated with this form...
    
    final List<FormDataModel> fdmList = new ArrayList<FormDataModel>();

    final Set<CommonFieldsBase> createdRelations = new HashSet<CommonFieldsBase>();
    
    try {
	    //////////////////////////////////////////////////
	    // Step 2: Now build up the parse tree for the form...
	    //
	    final FormDataModel fdm = FormDataModel.assertRelation(cc);
	    
	    final EntityKey k = new EntityKey( fdm, fdmSubmissionUri);
	
	    NamingSet opaque = new NamingSet();
	
	    // construct the data model with table and column placeholders.
	    // assumes that the root is a non-repeating group element.
	    final String tableNamePlaceholder = opaque
	        .getTableName(fdm.getSchemaName(), persistenceStoreFormId, "", "CORE");
	
	    constructDataModel(opaque, k, fdmList, fdm, k.getKey(), 1, persistenceStoreFormId, "",
	        tableNamePlaceholder, submissionElement, cc);
	
	    // emit the long string and ref text tables...
	    ++elementCount; // to give these tables their own element #.
	    String persistAsTable = opaque.getTableName(fdm.getSchemaName(), persistenceStoreFormId, "", "STRING_REF");
	    // long string ref text record...
	    FormDataModel d = ds.createEntityUsingRelation(fdm, user);
	    setPrimaryKey( d, fdmSubmissionUri, AuxType.LONG_STRING_REF );
	    fdmList.add(d);
	    final String lstURI = d.getUri();
	    d.setOrdinalNumber(2L);
	    d.setUriSubmissionDataModel(k.getKey());
	    d.setParentUriFormDataModel(k.getKey());
	    d.setElementName(null);
	    d.setElementType(FormDataModel.ElementType.LONG_STRING_REF_TEXT);
	    d.setPersistAsColumn(null);
	    d.setPersistAsTable(persistAsTable);
	    d.setPersistAsSchema(fdm.getSchemaName());
	
	    persistAsTable = opaque.getTableName(fdm.getSchemaName(), persistenceStoreFormId, "", "STRING_TXT");
	    // ref text record...
	    d = ds.createEntityUsingRelation(fdm, user);
	    setPrimaryKey( d, fdmSubmissionUri, AuxType.REF_TEXT );
	    fdmList.add(d);
	    d.setOrdinalNumber(1L);
	    d.setUriSubmissionDataModel(k.getKey());
	    d.setParentUriFormDataModel(lstURI);
	    d.setElementName(null);
	    d.setElementType(FormDataModel.ElementType.REF_TEXT);
	    d.setPersistAsColumn(null);
	    d.setPersistAsTable(persistAsTable);
	    d.setPersistAsSchema(fdm.getSchemaName());
	
	    // find a good set of names...
	    // this also ensures that the table names don't overlap existing tables
	    // in the datastore.
	    opaque.resolveNames(ds, user);
	
	    // and revise the data model with those names...
	    for (FormDataModel m : fdmList) {
	      String tablePlaceholder = m.getPersistAsTable();
	      if (tablePlaceholder == null)
	        continue;
	
	      String columnPlaceholder = m.getPersistAsColumn();
	
	      String tableName = opaque.resolveTablePlaceholder(tablePlaceholder);
	      String columnName = opaque.resolveColumnPlaceholder(tablePlaceholder, columnPlaceholder);
	      
	      m.setPersistAsColumn(columnName);
	      m.setPersistAsTable(tableName);
	    }
	
	    for (FormDataModel m : fdmList) {
	      m.print(System.out);
	    }
	
	    /////////////////////////////////////////////
	    // Step 3: create the backing tables...
	    //
	    // OK. At this point, the construction gets a bit ugly.
	    // We need to handle the possibility that the table
	    // needs to be split into phantom tables.
	    // That happens if the table exceeds the maximum row
	    // size for the persistence layer.
	
	    // we do this by constructing the form definition from the fdmList
	    // and then testing for successful creation of each table it defines.
	    // If that table cannot be created, we subdivide it, rearranging
	    // the structure of the fdmList. Repeat until no errors.
	    // Very error prone!!!
	    // 
	    try {
		    for (;;) {
		      FormDefinition fd = new FormDefinition(submissionElementDefn, fdmList, cc);
		
		      createdRelations.add(fd.getLongStringRefTextTable());
		      createdRelations.add(fd.getRefTextTable());
		      
		      List<CommonFieldsBase> badTables = new ArrayList<CommonFieldsBase>();
		
		      for (CommonFieldsBase tbl : fd.getBackingTableSet()) {
		
		        try {
		        	// patch up tbl with desired lengths of string fields...
		        	for ( FormDataModel m : fdmList ) {
		        		if ( m.getElementType().equals(ElementType.STRING) ) {
		        			DataField f = m.getBackingKey();
		        			Integer i = fieldLengths.get(m);
		        			if ( f != null && i != null ) {
		        				f.setMaxCharLen(new Long(i));
		        			}
		        		}
		        	}
		        	ds.assertRelation(tbl, user);
		        	createdRelations.add(tbl);
		        } catch (Exception e1) {
		          // assume it is because the table is too wide...
		          Logger.getLogger(FormParserForJavaRosa.class.getName()).warning(
		              "Create failed -- assuming phantom table required " + tbl.getSchemaName() + "."
		                  + tbl.getTableName());
		          try {
		            ds.dropRelation(tbl, user);
		          } catch (Exception e2) {
		            // no-op
		          }
		          if ((tbl instanceof DynamicBase) ||
		        	  (tbl instanceof TopLevelDynamicBase)) {
			          badTables.add(tbl); // we know how to subdivide these
		          } else {
		        	  throw e1; // must be something amiss with database...
		          }
		        }
		      }
		
		      for (CommonFieldsBase tbl : badTables) {
		        // dang. We need to create phantom tables...
		        orderlyDivideTable(fdmList, FormDataModel.assertRelation(cc), 
		        		tbl, opaque, cc);
		      }
		
		      if (badTables.isEmpty())
		        break;
		      
		      // reset the derived fields so that the FormDefinition construction will work.
		      for ( FormDataModel m : fdmList ) {
		    	  m.resetDerivedFields();
		      }
		    }
	    } catch ( Exception e ) {
		      FormDefinition fd = new FormDefinition(submissionElementDefn, fdmList, cc);
		  	
		      for (CommonFieldsBase tbl : fd.getBackingTableSet()) {
		    	  try {
		    		  ds.dropRelation(tbl, user);
		    		  createdRelations.remove(tbl);
		    	  } catch ( Exception e3 ) {
		    		  // do nothing...
		    		  e3.printStackTrace();
		    	  }
		      }
		      if ( !createdRelations.isEmpty()) {
		    	  Logger.getLogger(FormParserForJavaRosa.class.getName()).severe(
		    			  "createdRelations not fully unwound!");
		    	  for (CommonFieldsBase tbl : createdRelations ) {
			    	  try {
				    	  Logger.getLogger(FormParserForJavaRosa.class.getName()).severe(
		    			  "--dropping " + tbl.getSchemaName() + "." + tbl.getTableName());
			    		  ds.dropRelation(tbl, user);
			    		  createdRelations.remove(tbl);
			    	  } catch ( Exception e3 ) {
			    		  // do nothing...
			    		  e3.printStackTrace();
			    	  }
		    	  }
		    	  createdRelations.clear();
		      }
	    }
    
    // TODO: if the above gets killed, how do we clean up?
    } catch ( ODKParseException e ) {
    	List<EntityKey> keys = new ArrayList<EntityKey>();
    	formInfo.recursivelyAddEntityKeys(keys, cc);
    	keys.add(new EntityKey(sa, sa.getUri()));
    	keys.add(formInfo.getKey());
    	ds.deleteEntities(keys, user);
    	throw e;
    } catch ( ODKDatastoreException e ) {
    	List<EntityKey> keys = new ArrayList<EntityKey>();
    	formInfo.recursivelyAddEntityKeys(keys, cc);
    	keys.add(new EntityKey(sa, sa.getUri()));
    	keys.add(formInfo.getKey());
    	ds.deleteEntities(keys, user);
    	throw e;
    }

    //////////////////////////////////////////////
    // Step 4: record the data model...
    //
    // if we get here, we were able to create the tables -- record the
    // form description....
	ds.putEntities(fdmList, user);
	
    // TODO: if above write fails, how do we clean this up?
  }

  /**
   * The creation of the tbl relation has failed.  
   * We need to split it into multiple sub-tables and try again.
   * 
   * @param fdmList
   * @param fdmRelation
   * @param tbl
   * @param newPhantomTableName
   */
  private void orderlyDivideTable(List<FormDataModel> fdmList, FormDataModel fdmRelation,
      CommonFieldsBase tbl, NamingSet opaque, CallingContext cc) {
	  
    // Find out how many columns it has...
    int nCol = tbl.getFieldList().size() - DynamicCommonFieldsBase.WELL_KNOWN_COLUMN_COUNT;

    if (nCol < 2) {
      throw new IllegalStateException("Unable to subdivide instance table! " + tbl.getSchemaName()
          + "." + tbl.getTableName());
    }

    // search the fdmList for the most-enclosing element that uses this tbl as its backingObject.
    FormDataModel parentTable = null;

    // Step 1: find any FormDataModel that uses tbl as its backing object.
    for (FormDataModel m : fdmList) {
      if (tbl.equals(m.getBackingObjectPrototype())) {
        parentTable = m; // anything we find is good enough...
        break;
      }
    }
    // we should have found something...
    if (parentTable == null) {
      throw new IllegalStateException("Unable to locate model for backing table");
    }

    // Step 2: chain up to the parent whose parent doesn't have tbl as its backing object
    while (parentTable.getParent() != null) {
      FormDataModel parent = parentTable.getParent();
      if (!tbl.equals(parent.getBackingObjectPrototype()))
        break;
      // daisy-chain up to parent
      // we must have had an element or a subordinate group...
      parentTable = parent;
    }

    // go through the parent's children identifying those that 
    // are backed by the table we need to split.
    List<FormDataModel> topElementChange = new ArrayList<FormDataModel>();
    List<FormDataModel> groups = new ArrayList<FormDataModel>();
    for (;;) {
      for (FormDataModel m : parentTable.getChildren()) {
    	// ignore the choice and binary data fields of the parentTable
        if (tbl.equals(m.getBackingObjectPrototype())) {
          // geopoints, phantoms and groups don't have backing keys
          if (m.getBackingKey() != null ) {
            topElementChange.add(m);
          } else {
            int count = recursivelyCountChildrenInSameTable(m);
            if ( (nCol < 4*count) || (count > 10) ) {
            	// it is big enough to consider moving...
                groups.add(m);
            } else {
            	// clump it into the individual elements to move...
            	topElementChange.add(m);
            }
          }
        }
      }
      if (groups.size() + topElementChange.size() == 1) {
        // we have a bogus parent element -- it has only one group
        // in it -- recurse down the groups until we get something
        // with multiple elements. If it is just a single field,
        // we have big problems...
        parentTable = groups.get(0);
        groups.clear();
        topElementChange.clear();

        if (parentTable == null) {
          throw new IllegalStateException(
              "Are there database problems? Failure in create table when there are no nested groups!");
        }
        // note that we don't have to patch up the parentTable we are
        // moving off of, because the tbl will continue to exist.  We
        // just need to move some of its contents to a second table, 
        // either by moving a nested group or geopoint off, or by 
        // creating a phantom table.
      } else {
        // OK we have a chance to do something at this level...
        break;
      }
    }

    // If we have any decent-sized groups, we should cleave off up to 2/3 of the 
    // total elements that may be under a group...
    if (groups.size() > 0) {
      // order the list from high to low...
      Collections.sort(groups, new Comparator<FormDataModel>() {
		@Override
		public int compare(FormDataModel o1, FormDataModel o2) {
	        int c1 = recursivelyCountChildrenInSameTable(o1);
	        int c2 = recursivelyCountChildrenInSameTable(o2);
	        if ( c1 > c2 ) return -1;
	        if ( c1 < c2 ) return 1;
	        return 0;
		}
      });

      // go through the list moving the larger groups into tables
      // until close to half of the elements are moved...
      int cleaveCount = 0;
      for ( FormDataModel m : groups ) {
    	  int groupSize = recursivelyCountChildrenInSameTable(m);
    	  if ( cleaveCount+groupSize > (3*nCol)/4) {
    		  continue; // just too big to split this way see if there is a smaller group...
    	  }
          String newGroupTable = opaque.generateUniqueTableName(tbl.getSchemaName(), tbl.getTableName(),
        		  				cc);
          recursivelyReassignChildren(m, tbl, newGroupTable);
          cleaveCount += groupSize;
          // and if we have cleaved over half, (divide and conquer), retry it with the database.
          if ( cleaveCount > (nCol/2) ) return;
      }
      // and otherwise, if we did cleave anything off, try anyway...
      // the next time through, we won't have any groups and will need
      // to create phantom tables, so it is worth trying for this here now...
      if ( cleaveCount > 0 ) return;
    }

    // Urgh! we don't have a nested group we can cleave off.
    // or the nested groups are all small ones.  Create a 
    // phantom table.  We need to preserve the parent-child
    // relationship and the ordinal ordering even for the 
    // external tables like choices and binary objects.
    //
    // The children array is ordered by ordinal number, 
    // so we just need to get that, and update the entries
    // in the last half of the array.
    String phantomURI = generatePhantomKey(fdmSubmissionUri);
    String newPhantomTableName = opaque.generateUniqueTableName(tbl.getSchemaName(), tbl.getTableName(),
				cc);
    int desiredOriginalTableColCount = (nCol / 2);
    List<FormDataModel> children = parentTable.getChildren();
    int skipCleaveCount = 0;
    int idxStart;
    for (idxStart = 0 ; idxStart < children.size() ; ++idxStart ) {
    	FormDataModel m = children.get(idxStart);
        if (!tbl.equals(m.getBackingObjectPrototype())) continue;        
    	if (m.getBackingKey() == null) {
    		skipCleaveCount += recursivelyCountChildrenInSameTable(m);
    	} else {
    		++skipCleaveCount;
    	}
    	if ( skipCleaveCount > desiredOriginalTableColCount ) break;
    }
    // everything after idxStart should be moved to be "under" the 
    // phantom table.
    FormDataModel firstToMove = children.get(++idxStart);
    // data record...
    FormDataModel d = cc.getDatastore().createEntityUsingRelation(fdmRelation, cc.getCurrentUser());
    fdmList.add(d);
    d.setStringField(fdmRelation.primaryKey, phantomURI);
    d.setOrdinalNumber(firstToMove.getOrdinalNumber());
    d.setUriSubmissionDataModel(fdmSubmissionUri);
    d.setParentUriFormDataModel(parentTable.getUri());
    d.setElementName(null);
    d.setElementType(FormDataModel.ElementType.PHANTOM);
    d.setPersistAsColumn(null);
    d.setPersistAsTable(newPhantomTableName);
    d.setPersistAsSchema(fdmRelation.getSchemaName());

    // OK -- update ordinals and move remaining columns...
    long ordinalNumber = 0L;
    for ( ; idxStart < children.size() ; ++ idxStart ) {
    	FormDataModel m = children.get(idxStart);
        m.setParentUriFormDataModel(phantomURI);
        m.setOrdinalNumber(++ordinalNumber);
    	recursivelyReassignChildren(m, tbl, newPhantomTableName);
    }
  }

  private int recursivelyCountChildrenInSameTable(FormDataModel parent) {

    int count = 0;
    for (FormDataModel m : parent.getChildren()) {
      if (parent.getPersistAsTable().equals(m.getPersistAsTable())
          && parent.getPersistAsSchema().equals(m.getPersistAsSchema())) {
        count += recursivelyCountChildrenInSameTable(m);
      }
    }
    if (parent.getPersistAsColumn() != null) {
      count++;
    }
    return count;
  }

  private void recursivelyReassignChildren(FormDataModel biggest, CommonFieldsBase tbl, String newPhantomTableName) {
	  
    if (!tbl.equals(biggest.getBackingObjectPrototype())) return;

    biggest.setPersistAsTable(newPhantomTableName);

	for (FormDataModel m : biggest.getChildren()) {
		recursivelyReassignChildren(m, tbl, newPhantomTableName);
    }

  }

  /**
   * Used to recursively process the xform definition tree to create the form
   * data model.
   * 
   * @param treeElement
   *          java rosa tree element
   * 
   * @param parentKey
   *          key from the parent form for proper entity group usage in gae
   * 
   * @param parent
   *          parent form element
   * 
   * @throws ODKEntityPersistException
   * @throws ODKParseException
   * 
   */

  private void constructDataModel(final NamingSet opaque, final EntityKey k,
      final List<FormDataModel> dmList, final FormDataModel fdm, 
      String parent, int ordinal, String tablePrefix, String nrGroupPrefix, String tableName,
      TreeElement treeElement, CallingContext cc) throws ODKEntityPersistException, ODKParseException {
    System.out.println("processing te: " + treeElement.getName() + " type: " + treeElement.dataType
        + " repeatable: " + treeElement.repeatable);

    FormDataModel d;

    FormDataModel.ElementType et;
    String persistAsTable = tableName;
    String originalPersistAsColumn = opaque.getColumnName(persistAsTable, nrGroupPrefix,
        treeElement.getName());
    String persistAsColumn = originalPersistAsColumn;

    switch (treeElement.dataType) {
    case org.javarosa.core.model.Constants.DATATYPE_TEXT:/**
       * Text question type.
       */
      et = FormDataModel.ElementType.STRING;
      break;
    case org.javarosa.core.model.Constants.DATATYPE_INTEGER:/**
       * Numeric question
       * type. These are numbers without decimal points
       */
      et = FormDataModel.ElementType.INTEGER;
      break;
    case org.javarosa.core.model.Constants.DATATYPE_DECIMAL:/**
       * Decimal question
       * type. These are numbers with decimals
       */
      et = FormDataModel.ElementType.DECIMAL;
      break;
    case org.javarosa.core.model.Constants.DATATYPE_DATE:/**
       * Date question type.
       * This has only date component without time.
       */
      et = FormDataModel.ElementType.JRDATE;
      break;
    case org.javarosa.core.model.Constants.DATATYPE_TIME:/**
       * Time question type.
       * This has only time element without date
       */
      et = FormDataModel.ElementType.JRTIME;
      break;
    case org.javarosa.core.model.Constants.DATATYPE_DATE_TIME:/**
       * Date and Time
       * question type. This has both the date and time components
       */
      et = FormDataModel.ElementType.JRDATETIME;
      break;
    case org.javarosa.core.model.Constants.DATATYPE_CHOICE:/**
       * This is a question
       * with alist of options where not more than one option can be selected at
       * a time.
       */
      et = FormDataModel.ElementType.STRING;
      // et = FormDataModel.ElementType.SELECT1;
      // persistAsColumn = null;
      // persistAsTable = opaque.getTableName(fdm.getSchemaName(),
      // tablePrefix, nrGroupPrefix, treeElement.getName());
      break;
    case org.javarosa.core.model.Constants.DATATYPE_CHOICE_LIST:/**
       * This is a
       * question with alist of options where more than one option can be
       * selected at a time.
       */
      et = FormDataModel.ElementType.SELECTN;
      opaque.removeColumnName(persistAsTable, persistAsColumn);
      persistAsColumn = null;
      persistAsTable = opaque.getTableName(fdm.getSchemaName(), tablePrefix, nrGroupPrefix,
          treeElement.getName());
      break;
    case org.javarosa.core.model.Constants.DATATYPE_BOOLEAN:/**
       * Question with
       * true and false answers.
       */
      et = FormDataModel.ElementType.BOOLEAN;
      break;
    case org.javarosa.core.model.Constants.DATATYPE_GEOPOINT:/**
       * Question with
       * location answer.
       */
      et = FormDataModel.ElementType.GEOPOINT;
      opaque.removeColumnName(persistAsTable, persistAsColumn);
      persistAsColumn = null; // structured field
      break;
    case org.javarosa.core.model.Constants.DATATYPE_BARCODE:/**
       * Question with
       * barcode string answer.
       */
      et = FormDataModel.ElementType.STRING;
      break;
    case org.javarosa.core.model.Constants.DATATYPE_BINARY:/**
       * Question with
       * external binary answer.
       */
      et = FormDataModel.ElementType.BINARY;
      opaque.removeColumnName(persistAsTable, persistAsColumn);
      persistAsColumn = null;
      persistAsTable = opaque.getTableName(fdm.getSchemaName(), tablePrefix, nrGroupPrefix,
          treeElement.getName() + "_BN");
      break;

    case org.javarosa.core.model.Constants.DATATYPE_NULL: /*
                                                           * for nodes that have
                                                           * no data, or data
                                                           * type otherwise
                                                           * unknown
                                                           */
      if (treeElement.repeatable) {
        persistAsColumn = null;
        opaque.removeColumnName(persistAsTable, persistAsColumn);
        et = FormDataModel.ElementType.REPEAT;
        persistAsTable = opaque.getTableName(fdm.getSchemaName(), tablePrefix, nrGroupPrefix,
            treeElement.getName());
      } else if (treeElement.getNumChildren() == 0) {
        // assume fields that don't have children are string fields.
        // the developer likely has not set a type for the field.
        et = FormDataModel.ElementType.STRING;
        Logger.getLogger(FormParserForJavaRosa.class.getCanonicalName()).warning(
            "Element " + getTreeElementPath(treeElement) + " does not have a type");
        throw new ODKParseException(
            "Field name: "
                + getTreeElementPath(treeElement)
                + " appears to be a value field (it has no fields nested within it) but does not have a type.");
      } else /* one or more children -- this is a non-repeating group */{
        persistAsColumn = null;
        opaque.removeColumnName(persistAsTable, persistAsColumn);
        et = FormDataModel.ElementType.GROUP;
      }
      break;

    default:
    case org.javarosa.core.model.Constants.DATATYPE_UNSUPPORTED:
      et = FormDataModel.ElementType.STRING;
      break;
    }

    Datastore ds = cc.getDatastore();
    User user = cc.getCurrentUser();
    // data record...
    d = ds.createEntityUsingRelation(fdm, user);
    setPrimaryKey( d, fdmSubmissionUri, AuxType.NONE );
    dmList.add(d);
    final String groupURI = d.getUri();
    d.setOrdinalNumber(Long.valueOf(ordinal));
    d.setUriSubmissionDataModel(k.getKey());
    d.setParentUriFormDataModel(parent);
    d.setElementName(treeElement.getName());
    d.setElementType(et);
    d.setPersistAsColumn(persistAsColumn);
    d.setPersistAsTable(persistAsTable);
    d.setPersistAsSchema(fdm.getSchemaName());
    
    if ( et.equals(ElementType.STRING) ) {
    	// track the preferred string lengths of the string fields
    	Integer len = getNodesetStringLength(treeElement);
    	if ( len != null ) {
    		fieldLengths.put(d, len);
    	}
    }

    // and patch up the tree elements that have multiple fields...
    switch (et) {
    case BINARY:
      // binary elements have two additional tables associated with them
      // -- the _REF and _BLB tables (in addition to _BIN above).
      persistAsTable = opaque.getTableName(fdm.getSchemaName(), tablePrefix, nrGroupPrefix,
          treeElement.getName() + "_REF");

      // record for VersionedBinaryContentRefBlob..
      d = ds.createEntityUsingRelation(fdm, user);
	  setPrimaryKey( d, fdmSubmissionUri, AuxType.BC_REF );
	  dmList.add(d);
      final String bcbURI = d.getUri();
      d.setOrdinalNumber(1L);
      d.setUriSubmissionDataModel(k.getKey());
      d.setParentUriFormDataModel(groupURI);
      d.setElementName(treeElement.getName());
      d.setElementType(FormDataModel.ElementType.BINARY_CONTENT_REF_BLOB);
      d.setPersistAsColumn(null);
      d.setPersistAsTable(persistAsTable);
      d.setPersistAsSchema(fdm.getSchemaName());

      persistAsTable = opaque.getTableName(fdm.getSchemaName(), tablePrefix, nrGroupPrefix,
          treeElement.getName() + "_BLB");

      // record for RefBlob...
      d = ds.createEntityUsingRelation(fdm, user);
	  setPrimaryKey( d, fdmSubmissionUri, AuxType.REF_BLOB );
	  dmList.add(d);
      d.setOrdinalNumber(1L);
      d.setUriSubmissionDataModel(k.getKey());
      d.setParentUriFormDataModel(bcbURI);
	  d.setElementName(treeElement.getName());
	  d.setElementType(FormDataModel.ElementType.REF_BLOB);
      d.setPersistAsColumn(null);
      d.setPersistAsTable(persistAsTable);
      d.setPersistAsSchema(fdm.getSchemaName());
      break;

    case GEOPOINT:
      // geopoints are stored as 4 fields (_LAT, _LNG, _ALT, _ACC) in the
      // persistence layer.
      // the geopoint attribute itself has no column, but is a placeholder
      // within
      // the data model for the expansion set of these 4 fields.

      persistAsColumn = opaque.getColumnName(persistAsTable, nrGroupPrefix, treeElement.getName()
          + "_LAT");

      d = ds.createEntityUsingRelation(fdm, user);
	  setPrimaryKey( d, fdmSubmissionUri, AuxType.GEO_LAT );
      dmList.add(d);
      d.setOrdinalNumber(Long.valueOf(FormDataModel.GEOPOINT_LATITUDE_ORDINAL_NUMBER));
      d.setUriSubmissionDataModel(k.getKey());
      d.setParentUriFormDataModel(groupURI);
	  d.setElementName(treeElement.getName());
	  d.setElementType(FormDataModel.ElementType.DECIMAL);
      d.setPersistAsColumn(persistAsColumn);
      d.setPersistAsTable(persistAsTable);
      d.setPersistAsSchema(fdm.getSchemaName());

      persistAsColumn = opaque.getColumnName(persistAsTable, nrGroupPrefix, treeElement.getName()
          + "_LNG");

      d = ds.createEntityUsingRelation(fdm, user);
	  setPrimaryKey( d, fdmSubmissionUri, AuxType.GEO_LNG );
      dmList.add(d);
      d.setOrdinalNumber(Long.valueOf(FormDataModel.GEOPOINT_LONGITUDE_ORDINAL_NUMBER));
      d.setUriSubmissionDataModel(k.getKey());
      d.setParentUriFormDataModel(groupURI);
	  d.setElementName(treeElement.getName());
	  d.setElementType(FormDataModel.ElementType.DECIMAL);
      d.setPersistAsColumn(persistAsColumn);
      d.setPersistAsTable(persistAsTable);
      d.setPersistAsSchema(fdm.getSchemaName());

      persistAsColumn = opaque.getColumnName(persistAsTable, nrGroupPrefix, treeElement.getName()
          + "_ALT");

      d = ds.createEntityUsingRelation(fdm, user);
	  setPrimaryKey( d, fdmSubmissionUri, AuxType.GEO_ALT );
      dmList.add(d);
      d.setOrdinalNumber(Long.valueOf(FormDataModel.GEOPOINT_ALTITUDE_ORDINAL_NUMBER));
      d.setUriSubmissionDataModel(k.getKey());
      d.setParentUriFormDataModel(groupURI);
	  d.setElementName(treeElement.getName());
	  d.setElementType(FormDataModel.ElementType.DECIMAL);
      d.setPersistAsColumn(persistAsColumn);
      d.setPersistAsTable(persistAsTable);
      d.setPersistAsSchema(fdm.getSchemaName());

      persistAsColumn = opaque.getColumnName(persistAsTable, nrGroupPrefix, treeElement.getName()
          + "_ACC");

      d = ds.createEntityUsingRelation(fdm, user);
	  setPrimaryKey( d, fdmSubmissionUri, AuxType.GEO_ACC );
      dmList.add(d);
      d.setOrdinalNumber(Long.valueOf(FormDataModel.GEOPOINT_ACCURACY_ORDINAL_NUMBER));
      d.setUriSubmissionDataModel(k.getKey());
      d.setParentUriFormDataModel(groupURI);
	  d.setElementName(treeElement.getName());
      d.setElementType(FormDataModel.ElementType.DECIMAL);
      d.setPersistAsColumn(persistAsColumn);
      d.setPersistAsTable(persistAsTable);
      d.setPersistAsSchema(fdm.getSchemaName());
      break;

    case GROUP:
      // non-repeating group - this modifies the group prefix,
      // and all children are emitted.
      if (!parent.equals(k.getKey())) {
        // incorporate the group name only if it isn't the top-level
        // group.
        if (nrGroupPrefix.length() == 0) {
          nrGroupPrefix = treeElement.getName();
        } else {
          nrGroupPrefix = nrGroupPrefix + "_" + treeElement.getName();
        }
      }
      // OK -- group with at least one element -- assume no value...
      // TreeElement list has the begin and end tags for the nested groups.
      // Swallow the end tag by looking to see if the prior and current
      // field names are the same.
      TreeElement prior = null;
      int trueOrdinal = 0;
      for (int i = 0; i < treeElement.getNumChildren(); ++i) {
    	  TreeElement current = (TreeElement) treeElement.getChildAt(i);
    	  // TODO: make this pay attention to namespace of the tag...
    	  if ( (prior != null) && 
    		   (prior.getName().equals(current.getName())) ) {
    		  // it is the end-group tag...
    		  prior = current;
    	  } else {
    		  constructDataModel(opaque, k, dmList, fdm, groupURI, ++trueOrdinal, tablePrefix,
    				  nrGroupPrefix, persistAsTable, current, cc);
    		  prior = current;
    	  }
      }
      break;

    case REPEAT:
      // repeating group - clears group prefix
      // and all children are emitted.
      // TreeElement list has the begin and end tags for the nested groups.
      // Swallow the end tag by looking to see if the prior and current
      // field names are the same.
      prior = null;
      trueOrdinal = 0;
      for (int i = 0; i < treeElement.getNumChildren(); ++i) {
    	  TreeElement current = (TreeElement) treeElement.getChildAt(i);
    	  // TODO: make this pay attention to namespace of the tag...
    	  if ( (prior != null) && 
    		   (prior.getName().equals(current.getName())) ) {
    		  // it is the end-group tag...
    		  prior = current;
    	  } else {
    		  constructDataModel(opaque, k, dmList, fdm, groupURI, ++trueOrdinal, tablePrefix,
    				  "", persistAsTable, current, cc);
    		  prior = current;
    	  }
      }
      break;
    }
  }

  public String getTreeElementPath(TreeElement e) {
	  if ( e ==  null ) return null;
	  String s = getTreeElementPath(e.getParent());
	  if ( s == null ) return e.getName();
	  return s + "/" + e.getName();
  }
  
  public String getFormId() {
    return rootElementDefn.formId;
  }
}