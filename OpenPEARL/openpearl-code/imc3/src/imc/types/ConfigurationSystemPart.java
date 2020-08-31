package imc.types;


import org.w3c.dom.Node;

/**
 * a configuration entry is in most cases like a user defined name.
 * The only difference is the lacking user defined name.
 * 
 * @author mueller
 *
 */
public class ConfigurationSystemPart  extends ModuleEntrySystemPart {

	public ConfigurationSystemPart(int line, Node location) {
		super(line, location);
	}

}
