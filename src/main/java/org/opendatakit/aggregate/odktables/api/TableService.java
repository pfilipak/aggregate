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

package org.opendatakit.aggregate.odktables.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.opendatakit.aggregate.odktables.exception.PermissionDeniedException;
import org.opendatakit.aggregate.odktables.exception.TableAlreadyExistsException;
import org.opendatakit.aggregate.odktables.rest.ApiConstants;
import org.opendatakit.aggregate.odktables.rest.entity.TableDefinition;
import org.opendatakit.aggregate.odktables.rest.entity.TableDefinitionResource;
import org.opendatakit.aggregate.odktables.rest.entity.TableResource;
import org.opendatakit.aggregate.odktables.rest.entity.TableResourceList;
import org.opendatakit.common.persistence.exception.ODKDatastoreException;
import org.opendatakit.common.persistence.exception.ODKTaskLockException;

@Path("/tables")
@Produces({MediaType.APPLICATION_JSON, ApiConstants.MEDIA_TEXT_XML_UTF8, ApiConstants.MEDIA_APPLICATION_XML_UTF8})
public interface TableService {

  /**
   *
   * @return {@link TableResourceList} of all tables the user has access to.
   * @throws ODKDatastoreException
   */
  @GET
  public Response /*TableResourceList*/ getTables() throws ODKDatastoreException;

  /**
   *
   * @param tableId
   * @return {@link TableResource} of the requested table.
   * @throws ODKDatastoreException
   * @throws PermissionDeniedException
   */
  @GET
  @Path("{tableId}")
  public Response /*TableResource*/ getTable(@PathParam("tableId") String tableId) throws ODKDatastoreException,
      PermissionDeniedException;

  /**
   *
   * @param tableId
   * @param definition
   * @return {@link TableResource} of the table. This may already exist (with identical schema) or be newly created.
   * @throws ODKDatastoreException
   * @throws TableAlreadyExistsException
   * @throws PermissionDeniedException
   * @throws ODKTaskLockException
   */
  @PUT
  @Path("{tableId}")
  @Consumes({MediaType.APPLICATION_JSON, ApiConstants.MEDIA_TEXT_XML_UTF8, ApiConstants.MEDIA_APPLICATION_XML_UTF8})
  public Response /*TableResource*/ createTable(@PathParam("tableId") String tableId, TableDefinition definition)
      throws ODKDatastoreException, TableAlreadyExistsException, PermissionDeniedException, ODKTaskLockException;

  /**
   *
   * @param tableId
   * @return successful status code if successful.
   * @throws ODKDatastoreException
   * @throws ODKTaskLockException
   * @throws PermissionDeniedException
   */
  @DELETE
  @Path("{tableId}")
  public Response /*void*/ deleteTable(@PathParam("tableId") String tableId) throws ODKDatastoreException,
      ODKTaskLockException, PermissionDeniedException;

  /**
   *
   * @param tableId
   * @return {@link DataService} for manipulating row data in this table.
   * @throws ODKDatastoreException
   */
  @Path("{tableId}/rows")
  public Response /*DataService*/ getData(@PathParam("tableId") String tableId) throws ODKDatastoreException;

  /**
   *
   * @param tableId
   * @return {@link PropertiesService} for accessing table metadata
   * @throws ODKDatastoreException
   */
  @Path("{tableId}/properties")
  public Response /*PropertiesService*/ getProperties(@PathParam("tableId") String tableId)
      throws ODKDatastoreException;

  /**
   *
   * @param tableId
   * @return {@link TableDefinitionResource} for the schema of this table.
   * @throws ODKDatastoreException
   * @throws PermissionDeniedException
   * @throws ODKTaskLockException
   */
  @GET
  @Path("{tableId}/definition")
  public Response /*TableDefinitionResource*/ getDefinition(@PathParam("tableId") String tableId)
      throws ODKDatastoreException, PermissionDeniedException, ODKTaskLockException;

  /**
   *
   * @param tableId
   * @return {@link DiffService} for the row-changes on this table.
   * @throws ODKDatastoreException
   */
  @Path("{tableId}/diff")
  public Response /*DiffService*/ getDiff(@PathParam("tableId") String tableId) throws ODKDatastoreException;

  /**
   *
   * @param tableId
   * @return {@link TableAclService} for ACL management on this table.
   * @throws ODKDatastoreException
   */
  @Path("{tableId}/acl")
  public Response /*TableAclService*/ getAcl(@PathParam("tableId") String tableId) throws ODKDatastoreException;
}
