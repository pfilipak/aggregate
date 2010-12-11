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
package org.opendatakit.aggregate.submission;


/**
 * Helper class for extracting parts of a SubmissionKey
 * 
 * @author mitchellsundt@gmail.com
 * @author wbrunette@gmail.com
 * 
 */
public class SubmissionKeyPart {

	public static final String K_SLASH = "/";
	public static final String K_OPEN_BRACKET = "[";
	public static final String K_OPEN_BRACKET_KEY_EQUALS = "[@key=";
	public static final String K_OPEN_BRACKET_ORDINAL_EQUALS = "[@ordinal=";
	public static final String K_AND_VERSION_EQUALS = " and @version=";
	public static final String K_OPEN_BRACKET_VERSION_EQUALS = "[@version=";
	public static final String K_AND_UI_VERSION_EQUALS = " and @uiVersion=";
	public static final String K_CLOSE_BRACKET = "]";
	final String elementName;
	final Long modelVersion; // only valid in form part!  Not propagated to nested elements.
	final Long uiVersion;  // only valid in form part!  Not propagated to nested elements.
	final String auri;
	final Long ordinal;
	final String version;

	SubmissionKeyPart(String part) {
		int idx = part.indexOf(K_OPEN_BRACKET);
		if (idx == -1) {
			if (part.indexOf(K_CLOSE_BRACKET) == -1) {
				elementName = part;
				modelVersion = null;
				uiVersion = null;
				auri = null;
				ordinal = null;
				version = null;
			} else {
				throw new IllegalArgumentException("submission key part "
						+ part + " not well formed");
			}
		} else {
			elementName = part.substring(0, idx);
			String remainder = part.substring(idx);
			if (remainder.startsWith(K_OPEN_BRACKET_KEY_EQUALS)) {
				if (!remainder.endsWith(K_CLOSE_BRACKET)) {
					throw new IllegalArgumentException("submission key part "
							+ part + " is not well formed");
				}
				auri = remainder.substring(K_OPEN_BRACKET_KEY_EQUALS.length(), remainder.length() - 1);
				modelVersion = null;
				uiVersion = null;
				ordinal = null;
				version = null;
			} 
			else if (remainder.startsWith(K_OPEN_BRACKET_ORDINAL_EQUALS)) {
				if (!remainder.endsWith(K_CLOSE_BRACKET)) {
					throw new IllegalArgumentException("submission key part "
							+ part + " is not well formed");
				}
				auri = null;
				modelVersion = null;
				uiVersion = null;
				String ordinalStr = remainder.substring(K_OPEN_BRACKET_ORDINAL_EQUALS.length(), remainder.length() - 1);
				if ( ordinalStr.contains(K_AND_VERSION_EQUALS) ) {
					idx = ordinalStr.indexOf(K_AND_VERSION_EQUALS);
					version = ordinalStr.substring(idx+K_AND_VERSION_EQUALS.length());
					ordinalStr = ordinalStr.substring(0,idx);
				} else {
					version = null;
				}
				ordinal = Long.valueOf(ordinalStr);
			}
			else if (remainder.startsWith(K_OPEN_BRACKET_VERSION_EQUALS)) {
				if (!remainder.endsWith(K_CLOSE_BRACKET)) {
					throw new IllegalArgumentException("submission key part "
							+ part + " is not well formed");
				}
				auri = null;
				ordinal = null;
				version = null;
				String modelVersionStr = remainder.substring(K_OPEN_BRACKET_VERSION_EQUALS.length(), remainder.length() - 1);
				String uiVersionStr = null;
				if ( modelVersionStr.contains(K_AND_UI_VERSION_EQUALS) ) {
					idx = modelVersionStr.indexOf(K_AND_UI_VERSION_EQUALS);
					uiVersionStr = modelVersionStr.substring(idx+K_AND_UI_VERSION_EQUALS.length());
					modelVersionStr = modelVersionStr.substring(0,idx);
				}
				modelVersion = "null".equals(modelVersionStr) ? null : Long.valueOf(modelVersionStr);
				uiVersion = "null".equals(uiVersionStr) ? null : Long.valueOf(uiVersionStr);
			}
			else {
				throw new IllegalArgumentException("submission key part "
						+ part + " is not well formed");
			}
		}
	}

	public String getElementName() {
		return elementName;
	}

	public String getAuri() {
		return auri;
	}
	
	public Long getOrdinalNumber() {
		return ordinal;
	}

	public String getVersion() {
		return version;
	}
}