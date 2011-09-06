/*
 * Copyright (C) 2011 University of Washington
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

package org.opendatakit.aggregate.client.widgets;

import org.opendatakit.aggregate.client.AggregateUI;
import org.opendatakit.aggregate.client.popups.ExternalServicePopup;
import org.opendatakit.aggregate.client.popups.HelpBalloon;
import org.opendatakit.common.security.common.GrantedAuthorityName;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.PopupPanel;

public class PublishButton extends AbstractButtonBase implements ClickHandler {

	private static final String TOOLTIP_TEXT = "Publish the data";

	private static final String HELP_BALLOON_TXT = "This will publish the data to Google Fusion Tables or " +
			"Google Spreadsheets.";

	private String formId;

	public PublishButton(String formId) {
		super("<img src=\"images/green_right_arrow.png\" /> Publish", TOOLTIP_TEXT);
		this.formId = formId;
		boolean enabled = AggregateUI.getUI().getUserInfo()
		.getGrantedAuthorities().contains(GrantedAuthorityName.ROLE_DATA_OWNER);
		setEnabled(enabled);
		helpBalloon = new HelpBalloon(this, HELP_BALLOON_TXT);
	}

	@Override
	public void onClick(ClickEvent event) {
		super.onClick(event);

		final PopupPanel popup = new ExternalServicePopup(formId);
		popup.setPopupPositionAndShow(new PopupPanel.PositionCallback() {
			@Override
			public void setPosition(int offsetWidth, int offsetHeight) {
				int left = ((Window.getScrollLeft() + Window.getClientWidth() - offsetWidth) / 2);
				int top = ((Window.getScrollTop() + Window.getClientHeight() - offsetHeight) / 2);
				popup.setPopupPosition(left, top);
			}
		});
	}
}
