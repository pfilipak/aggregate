/*
 * Copyright (C) 2013 University of Washington
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

package org.opendatakit.aggregate.client.odktables;

import java.util.List;

import org.opendatakit.aggregate.client.exception.BadColumnNameExceptionClient;
import org.opendatakit.aggregate.client.exception.EntityNotFoundExceptionClient;
import org.opendatakit.aggregate.client.exception.ETagMismatchExceptionClient;
import org.opendatakit.aggregate.client.exception.PermissionDeniedExceptionClient;
import org.opendatakit.aggregate.client.exception.RequestFailureException;
import org.opendatakit.common.persistence.client.exception.DatastoreFailureException;
import org.opendatakit.common.security.client.exception.AccessDeniedException;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

/**
 * This will be the DataService for the server. It will act the same way as
 * org.opendatakit.aggregate.odktables.api.DataService, except that it will be
 * for interacting with the table information on the server, rather than with a
 * phone.
 *
 * @author sudar.sam@gmail.com
 *
 */

@RemoteServiceRelativePath("serverdataservice")
public interface ServerDataService extends RemoteService {

  List<RowClient> getRows(String tableId) throws AccessDeniedException, RequestFailureException,
      DatastoreFailureException, PermissionDeniedExceptionClient, EntityNotFoundExceptionClient;

  TableContentsClient getRow(String tableId, String rowId) throws AccessDeniedException,
      RequestFailureException, DatastoreFailureException, PermissionDeniedExceptionClient,
      EntityNotFoundExceptionClient;

  RowClient createOrUpdateRow(String tableId, String rowId, RowClient row)
      throws AccessDeniedException, RequestFailureException, DatastoreFailureException,
      PermissionDeniedExceptionClient, ETagMismatchExceptionClient, BadColumnNameExceptionClient,
      EntityNotFoundExceptionClient;

  void deleteRow(String tableId, String rowId) throws AccessDeniedException,
      RequestFailureException, DatastoreFailureException, PermissionDeniedExceptionClient,
      EntityNotFoundExceptionClient;

  List<String> getColumnNames(String tableId) throws DatastoreFailureException,
      EntityNotFoundExceptionClient;

  List<FileSummaryClient> getNonMediaFiles(String tableId) throws AccessDeniedException,
      RequestFailureException, DatastoreFailureException, PermissionDeniedExceptionClient,
      EntityNotFoundExceptionClient;

  List<FileSummaryClient> getMedialFilesKey(String tableId, String key)
      throws AccessDeniedException, RequestFailureException, DatastoreFailureException,
      PermissionDeniedExceptionClient, EntityNotFoundExceptionClient;

  List<String> getFileRowInfoColumnNames();

  TableContentsClient getTableContents(String tableId) throws AccessDeniedException,
      RequestFailureException, DatastoreFailureException, PermissionDeniedExceptionClient,
      EntityNotFoundExceptionClient;

  TableContentsForFilesClient getFileInfoContents(String tableId) throws AccessDeniedException,
      RequestFailureException, DatastoreFailureException, PermissionDeniedExceptionClient,
      EntityNotFoundExceptionClient;

  void deleteTableFile(String tableId, String rowId) throws AccessDeniedException,
      RequestFailureException, DatastoreFailureException, PermissionDeniedExceptionClient,
      EntityNotFoundExceptionClient;

}
