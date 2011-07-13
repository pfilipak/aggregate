package org.opendatakit.aggregate.odktables.relation;

import java.util.Collections;
import java.util.List;

import org.opendatakit.aggregate.odktables.entity.Row;
import org.opendatakit.common.ermodel.Entity;
import org.opendatakit.common.ermodel.typedentity.TypedEntityRelation;
import org.opendatakit.common.persistence.DataField;
import org.opendatakit.common.persistence.DataField.DataType;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.web.CallingContext;

/**
 * <p>
 * Rows is a set of relations containing all the {@link Row} entities for all
 * tables stored in the datastore. An instance of Rows contains all the Row
 * entities for a specific table.
 * </p>
 * 
 * @author the.dylan.price@gmail.com
 */
public class Rows extends TypedEntityRelation<Row>
{
    /**
     * The name of the revisionTag field.
     */
    public static final String REVISION_TAG = "REVISION_TAG";

    /**
     * The revisionTag field.
     */
    private static final DataField revisionTag = new DataField(REVISION_TAG,
            DataType.STRING, false);
    
    /**
     * The namespace for Rows relations.
     */
    private static final String NAMESPACE = "ODKTABLES";

    private List<DataField> fields;

    /**
     * Constructs a Table. If the constructed Table does not already exist in
     * the datastore it will be created.
     * 
     * @param namespace
     *            the namespace the table should be created under.
     * @param tableUUID
     *            the globally unique identifier of the Table.
     * @param tableFields
     *            a list of DataFields representing the fields of the Table
     * @param cc
     *            the CallingContext of this Aggregate instance
     * @throws ODKDatastoreException
     *             if there was a problem during communication with the
     *             datastore
     */
    private Rows(String namespace, String tableUUID,
            List<DataField> tableFields, CallingContext cc)
            throws ODKDatastoreException
    {
        super(namespace, tableUUID, tableFields, cc);
        this.fields = tableFields;
    }

    @Override
    public Row initialize(Entity entity) throws ODKDatastoreException
    {
        return new Row(this, entity);
    }

    /**
     * @return a list of DataFields representing the columns of this table.
     */
    public List<DataField> getDataFields()
    {
        return Collections.unmodifiableList(this.fields);
    }

    public static Rows getInstance(String tableUUID, CallingContext cc)
            throws ODKDatastoreException
    {
        List<DataField> tableFields = Columns.getInstance(cc).getDataFields(
                tableUUID);
        tableFields.add(revisionTag);
        return new Rows(NAMESPACE, tableUUID, tableFields, cc);
    }
}