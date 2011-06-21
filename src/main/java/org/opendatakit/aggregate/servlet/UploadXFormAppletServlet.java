/*
 * Copyright (C) 2010 University of Washington
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
package org.opendatakit.aggregate.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.opendatakit.aggregate.ContextFactory;
import org.opendatakit.common.constants.HtmlUtil;
import org.opendatakit.common.web.CallingContext;


/**
 * The only purpose of this servlet is to insert Set-Cookie META tags into the
 * HEAD section of the Upload XForm Applet page being vended.  UPLOAD_BODY is a
 * string representation of the HTML body for the Upload XForm Applet.
 * 
 * Copied from BriefcaseServlet.java with a few changes.
 * 
 * @author the.dylan.price@gmail.com
 * @author mitchellsundt@gmail.com
 * @author wbrunette@gmail.com
 * 
 */
public class UploadXFormAppletServlet extends ServletUtilBase {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5121979982542017092L;

	/**
	 * URI from base
	 */
	public static final String ADDR = "UploadXFormApplet";

	/**
	 * Title for generated webpage
	 */
	private static final String TITLE_INFO = "ODK Upload XForm";
	
	/**
	 * Upload Applet body
	 */
	private static final String UPLOAD_PREAMBLE =
		"<h3>Upload forms into ODK Aggregate</h3>";
	private static final String UPLOAD_BODY = 
"<p>Media files for icons, images, audio clips, video clips and " +
"form logos are expected to be in a folder in the same directory as the form definition file (.xml). " +
"If the form definition file is <code>\"My Form.xml\"</code> then the media folder should be named " +
"<code>\"My Form-media\"</code>.  The applet below will upload the form definition file and the contents " +
"of the media folder, if present, into ODK Aggregate.</p>" +
"<p>On ODK Collect 1.1.6 and higher, the file named <code>\"form_logo.png\"</code>, if present in the " +
"media folder, will be displayed as the form's logo. </p>" +
"\n<p><a href=\"#Cert\">Import the signing certificate</a> to stop the security warnings.</p>" +
"\n<object type=\"application/x-java-applet\" height=\"700\" width=\"1000\" >" +
"\n  <param name=\"jnlp_href\" value=\"upload-xform/opendatakit-upload.jnlp\" />" +
"\n  <param name=\"mayscript\" value=\"true\" />" +
"\n</object>" +
"\n<script language=\"JavaScript\"><!--" +
"\n  document.write('<p>Cookies: '+document.cookie+'</p>')" +
"\n  document.write('<p>Location: '+location.href+'</p>');" +
"\n  //-->" +
"\n</script>";

	/**
	 * Handler for HTTP Get request to create blank page that is navigable
	 * 
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest,
	 *      javax.servlet.http.HttpServletResponse)
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		CallingContext cc = ContextFactory.getCallingContext(this, req);

		String cookieSet = "";
		Cookie[] cookies = req.getCookies();
		if ( cookies != null ) {
			for ( Cookie c : cookies ) {
				String aDef = "<META HTTP-EQUIV=\"Set-Cookie\" CONTENT=\"" + 
								c.getName() + "=" + c.getValue() + "\" />\n";
				resp.addCookie(c);
				cookieSet += aDef;
				
			}
		}
		String headContent = cookieSet;
		beginBasicHtmlResponse(TITLE_INFO, headContent, resp, true, cc); // header
		PrintWriter out = resp.getWriter();
	    out.write(UPLOAD_PREAMBLE);
	    out.write("<p>Click ");
	    out.write(HtmlUtil.createHref(cc.getWebApplicationURL(FormUploadServlet.ADDR), "here"));
	    out.write(" for the plain html webpage.</p>");
	    out.write(UPLOAD_BODY);
	    out.write(APPLET_SIGNING_CERTIFICATE_SECTION);

		finishBasicHtmlResponse(resp);
		resp.setStatus(200);
	}
}
