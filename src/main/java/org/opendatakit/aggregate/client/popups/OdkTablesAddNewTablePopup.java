package org.opendatakit.aggregate.client.popups;

import java.util.ArrayList;
import java.util.List;

import org.opendatakit.aggregate.client.AggregateUI;
import org.opendatakit.aggregate.client.SecureGWT;
import org.opendatakit.aggregate.client.odktables.ColumnClient;
import org.opendatakit.aggregate.client.odktables.TableDefinitionClient;
import org.opendatakit.aggregate.client.odktables.TableEntryClient;
import org.opendatakit.aggregate.client.odktables.TableTypeClient;
import org.opendatakit.aggregate.client.widgets.AggregateButton;
import org.opendatakit.aggregate.client.widgets.ClosePopupButton;
import org.opendatakit.aggregate.client.widgets.OdkTablesAddTableButton;
import org.opendatakit.aggregate.client.widgets.OdkTablesTableNameBox;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HTML;

/**
 * This popup creates a table and adds it to the datastore.
 * @author sudar.sam@gmail.com
 *
 */
public class OdkTablesAddNewTablePopup extends AbstractPopupBase {
	  
	  private TableDefinitionClient tableDef;
	  
	  // the textbox for the table name
	  private OdkTablesTableNameBox nameBox;

	  public OdkTablesAddNewTablePopup() {
	    super();

	    AggregateButton addTableButton = new OdkTablesAddTableButton();
	    addTableButton.addClickHandler(new ExecuteAdd());
	    
	    nameBox = new OdkTablesTableNameBox(this);

	    FlexTable layout = new FlexTable();

	    HTML message = new HTML("You are adding a new table.");
	    layout.setWidget(0, 0, message);
	    layout.setWidget(0, 1, nameBox);
	    layout.setWidget(0, 2, addTableButton);
	    layout.setWidget(0, 3, new ClosePopupButton(this));

	    setWidget(layout);
	  }

	  private class ExecuteAdd implements ClickHandler {

	    @Override
	    public void onClick(ClickEvent event) {
	    	
          String tableName = nameBox.getValue();
          // TODO: for now, just add the tableKey and dbTableName to be the 
          // same as the tableName. The correct workflow and checks need to
          // be performed.
          List<ColumnClient> columns = new ArrayList<ColumnClient>(0);
          tableDef = new TableDefinitionClient(null, columns, tableName,
              tableName, TableTypeClient.DATA,
              null);
          
	      // Set up the callback object.
	      AsyncCallback<TableEntryClient> callback = new AsyncCallback<TableEntryClient>() {
	        @Override
	        public void onFailure(Throwable caught) {
	          AggregateUI.getUI().reportError(caught);
	        }

	        @Override
	        public void onSuccess(TableEntryClient table) {
	          AggregateUI.getUI().clearError();

	          
	          AggregateUI.getUI().getTimer().refreshNow();
	        }
	      };
	      Window.alert("before call");
	      // Make the call to the form service. null tableId so that the 
	      // server knows to generate a random UUID.
	      SecureGWT.getServerTableService().createTable(null, 
	    		  tableDef, callback);
	      hide();
	    }
	  }
}
