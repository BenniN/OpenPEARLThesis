package imc.types;

import org.w3c.dom.Node;

/**
 * represent a specification in the problem part of a module
 * 
 * @author mueller
 *
 */
public class SpcProblemPart extends SpcDclProblemPart {

	/**
	 * represents a specification of a global symbol
	 * 
	 * @param sn the name of the symbol
	 * @param line the line number of the specification in the source file 
	 * @param t the type of the symbol
	 * @param location the node in the DOM tree
	 * @param module the module which contains the specification
	 * @param global the parameter of the GLOBAL attribute in the specification
	 */
	public SpcProblemPart(String sn, int line, String t, Node location, Module module, String global) {
		super(sn, line, t, location, module, global);
		
	}
}
