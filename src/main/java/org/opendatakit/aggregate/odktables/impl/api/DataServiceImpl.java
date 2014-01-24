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

package org.opendatakit.aggregate.odktables.impl.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.opendatakit.aggregate.odktables.DataManager;
import org.opendatakit.aggregate.odktables.api.DataService;
import org.opendatakit.aggregate.odktables.api.TableService;
import org.opendatakit.aggregate.odktables.exception.BadColumnNameException;
import org.opendatakit.aggregate.odktables.exception.ETagMismatchException;
import org.opendatakit.aggregate.odktables.exception.InconsistentStateException;
import org.opendatakit.aggregate.odktables.exception.PermissionDeniedException;
import org.opendatakit.aggregate.odktables.rest.entity.Row;
import org.opendatakit.aggregate.odktables.rest.entity.RowResource;
import org.opendatakit.aggregate.odktables.rest.entity.RowResourceList;
import org.opendatakit.aggregate.odktables.security.TablesUserPermissions;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.persistence.exception.ODKEntityNotFoundException;
import org.opendatakit.common.persistence.exception.ODKTaskLockException;
import org.opendatakit.common.web.CallingContext;

public class DataServiceImpl implements DataService {
  private DataManager dm;
  private UriInfo info;

  public DataServiceImpl(String tableId, UriInfo info, TablesUserPermissions userPermissions, CallingContext cc)
      throws ODKEntityNotFoundException, ODKDatastoreException {
    this.dm = new DataManager(tableId, userPermissions, cc);
    this.info = info;
  }

  @Override
  public RowResourceList getRows() throws ODKDatastoreException, PermissionDeniedException, InconsistentStateException, ODKTaskLockException, BadColumnNameException {
    List<Row> rows;
    rows = dm.getRows();
    return new RowResourceList(getResources(rows));
  }

  @Override
  public RowResource getRow(String rowId) throws ODKDatastoreException, PermissionDeniedException, InconsistentStateException, ODKTaskLockException, BadColumnNameException {
    Row row = dm.getRow(rowId);
    RowResource resource = getResource(row);
    return resource;
  }

  @Override
  public RowResource createOrUpdateRow(String rowId, Row row) throws ODKTaskLockException,
      ODKDatastoreException, ETagMismatchException, PermissionDeniedException,
      BadColumnNameException, InconsistentStateException {
    row.setRowId(rowId);
    Row newRow = dm.insertOrUpdateRow(row);
    RowResource resource = getResource(newRow);
    return resource;
  }

  @Override
  public String deleteRow(String rowId) throws ODKDatastoreException, ODKTaskLockException,
      PermissionDeniedException, InconsistentStateException, BadColumnNameException {
    return dm.deleteRow(rowId);
  }

  private RowResource getResource(Row row) {
    String tableId = dm.getTableId();
    String rowId = row.getRowId();
    UriBuilder ub = info.getBaseUriBuilder();
    ub.path(TableService.class);
    URI self = ub.clone().path(TableService.class, "getData").path(DataService.class, "getRow")
        .build(tableId, rowId);
    URI table = ub.clone().path(TableService.class, "getTable").build(tableId);
    RowResource resource = new RowResource(row);
    resource.setSelfUri(self.toASCIIString());
    resource.setTableUri(table.toASCIIString());
    return resource;
  }

  private ArrayList<RowResource> getResources(List<Row> rows) {
    ArrayList<RowResource> resources = new ArrayList<RowResource>();
    for (Row row : rows) {
      resources.add(getResource(row));
    }
    return resources;
  }
}