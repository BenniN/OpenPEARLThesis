package imc.types;


/**
 * provide the link to the ModuleEntrySystemPart of an association
 * 
 * @author mueller
 *
 */

public class Association  {
	ModuleEntrySystemPart username;
	
	public Association(ModuleEntrySystemPart username) {
		this.username = username;
	}

	public ModuleEntrySystemPart getUsername() {
		return username;
	}
}
