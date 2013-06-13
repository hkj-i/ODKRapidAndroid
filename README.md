ODK for weReport Summary
Author: Hee Jung

This README contains imformation about the code changes made
to ODK for the purposes of building weReport.

Changes include:
  - additional row to the instance folder: "jrFormName"
		to hold the FormName so that we can query by a different
		property of an instance file other than the jrFormId
		the value contained in "jrFormName" column is equivalent to
		the displayname of the form that the instance is using
		
	- FormEntryActivity.java
		when deciding which form's blank xml to pull, changed the code
		to query by form's display name instead of the form id
		here we assume that each form has a unique display name
		
	- SaveToDiskTask.java
		changed the code so that when an instance has been saved, it calls
		the update function of UpdateRapidAndroidDBTask.java
		
	- UpdateRapidAndroidDBTask.java (new file)
		updates the columns of RapidAndroid's database for the message
		that is linked to the instance file that has just been changed.
		
		also updates the "is_finalized" column in RapidAndroid's database
		
	- InstanceUploaderTask.java
		when an instance file has been uploaded to Aggregate, updates the
		"is_sent" field in RapidAndroid's database for the message
