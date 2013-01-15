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

import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.opendatakit.aggregate.constants.BeanDefs;
import org.opendatakit.common.utils.HttpClientFactory;
import org.opendatakit.common.web.CallingContext;

import com.google.api.client.http.HttpMethods;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;

/**
 * Thread-safe HTTP transport based on the Apache HTTP Client library.
 *
 * <p>
 * Implementation is thread-safe, as long as any parameter modification to the
 * {@link #getHttpClient() Apache HTTP Client} is only done at initialization time. For maximum
 * efficiency, applications should use a single globally-shared instance of the HTTP transport.
 * </p>
 *
 * <p>
 * Default settings are specified in {@link #newDefaultHttpClient()}. Use the
 * {@link #ApacheHttpTransport(HttpClient)} constructor to override the Apache HTTP Client used.
 * Alternatively, use {@link #ApacheHttpTransport()} and change the {@link #getHttpClient()}. Please
 * read the <a
 * href="http://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html">Apache HTTP
 * Client connection management tutorial</a> for more complex configuration questions, such as how
 * to set up an HTTP proxy.
 * </p>
 *
 * @since 1.0
 * @author Yaniv Inbar
 *
 * Revised to retrieve the DefaultHttpClient factory bean for creating the DefaultHttpClient
 * so that we can supply a Google AppEngine-safe implementation.
 *
 *  @author mitchellsundt@gmail.com
 */
public final class SafeApacheHttpTransport extends HttpTransport {

  private static HttpClientFactory factory = null;

  /** Apache HTTP client. */
  private final HttpClient httpClient;

  /**
   * Constructor that uses {@link #newDefaultHttpClient()} for the Apache HTTP client.
   *
   * @since 1.3
   */
  public SafeApacheHttpTransport() {
    this(newDefaultHttpClient());
  }

  public static void setHttpClientFactory(CallingContext cc) {
    factory = (HttpClientFactory) cc.getBean(BeanDefs.HTTP_CLIENT_FACTORY);
  }

  /**
   * Constructor that allows an alternative Apache HTTP client to be used.
   *
   * <p>
   * Note that a few settings are overridden:
   * </p>
   * <ul>
   * <li>HTTP version is set to 1.1 using {@link HttpProtocolParams#setVersion} with
   * {@link HttpVersion#HTTP_1_1}.</li>
   * <li>Redirects are disabled using {@link ClientPNames#HANDLE_REDIRECTS}.</li>
   * <li>{@link HttpConnectionParams#setConnectionTimeout}
   * are set on each request based on {@link HttpRequest#getConnectTimeout()}.</li>
   * <li>{@link HttpConnectionParams#setSoTimeout} is set on each request based on
   * {@link HttpRequest#getReadTimeout()}.</li>
   * </ul>
   *
   * @param httpClient Apache HTTP client to use
   *
   * @since 1.6
   */
  public SafeApacheHttpTransport(HttpClient httpClient) {
    this.httpClient = httpClient;
    HttpParams params = httpClient.getParams();
    HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
    params.setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);
  }

  /**
   * Creates a new instance of the Apache HTTP client that is used by the
   * {@link #ApacheHttpTransport()} constructor.
   *
   * <p>
   * Use this constructor if you want to customize the default Apache HTTP client. Settings:
   * </p>
   * <ul>
   * <li>The socket buffer size is set to 8192 using
   * {@link HttpConnectionParams#setSocketBufferSize}.</li>
   * <li><The retry mechanism is turned off by setting
   * {@code new DefaultHttpRequestRetryHandler(0, false)}</li>
   * </ul>
   *
   * @return new instance of the Apache HTTP client
   * @since 1.6
   */
  public static DefaultHttpClient newDefaultHttpClient() {
    // Turn off stale checking. Our connections break all the time anyway,
    // and it's not worth it to pay the penalty of checking every time.
    HttpParams params = new BasicHttpParams();
    HttpConnectionParams.setStaleCheckingEnabled(params, false);
    HttpConnectionParams.setSocketBufferSize(params, 8192);
    DefaultHttpClient defaultHttpClient = (DefaultHttpClient) factory.createHttpClient(params);
    defaultHttpClient.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
    return defaultHttpClient;
  }

  @Override
  public boolean supportsMethod(String method) {
    return true;
  }

  @Override
  protected ApacheHttpRequest buildRequest(String method, String url) {
    HttpRequestBase requestBase;
    if (method.equals(HttpMethods.DELETE)) {
      requestBase = new HttpDelete(url);
    } else if (method.equals(HttpMethods.GET)) {
      requestBase = new HttpGet(url);
    } else if (method.equals(HttpMethods.HEAD)) {
      requestBase = new HttpHead(url);
    } else if (method.equals(HttpMethods.POST)) {
      requestBase = new HttpPost(url);
    } else if (method.equals(HttpMethods.PUT)) {
      requestBase = new HttpPut(url);
    } else if (method.equals(HttpMethods.TRACE)) {
      requestBase = new HttpTrace(url);
    } else if (method.equals(HttpMethods.OPTIONS)) {
      requestBase = new HttpOptions(url);
    } else {
      requestBase = new HttpExtensionMethod(method, url);
    }
    return new ApacheHttpRequest(httpClient, requestBase);
  }

  @Deprecated
  @Override
  public boolean supportsHead() {
    return true;
  }

  @Deprecated
  @Override
  public boolean supportsPatch() {
    return true;
  }

  @Deprecated
  @Override
  public ApacheHttpRequest buildDeleteRequest(String url) {
    return buildRequest("DELETE", url);
  }

  @Deprecated
  @Override
  public ApacheHttpRequest buildGetRequest(String url) {
    return buildRequest("GET", url);
  }

  @Deprecated
  @Override
  public ApacheHttpRequest buildHeadRequest(String url) {
    return buildRequest("HEAD", url);
  }

  @Deprecated
  @Override
  public ApacheHttpRequest buildPatchRequest(String url) {
    return buildRequest("PATCH", url);
  }

  @Deprecated
  @Override
  public ApacheHttpRequest buildPostRequest(String url) {
    return buildRequest("POST", url);
  }

  @Deprecated
  @Override
  public ApacheHttpRequest buildPutRequest(String url) {
    return buildRequest("PUT", url);
  }

  /**
   * Shuts down the connection manager and releases allocated resources. This includes closing all
   * connections, whether they are currently used or not.
   *
   * @since 1.4
   */
  @Override
  public void shutdown() {
    httpClient.getConnectionManager().shutdown();
  }

  /**
   * Returns the Apache HTTP client.
   *
   * @since 1.5
   */
  public HttpClient getHttpClient() {
    return httpClient;
  }
}
