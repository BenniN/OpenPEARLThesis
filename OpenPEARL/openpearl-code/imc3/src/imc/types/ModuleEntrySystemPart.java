package imc.types;
import imc.utilities.NodeUtils;

import java.util.ArrayList;

import org.w3c.dom.Node;



/**
 * entry in the system part of a module
 * 
 * @author mueller
 *
 */
public class ModuleEntrySystemPart {
	/**
	 * node of the definition of this item in the DOM tree 
	 */
	private Node   node;
	
	/**
	 * source line number of the definition of this element  
	 */
	private int line;
	
	/**
	 * list of the parameters of the platform element   
	 */
	private ArrayList<Parameter> parameters;
	
	/**
	 * link to the {@link ModuleEntrySystemPart} of an association if specified, else null
	 */
	private Association association;
	
	/**
	 * needed for code generation. Preset to false, will be set to true to iterate over all
	 * {@link ModuleEntrySystemPart}s to avoid duplicates 
	 */
	private boolean codeGenerated;
	
	/**
	 * the user supplied name of the system part element
	 */
	private String userName;
	
	/**
	 * flag to detect unused system part elements
	 */
	private boolean isUsedBySpc;

	private String prefix;
	
	ModuleEntrySystemPart() {
		parameters = new ArrayList<Parameter>();
		codeGenerated=false;
		isUsedBySpc = false;		
		prefix = "_";
	}
	
	
	ModuleEntrySystemPart(int line, Node location) {
		parameters = new ArrayList<Parameter>();	
		this.line = line;
		node = location;
		prefix = "_";
	}
	
	public ModuleEntrySystemPart(String userName, int line, Node location) {
		parameters = new ArrayList<Parameter>();	
		this.line = line;
		node = location;
		this.userName = userName;
		prefix = "_";
	}

	public String getUserName() {
		return userName;
	}
	

	public void setUserName(String userName) {
		this.userName = userName;
	}


	public int getLine() {
		return line;
	}

	public Node getNode() {
		return node;
	}

	/**
	 * obtain the name of the platform specific element
	 * 
	 * @return the name of the platform specific system name
	 */
	public String getNameOfSystemelement() {

		Node sys = NodeUtils.getChildByName(node,"sysname");
		if (sys == null) {
			// no sysname tag --> lookup system name of association. 
			// we are on the correct node
			sys = node;
		}
		String sysname = sys.getAttributes().getNamedItem("name").getTextContent();
		
		return sysname;
	}

	public void addParameter(Parameter p1) {
		parameters.add(p1);
		
	}

	public ArrayList<Parameter> getParameters() {
		return parameters;
	}
	
	public String getParametersAsString() {
		String result="";
		if (parameters.size()>0) {
			result = "(";
			for (int i=0; i<parameters.size()-1; i++) {
				result += parameters.get(i)+", ";
			}
			result += parameters.get(parameters.size()-1)+")";
		}
		return result;
	}

	public Association getAssociation() {
		return association;
	}

	public void setAssociation(Association association) {
		this.association = association;
	}

	public boolean isCodeGenerated() {
		return codeGenerated;
	}

	public void setCodeGenerated(boolean codeGenerated) {
		this.codeGenerated = codeGenerated;
	}


	public boolean isUsed() {
		return isUsedBySpc;
	}


	public void setIsUsed() {
		this.isUsedBySpc = true;
	}


	public void setPrefix(String prefix) {
		this.prefix = prefix;
		
	}


	public String getPrefix() {
		return prefix;
	}
	


}
