package imc.types;


import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Node;

/**
 * provide easy access to the platform specific elements in the <bf>SYSTEM</bf> part of the PEARL code
 * 
 * A list of all available {@link PlatformSystemElement} is provided.
 * <ul> 
 * <lI>Each element of this list maintains a list of {@link ModuleEntrySystemPart} entries which use this
 * element. This list is needed for checks about multiple usage of platform specific elements
 * <li>a reference  to the node in the DOM tree in the platform definition to get fast access
 *   to further informations like check-definitions
 * <li>the  type information of the elements (dation, signal, interrupt, connection)
 * </ul>
 *     
 * @author mueller
 *
 */

public class PlatformSystemElement {
	/**
	 * the name of the platform specific element
	 */
	private String systemName;
	
	/**
	 * the type as defined in the platform definition file
	 */
	private String type;
	
	/**
	 * the node in the DOM tree which defined this element
	 */
	private Node   node;
	
	/**
	 * list of {@link ModuleEntrySystemPart}s which use this element
	 */
	private List<ModuleEntrySystemPart> usedBy = new ArrayList<ModuleEntrySystemPart>();
	
	/**
	 * create a {@link PlatformSystemElement}
	 * 
	 * 
	 * @param sn the system name
	 * @param t the type
	 * @param location the node in the DOM tree of this element
	 */
	PlatformSystemElement(String sn, String t, Node location) {
		systemName = sn;
		type = t;
		node = location;
	
	}

	public String getSystemName() {
		return systemName;
	}

	public String getType() {
		return type;
	}

	public Node getNode() {
		return node;
	}

	public void add(ModuleEntrySystemPart usedBy) {
		this.usedBy.add(usedBy);
	}

	public List<ModuleEntrySystemPart> getUsedBy() {
		return usedBy;
	}
}
