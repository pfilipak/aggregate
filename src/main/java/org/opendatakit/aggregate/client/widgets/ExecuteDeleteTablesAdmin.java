package org.opendatakit.aggregate.client.widgets;

import org.opendatakit.aggregate.client.AggregateUI;
import org.opendatakit.aggregate.client.SecureGWT;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.PopupPanel;

public class ExecuteDeleteTablesAdmin extends AButtonBase implements ClickHandler {
 
  private String aggregateUid;
  private PopupPanel popup;
  
  public ExecuteDeleteTablesAdmin(String aggregateUid, PopupPanel popup) {
    super("<img src=\"images/green_right_arrow.png\" /> Delete User");
    this.aggregateUid = aggregateUid;
    this.popup = popup;
    addClickHandler(this);
  }

  @Override
  public void onClick(ClickEvent event) {
    super.onClick(event);
    
    // OK -- we are to proceed.
    // Set up the callback object.
    AsyncCallback<Boolean> callback = new AsyncCallback<Boolean>() {
      @Override
      public void onFailure(Throwable caught) {
        AggregateUI.getUI().reportError(caught);
      }

      @Override
      public void onSuccess(Boolean result) {
        AggregateUI.getUI().clearError();
        if ( result ) {
           Window.alert("Successfully deleted the user");
        } else {
         Window.alert("Error: unable to delete the user!");
        }
        AggregateUI.getUI().getTimer().refreshNow();
      }
    };
    // Make the call to the form service.
    SecureGWT.getOdkTablesAdminService().deleteAdmin(aggregateUid, callback);
    popup.hide();
  }

}
