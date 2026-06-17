def tutorial = ec.entity.makeValue("tutorial.Tutorial") // Initialize the object.	Empty in RAM.
tutorial.setFields(context, true, null, null) // Fill the object with data.	Filled in RAM.
if (!tutorial.tutorialId) tutorial.setSequencedIdPrimary() // Generate the Unique ID.	Ready to Save.
tutorial.create() // Write to the Database.	Saved in MySQL.Write to the Database.	Saved in MySQL.