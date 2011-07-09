package org.opendatakit.aggregate.client.widgets;

import org.opendatakit.aggregate.client.popups.ConfirmFormDeletePopup;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.PopupPanel;

public class DeleteFormButton extends AButtonBase implements ClickHandler {
 
  private String formId;

  public DeleteFormButton(String formId) {
    super("<img src=\"images/red_x.png\" /> Delete");
    this.formId = formId;
    addStyleDependentName("negative");
    addClickHandler(this);
  }

  @Override
  public void onClick(ClickEvent event) {
    super.onClick(event);
    
     // TODO: display pop-up with text from b...
     final ConfirmFormDeletePopup popup = new ConfirmFormDeletePopup(formId);
     popup.setPopupPositionAndShow(new PopupPanel.PositionCallback() {
        @Override
        public void setPosition(int offsetWidth, int offsetHeight) {
           int left = ((Window.getClientWidth() - offsetWidth) / 2);
           int top = ((Window.getClientHeight() - offsetHeight) / 2);
           popup.setPopupPosition(left, top);
        }
     });
  }

}