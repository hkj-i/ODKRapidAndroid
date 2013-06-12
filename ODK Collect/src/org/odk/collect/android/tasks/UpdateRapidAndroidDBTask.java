package org.odk.collect.android.tasks;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.odk.collect.android.application.Collect;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.sax.Element;
import android.util.Log;

public class UpdateRapidAndroidDBTask {
	private Uri mUri;
	private String mXML;
	private boolean shouldParse;
	private Document mDoc;
    
    // for rapidAndroid
	private static final String RAPIDANDROID = "content://org.rapidandroid.provider.RapidSms/";
    private static final String RAPIDANDROID_MESSAGE =  RAPIDANDROID + "message";
    private static final String RAPIDANDROID_FIELD =   RAPIDANDROID + "field";
    private static final String RAPIDANDROID_FIELDTYPE =   RAPIDANDROID + "fieldtype";
    private static final String RAPIDANDROID_FORM =  RAPIDANDROID + "form";
    private static final String RAPIDANDROID_FORMDATA =  RAPIDANDROID + "formdata/";
    

	
	public UpdateRapidAndroidDBTask(Uri uri) {
		mUri = uri;
		if (Collect.getInstance().getContentResolver().getType(mUri) == InstanceColumns.CONTENT_ITEM_TYPE) {
			// for now, we are only updating to database
			// only xml files that were received via text
			// and not inserted through odk
			shouldParse = true;
		} else {
			shouldParse = false;
		}
	}
    /**
	 * Called by onPostExecute
	 * updates rapid android database
	 */
	
	private boolean getXML(String filepath) {
		try {
			// get xml string from file
			Scanner xmlfile = new Scanner(new FileReader(filepath));
		    StringBuilder sb = new StringBuilder();
		    while (xmlfile.hasNextLine()) {
		      sb.append(xmlfile.nextLine()).append("\n");
		    }
		    mXML = sb.toString();
		    // get the DOM	        
	        return true;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			Log.i("Update Rapid Android", "couldn't open xml file");
			return false;
		}
	}

	private boolean createDOM() {
		Document doc = null;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            InputSource is = new InputSource();
	        is.setCharacterStream(new StringReader(mXML));
	        mDoc =  db.parse(is);
	        return true;
        } catch (ParserConfigurationException e) {
            Log.e("Error: ", e.getMessage());
        } catch (SAXException e) {
            Log.e("Error: ", e.getMessage());
        } catch (IOException e) {
            Log.e("Error: ", e.getMessage());
        }
        return false;
	}
	
	public void update() {
		if (shouldParse) {
			Context context = Collect.getInstance();
			// 1) ODK db: find the form name corresponding to this instance file
			//	- get the instance id and look in the database for its formname
			String instance_id = mUri.toString().substring(InstanceProviderAPI.InstanceColumns.CONTENT_URI_STRING.length());
			Log.i("UpdateRapidAndroidDb", "instance id is " + instance_id);
			Cursor instanceRows = context.getContentResolver().query(
					InstanceProviderAPI.InstanceColumns.CONTENT_URI,
					null,
					"_id = " + instance_id,
					null,
					null);
			// get the display name of the form
			instanceRows.moveToFirst();
			String formname = instanceRows.getString(instanceRows.getColumnIndex("jrFormName"));
			String filepath = instanceRows.getString(instanceRows.getColumnIndex("instanceFilePath"));
			
			Log.i("Update Rapid Android DB", "formname is " + formname);
			
			// 2) RapidAndroid db: find the message corresponding to this id
			//	- find the message id
			// 		- if the message id exists, do the rest
			//	- query the "form" table to find the "@prefix".  The table of interest is formdata_prefix
			//	- go to the table for the specific form, and find the message by its message_id
			//	- update the columns
			Cursor messageRows = context.getContentResolver().query(
					Uri.parse(RAPIDANDROID_MESSAGE),
					null,
					"form_uri = " + instance_id,
					null,
					null);
			
			messageRows.moveToFirst();
			if (messageRows != null || messageRows.getColumnCount() > 0) {
				String messageid = messageRows.getString(messageRows.getColumnIndex("_id"));
				// if the message is found, then try to update the fields
				String[] stringArgs = {formname};
				Cursor formRow = Collect.getInstance().getContentResolver().query(
							Uri.parse(RAPIDANDROID_FORM), 
							null, 
							"formname = ?", 
							stringArgs, 
							null);
				formRow.moveToFirst();
				// get the form's id
				String formid = formRow.getString(formRow.getColumnIndex("_id"));				
				
				// now query in the formdata_prefix table
				// for the message
				Cursor parsedDataRow = context.getContentResolver().query(
						Uri.parse(RAPIDANDROID_FORMDATA + formid),
						null,
						"message_id = " + messageid,
						null,
						null);
				
				// get the column names
				parsedDataRow.moveToFirst();
				String[] columnNames = parsedDataRow.getColumnNames();
				
				
				if (getXML(filepath) && createDOM()) {

					// read file and get doc
					int text = 1;
					int num = 1;
					int select = 1;
					int countm = 1;
					for (int i = 0; i < columnNames.length; i++) {
						if (columnNames[i].startsWith("col_")) {
							String parsedfield = parsedDataRow.getString(i);

							// grab the fieldtype id corresponding to the sequence
							Cursor parsedDataFieldNamesRows = context.getContentResolver().query(
									Uri.parse(RAPIDANDROID_FIELD),
									null,
									"form_id = " + formid + " AND " + "sequence = " + countm,
									null,
									null);
							
							parsedDataFieldNamesRows.moveToFirst();

							String ftype_id = parsedDataFieldNamesRows.getString(
									parsedDataFieldNamesRows.getColumnIndex("fieldtype_id"));
							
							Cursor datatyperow = context.getContentResolver().query(
									Uri.parse(RAPIDANDROID_FIELDTYPE),
									null,
									"_id = " + ftype_id,
									null,
									null);

							datatyperow.moveToFirst();

							// according to the type, choose num or select or text
							String type =  datatyperow.getString(datatyperow.getColumnIndex("datatype"));
							String nodelabel = "";
							if (type.equals("word")) {
								nodelabel = "text" + text;
								text++;
							} else if (type.equals("boolean")) {
								// yes no
								nodelabel = "select" + select;
								select++;
							} else {
								// number
								nodelabel = "num" + num;
								num++;
							}
							countm++;
							// look for the tag and insert the value into rapid android db
							NodeList nl = mDoc.getElementsByTagName(nodelabel);
							// and we assume there is only one of each label
							String value = getElementValue(nl.item(0));
							
							if (!value.equals("")) {
								// if it is not an empty string, then insert back into db
								ContentValues rapidValues = new ContentValues();
								rapidValues.put(columnNames[i], value);
								context.getContentResolver().update(
										Uri.parse(RAPIDANDROID_FORMDATA + formid),
										rapidValues,
							    		 "message_id = " + messageid,
							    		 null);
							} // if value.equals
							
						} // it is a valid column to be updated
						
					} //forloop
						
				}
				// dom creation
			}
			// message not found
		}
		// not inserted via sms
	}
	
	private String getElementValue(Node elem) {
		Node child;
        if( elem != null){
            if (elem.hasChildNodes()){
                for( child = elem.getFirstChild(); child != null; child = child.getNextSibling() ){
                    if( child.getNodeType() == Node.TEXT_NODE  ){
                        return child.getNodeValue();
                    }
                }
            }
        }
        return "";
	}

}

