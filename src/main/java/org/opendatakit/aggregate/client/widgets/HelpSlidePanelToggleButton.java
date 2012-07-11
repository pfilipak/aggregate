package org.opendatakit.aggregate.client.widgets;

import org.opendatakit.aggregate.client.AggregateUI;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Image;

public final class HelpSlidePanelToggleButton extends AggregateImageToggleButton {

  private static final Image HELP_PANEL_ICON_ON = new Image("images/help_icon_on.png");
  private static final Image HELP_PANEL_ICON_OFF = new Image("images/help_icon_off.png");
  private static final String TOOLTIP_TXT = "Help Panel";
  private static final String HELP_BALLOON_TXT = "This displays a help panel below with comprehensive information.";

  public HelpSlidePanelToggleButton() {
    super(HELP_PANEL_ICON_OFF, HELP_PANEL_ICON_ON, TOOLTIP_TXT, HELP_BALLOON_TXT);
  }

  @Override
  public void onClick(ClickEvent event) {
    super.onClick(event);

    if (getValue()) {
      AggregateUI.getUI().displayHelpPanel();
    } else {
      AggregateUI.getUI().hideHelpPanel();
    }

  }

}
