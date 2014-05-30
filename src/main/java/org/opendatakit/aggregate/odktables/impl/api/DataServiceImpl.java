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

import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
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
  private final String schemaETag;
  private final DataManager dm;
  private final UriInfo info;

  public DataServiceImpl(String appId, String tableId, String schemaETag, UriInfo info, TablesUserPermissions userPermissions, CallingContext cc)
      throws ODKEntityNotFoundException, ODKDatastoreException {
    this.schemaETag = schemaETag;
    this.dm = new DataManager(appId, tableId, userPermissions, cc);
    this.info = info;
  }

  @Override
  public Response getRows() throws ODKDatastoreException, PermissionDeniedException, InconsistentStateException, ODKTaskLockException, BadColumnNameException {
    List<Row> rows;
    rows = dm.getRows();
    RowResourceList rowResourceList = new RowResourceList(getResources(rows));
    return Response.ok(rowResourceList).build();
  }

  @Override
  public Response getRow(@PathParam("rowId") String rowId) throws ODKDatastoreException, PermissionDeniedException, InconsistentStateException, ODKTaskLockException, BadColumnNameException {
    rowId = ServiceUtils.decodeSegment(rowId);
    Row row = dm.getRow(rowId);
    RowResource resource = getResource(row);
    return Response.ok(resource).build();
  }

  @Override
  public Response createOrUpdateRow(@PathParam("rowId") String rowId, Row row) throws ODKTaskLockException,
      ODKDatastoreException, ETagMismatchException, PermissionDeniedException,
      BadColumnNameException, InconsistentStateException {
    rowId = ServiceUtils.decodeSegment(rowId);
    row.setRowId(rowId);
    Row newRow = dm.insertOrUpdateRow(row);
    RowResource resource = getResource(newRow);
    return Response.ok(resource).build();
  }

  @Override
  public Response deleteRow(@PathParam("rowId") String rowId, @QueryParam(QUERY_ROW_ETAG) String rowETag) throws ODKDatastoreException, ODKTaskLockException,
      PermissionDeniedException, InconsistentStateException, BadColumnNameException, ETagMismatchException {
    rowId = ServiceUtils.decodeSegment(rowId);
    rowETag = ServiceUtils.decodeSegment(rowETag);
    String dataETagOnTableOfModification = dm.deleteRow(rowId, rowETag);
    return Response.ok(dataETagOnTableOfModification).build();
  }

  private RowResource getResource(Row row) {
    String appId = dm.getAppId();
    String tableId = dm.getTableId();
    String rowId = row.getRowId();
    // segments need to be cleansed of URI-inappropriate characters...
    String appSegment = ServiceUtils.encodeSegment(appId);
    String tableSegment = ServiceUtils.encodeSegment(tableId);
    String schemaSegment = ServiceUtils.encodeSegment(schemaETag);
    String rowSegment = ServiceUtils.encodeSegment(rowId);

    UriBuilder ub = info.getBaseUriBuilder();
    ub.path(TableService.class);
    URI self = ub.clone().path(TableService.class, "getData").path(DataService.class, "getRow")
        .build(appSegment, tableSegment, schemaSegment, rowSegment);
    URI table = ub.clone().path(TableService.class, "getTable").build(appSegment, tableSegment);
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