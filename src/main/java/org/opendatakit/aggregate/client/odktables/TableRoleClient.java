package org.opendatakit.aggregate.client.odktables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;

/**
 * This is the client-side version of
 * org.opendatakit.aggregate.odktables.entity.TableRole.java.
 * <br>
 * The idea is that it will serve the same function as the server-side
 * code.
 * @author sudar.sam@gmail.com
 *
 */
public enum TableRoleClient {
  NONE("No permissions. Can not see that the table exists."),
  
  FILTERED_READER("Can read properties and read filtered data .", 
      TablePermission.READ_TABLE_ENTRY,
      TablePermission.READ_ROW, 
      TablePermission.READ_PROPERTIES),
      
  FILTERED_WRITER("Can read properties and read/write/delete filtered data.",
      TablePermission.READ_TABLE_ENTRY, 
      TablePermission.READ_ROW,
      TablePermission.WRITE_ROW, 
      TablePermission.DELETE_ROW, 
      TablePermission.READ_PROPERTIES),     
      
  UNFILTERED_READER_FILTERED_WRITER("Can read properties, read all data, and write/delete filtered data.",
      TablePermission.READ_TABLE_ENTRY,
      TablePermission.READ_ROW,
      TablePermission.WRITE_ROW, 
      TablePermission.DELETE_ROW, 
      TablePermission.UNFILTERED_READ,
      TablePermission.READ_PROPERTIES),     
      
  READER("Can read properties and all data.", 
      TablePermission.READ_TABLE_ENTRY,
      TablePermission.READ_ROW, 
      TablePermission.UNFILTERED_READ, 
      TablePermission.READ_PROPERTIES),     
      
   WRITER("Can read properties and read/write/delete all data.", 
      TablePermission.READ_TABLE_ENTRY,
      TablePermission.READ_ROW, 
      TablePermission.WRITE_ROW, 
      TablePermission.DELETE_ROW,
      TablePermission.UNFILTERED_READ, 
      TablePermission.UNFILTERED_WRITE, 
      TablePermission.UNFILTERED_DELETE, 
      TablePermission.READ_PROPERTIES),     
      
   OWNER("All permissions. Can delete table, read/write properties, read/write/delete all data, and read/write/delete access control lists.",
      TablePermission.values());

  private final String description;
  private final List<TablePermission> permissions;

   TableRoleClient(String description, TablePermission... permissions) {
    this.description = description;
   // this.permissions = Lists.newArrayList(permissions);
    this.permissions = new ArrayList<TablePermission>();
    for (TablePermission tp : permissions) {
    	this.permissions.add(tp);
    }
  }

  /**
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * @return the permissions
   */
  public List<TablePermission> getPermissions() {
    return Collections.unmodifiableList(permissions);
  }
  
  /**
   * 
   * @param permission
   * @return true if this role has the given permission
   */
  public boolean hasPermission(TablePermission permission) {
    return permissions.contains(permission);
  }
  
  public enum TablePermission {
    READ_TABLE_ENTRY,
    DELETE_TABLE,
    READ_ROW,
    WRITE_ROW,
    DELETE_ROW,
    UNFILTERED_READ,
    UNFILTERED_WRITE,
    UNFILTERED_DELETE,
    READ_PROPERTIES,
    WRITE_PROPERTIES,
    READ_ACL,
    WRITE_ACL,
    DELETE_ACL,
  }
}
