/*
 * Copyright (c) 2010 Google Inc.
 * Copyright (c) 2013 University of Washington
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

package org.opendatakit.aggregate.externalservice.googleapi;

import java.io.IOException;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import com.google.api.client.http.HttpContent;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.common.base.Preconditions;

/**
 * Copied due to package scoping of original Google impl. mitchellsundt@gmail.com
 * @author Yaniv Inbar
 */
final class ApacheHttpRequest extends LowLevelHttpRequest {
  private final HttpClient httpClient;

  private final HttpRequestBase request;

  ApacheHttpRequest(HttpClient httpClient, HttpRequestBase request) {
    this.httpClient = httpClient;
    this.request = request;
  }

  @Override
  public void addHeader(String name, String value) {
    request.addHeader(name, value);
  }

  @Override
  public void setTimeout(int connectTimeout, int readTimeout) throws IOException {
    HttpParams params = request.getParams();
    HttpConnectionParams.setConnectionTimeout(params, connectTimeout);
    HttpConnectionParams.setSoTimeout(params, readTimeout);
  }

  @Override
  public LowLevelHttpResponse execute() throws IOException {
    return new ApacheHttpResponse(request, httpClient.execute(request));
  }

  @Override
  public void setContent(HttpContent content) throws IOException {
    Preconditions.checkArgument(request instanceof HttpEntityEnclosingRequest,
        "Apache HTTP client does not support %s requests with content.",
        request.getRequestLine().getMethod());
    ContentEntity entity = new ContentEntity(content.getLength(), content);
    entity.setContentEncoding(content.getEncoding());
    entity.setContentType(content.getType());
    ((HttpEntityEnclosingRequest) request).setEntity(entity);
  }
}
