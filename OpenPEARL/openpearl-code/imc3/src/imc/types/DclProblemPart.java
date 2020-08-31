package imc.types;

import org.w3c.dom.Node;

/**
 * represent a declaration in the problem part of a module
 * 
 * @author mueller
 * 
 */
public class DclProblemPart extends SpcDclProblemPart {
	
	/**
	 * flag to detect unuser global definitions
	 */
	private boolean isUsedBySpc;
	
	/**
	 * represent a declaration in the problem part of a module
	 * 
	 * 
	 * @param sn the name of the symbol
	 * @param line the line number of the declaration in the source file 
	 * @param t the type of the symbol
	 * @param location the node in the DOM tree
	 * @param module the module which contains the declaration
	 * @param global the parameter of the GLOBAL attribute in the declaration
	 */
	public DclProblemPart(String sn, int line, String t, Node location, Module module, String global) {
		super(sn, line, t, location, module, global);
		isUsedBySpc = false;
	}

	public boolean isUsedBySpc() {
		return isUsedBySpc;
	}

	public void setUsedBySpc() {
		this.isUsedBySpc = true;
	}
	
}
