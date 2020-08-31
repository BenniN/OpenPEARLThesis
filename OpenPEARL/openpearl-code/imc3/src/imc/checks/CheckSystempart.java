package imc.checks;


import imc.types.Association;
import imc.types.Module;
import imc.types.ModuleEntrySystemPart;
import imc.types.Parameter;
import imc.types.Platform;
import imc.types.PlatformSystemElement;
import imc.utilities.EvaluateNamedExpression;
import imc.utilities.Log;
import imc.utilities.NodeUtils;

import java.util.ArrayList;
import java.util.List;


import org.w3c.dom.Node;
import org.w3c.dom.NodeList;



public class CheckSystempart {
	private static Module module;
	private static List<ModuleEntrySystemPart> newItems;
	private static int autoNumber = 0;
	
	/**
	 * check the system elements of a module
	 * 
	 * checkSystemelementsForSystemParts checks:
	 * <ul>
	 * <li> check if the system names exist
	 * <li> check if the parameters match with the system names definition
	 * <li> check if given and requested associations fit 
	 * </ul>
	 * 
	 * checkAssociationType checks:
	 * <ul>
	 * <li>type of the association
	 * </ul>
	 * 
	 * do not check
	 * <ul>
	 * <li>restrictions of the association
	 * </ul>
	 * 
	 * Detected associations enrich the ModuleEntrySystemPart elements for subsequent 
	 * jobs
	 * 
	 * Error messages are emitted if problems were detected. The checks are continued
	 * 
	 * @param modules the current module to be treated
	 */
	public static void checkSystemelementsForSystemParts(List<Module> modules) {
		Log.setLocation("-",-1);

		Log.info("start checking checkSystemelementsForSystemParts...");
		for ( Module m: modules) {
			newItems = new ArrayList<ModuleEntrySystemPart>();
			module = m;
			for (ModuleEntrySystemPart se: module.getSystemElements()) {
						
				Node systemNode = checkSystemNameExists(se, module.getSourceFileName());
				if (systemNode != null) {
					//Node inModule = se.getNode();
					// get node which contains the parameters
					Node seSystem = NodeUtils.getChildByName(se.getNode(), "sysname");

					Log.info("   check illegal autoInstanciate usage");
					checkAutoInstanciate(se,seSystem,systemNode);
					Log.info("   check ParameterTypes ..");
					compareParameterTypes(se, seSystem, systemNode);
					Log.info("   check Associations ..");
					checkAssociation(se, seSystem, systemNode);
				}
			}
			for (ModuleEntrySystemPart me : newItems) {
				m.getSystemElements().add(me);
			}
			
		}

	}


	private static void checkAutoInstanciate(ModuleEntrySystemPart se,
		Node seSystem, Node systemNode) {
		if (systemNode.getAttributes().getNamedItem("autoInstanciate") != null) {
			
		   Log.error("illegal instantiation of '"+seSystem.getAttributes().getNamedItem("name").getTextContent()+"'");
		}
	    return;
	}


	/**
	 * check if associations are valid
	 *   1) if se has no association, the systemNode must not require an association
	 *   2) if se has an association, the systemNode must require an association
	 *      and the type of requested associationProvider must be provided by the
	 *      systemNode
	 *   3) the check is continued recursively until the end of the association chain is reached
	 *       
	 * @param se the element in the system part of the module
	 * @param systemNodeInPlatform is the node in the platform definition
	 */
	private static void checkAssociation(ModuleEntrySystemPart se, Node systemNodeInModule,
			Node systemNodeInPlatform) {
		
		Node seAssoc = NodeUtils.getChildByName(systemNodeInModule, "association");

		Node needAssociation = NodeUtils.getChildByName(systemNodeInPlatform, "needAssociation");
		if (seAssoc == null && needAssociation != null) {
			Log.error("system element required an association - no association supplied");
			return;
		}
		if (seAssoc != null && needAssociation == null) {
			Log.error("no association expected");
			return;
		}
		if (seAssoc == null && needAssociation == null) {
			// we may finish now
			return;
		}

		// both elements have associations
		//  we must check whether the name of the association is a user-name
		//  or a system-name.
		// If it is a system name, we must check the parameters 
		//                            and repeat the association check

		// check if the provided element is ether a username or a system name
		boolean isUserName = false;
		boolean isSystemName = false;

		String providedAssociationName = seAssoc.getAttributes().getNamedItem("name").getTextContent();

		// check for user name in system part
		List<ModuleEntrySystemPart> systemElements = module.getSystemElements();
		for (ModuleEntrySystemPart m: systemElements) {
			if (m.getUserName() == null) continue;
	        if (!m.getUserName().equals(providedAssociationName)) continue;
	        
	        isUserName = true;
							
					se.setAssociation(new Association(m));
					
					// mark the association provider to be used
					// this make the check for unused elements easier 
					m.setIsUsed();  
					

					// is a user name --- MUST not have parameters!
					Node hasParameters = NodeUtils.getChildByName(needAssociation, "parameters");
					if (hasParameters != null) {
						Log.error("association as user name forbids parameters");
						return;  // abort further checks
					}

					break; // search finished
				}
			

		// check for system name
		PlatformSystemElement pse = Platform.getInstance().getSystemElement(providedAssociationName);

		if (pse != null) {
			isSystemName = true;
		}

		if (!isUserName && !isSystemName) {
			Log.error("'"+providedAssociationName+"' is nether user name nor system name");
			return; // abort further checks for this element
		}

		if (isUserName && isSystemName) {
			Log.warn("'"+providedAssociationName+"' is user name AND system name -- system name ignored");
			return;
		}
		if (isSystemName) {
			// we must check the parameters
			// we must create an anonymous ModuleEntrySystemPart
			// find the node of the association in the module definition file
			//  if is the first level, we must search in the sysname-branch
			//  if it is a deeper level we must search in the child branch
			Node nodeInModule = imc.utilities.NodeUtils.getChildByName(se.getNode(), "sysname");
			if (nodeInModule==null) {
				nodeInModule = imc.utilities.NodeUtils.getChildByName(se.getNode(), "association");
			} else {
				nodeInModule = imc.utilities.NodeUtils.getChildByName(nodeInModule, "association");
			}
			
			String autoName = "autoNameForAssociation_"+autoNumber++;
			
			ModuleEntrySystemPart anonymousModuleEntrySystemPart = new ModuleEntrySystemPart(autoName, se.getLine(), nodeInModule);
			anonymousModuleEntrySystemPart.setPrefix("");
			
			// mark the new element as used
			anonymousModuleEntrySystemPart.setIsUsed();
			
			se.setAssociation(new Association(anonymousModuleEntrySystemPart));
	
			Node nodeInPlatform = pse.getNode();
			compareParameterTypes(anonymousModuleEntrySystemPart, seAssoc, nodeInPlatform);
			checkAssociation(anonymousModuleEntrySystemPart, seAssoc, nodeInPlatform);
			// add anonymousModuleSystemElement to systemElements is not allowed 
			// thus add it to another list and integrate this new list at the end in systemElements
			newItems.add(anonymousModuleEntrySystemPart);
		}
	}


	private static Node checkSystemNameExists(ModuleEntrySystemPart se, String fn) {
		Log.setLocation(fn, se.getLine());

		// get system name of se
		String systemName = se.getNameOfSystemelement();
		Log.info("sysName "+systemName+" requested");


		Platform pl = Platform.getInstance();
		Node s = pl.getNodeOfSystemname(systemName);
		if (s == null) {
			Log.error("system name '" + systemName+ "' does not exist in this platform");
		}
		return s;
	}

	private static boolean compareParameterTypes(ModuleEntrySystemPart se, Node inModule, Node inTarget) {

		Node moduleParameters = NodeUtils.getChildByName(inModule, "parameters");
		Node targetParameters = NodeUtils.getChildByName(inTarget, "parameters");

		int nbrModuleParameters = NodeUtils.getNumberOfChilds(moduleParameters);
		int nbrTargetParameters = NodeUtils.getNumberOfChilds(targetParameters);



		// System.out.println("#P1= " + nbrModuleParameters + "  #P2="
		// + nbrTargetParameters);



		if (nbrModuleParameters != nbrTargetParameters) {
			Log.error("'"+se.getUserName()+se.getParametersAsString()+"' has wrong number of parameters "+
					"( required: "+nbrTargetParameters+")");
			return false;
		}
		if (nbrModuleParameters == 0) {
			return true; // both are zero
		}

		// check each parameter to have the same type
		NodeList moduleParameterNodes = moduleParameters.getChildNodes();
		NodeList targetParameterNodes = targetParameters.getChildNodes();
		int targetParameterIndex = 0;

		for (int i = 0; i < moduleParameterNodes.getLength(); i++) {
			if (moduleParameterNodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
				// System.out.println("ElementNode "
				// + moduleParameterNodes.item(i).getNodeName());

				Parameter p1 = new Parameter(moduleParameterNodes.item(i));
				// System.out.println("P1: " + p1.getValue() + " " +
				// p1.getType()
				// + " " + p1.length());

				// find next target parameter
				while (targetParameterIndex < targetParameterNodes.getLength()
						&& targetParameterNodes.item(targetParameterIndex)
						.getNodeType() != Node.ELEMENT_NODE) {
					targetParameterIndex++;
				}

				// das sollte nie passieren!
				if (targetParameterIndex >= targetParameterNodes.getLength()) {
					Log.internalError("CheckSystempart.compareParameterTypes: too many parameters given");
					return false;
				}

				checkParameterTypeAndValue(i,
						targetParameterNodes.item(targetParameterIndex),
						p1, se);
				targetParameterIndex++;
				se.addParameter(p1);
			}
		}

		return true;
	}

	private static boolean checkParameterTypeAndValue(int parameterNumber, Node n, Parameter p, ModuleEntrySystemPart se) {
		String type;
		int length;

		type = n.getNodeName();
		Node lengthNode = n.getAttributes().getNamedItem("length");
		if (lengthNode == null) {
			Log.internalError("no attribute length for parameter for '" + se.getNameOfSystemelement() +
					"' in system definition file");
			return false;
		}
		String lengthIsNumericalOrName = lengthNode.getTextContent();
		if (lengthIsNumericalOrName.contains("$")) {
			Log.internalError("length must not contain expression for '"+ se.getNameOfSystemelement() +
					"' in system definition file ("+lengthIsNumericalOrName+")");	
			return false; 
		} else {
			length = Integer.parseInt(lengthIsNumericalOrName);
		}

		if (type != p.getType()) {
			Log.error("parameter "+parameterNumber+1 + " has wrong type '" + type + "' expected: '"
					+ p.getType()+"'");
			return false;
		}
		if (length < p.length()) {
			Log.error("parameter "+parameterNumber+1 + " of type " + type + "(" + length
					+ ") does not fit into expected type" + p.getType() + "("
					+ p.length() + ")");
			return false;
		}

		// look for a name of this parameter
		Node name = n.getAttributes().getNamedItem("name");
		if (name!= null) {
			p.setName(name.getTextContent());
		} else {
			Log.internalError("parameter "+parameterNumber+1+" in '"+ se.getNameOfSystemelement()+"' has no name");
			return false;
		}

		// test value
		EvaluateNamedExpression ee = new EvaluateNamedExpression(se.getParameters());
		NodeList childs = n.getChildNodes();
		for (int i = 0; i < childs.getLength(); i++) {
			if (childs.item(i).getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			// rule found
			String rule = childs.item(i).getNodeName();
			String ruleContent = childs.item(i).getTextContent();
			Log.info("Rule " + rule + "  content " + ruleContent);
			if (rule.equals("VALUES")) {
				String[] items = ruleContent.split(",");
				for (int j = 0; j < items.length; j++) {
					if (items[j].trim().equals(p.getValue())) {
						return true;
					}
				}
				Log.error("parameter "+parameterNumber+1 +" has illegal value: " + p.getValue());
				Log.note("allowed values: "+ruleContent.trim());
				return false;
			} else if (rule.equals("ConsistsOf")) {
				// only the elements of the comma separated list are allowed in the parameter
				boolean found=false;
				boolean ok=true;
				String[] ruleItems = ruleContent.split(",");
				String[] paramItems = p.getValue().split(" ");
				for (int pi =0; pi < paramItems.length; pi++) {
					found = false;
					for (int ri=0; ri < ruleItems.length && found == false; ri++) {			
						if (paramItems[pi].trim().equals(ruleItems[ri].trim())) {
							found = true;
						}
					}
					if (!found) {
						Log.error("parameter "+parameterNumber+1 +" value "+paramItems[i]+" not supported");
						Log.note("allowed values: "+ruleContent.trim());
						ok = false;
					}
				}
				return ok;
			} else if (rule.equals("FIXEDRANGE")) {

				String[] items = ruleContent.split(",");

				String evaluated = ee.evaluateExpression(items[0]);
				int low = Integer.parseInt(evaluated);
				evaluated = ee.evaluateExpression(items[1]);
				//System.out.println("evaluated high as string: "+evaluated+"  item[1]="+items[1]);
				int high = Integer.parseInt(evaluated);
				int val = Integer.parseInt(p.getValue());
				if (val >= low && val <= high) {
					return true;
				} else {
					Log.error("value \"" + val + "\" out of range [" + low
							+ "," + high + "]");
					return false;
				}
			} else if (rule.equals("FIXEDGT")) {
				int low = Integer.parseInt(ee.evaluateExpression(ruleContent));

				int val = Integer.parseInt(p.getValue());
				if (val > low) {
					return true;
				} else {
					Log.error("value \"" + val + "\" out of range not > than " + low + ")");
					return false;
				}
			} else if (rule.equals("NotEmpty")) {
				if (p.length() > 0) {
					return true;
				} else {
					Log.error("value must not be empty");
					return false;
				}

			} else if (rule.equals("ALL")) {
				return true;
			} else {
				Log.info("no rule found -- accept everything");
				return true;
			}
		}

		return false;
	}

	/**
	 * check if specified associations agree with the list of provided associations
	 * of the corresponding system device of the platform
	 * 
	 * @param modules list of modules to be treated
	 */

	public static void checkAssociationType(List<Module> modules) {
		Log.setLocation("-",-1);

		Log.info("start checking checkAssociationType...");
		for ( Module m: modules) {
			module = m;
			for (ModuleEntrySystemPart se: module.getSystemElements()) {


				// get provided association types
				Association a = se.getAssociation();
				Node nodeInPlatform=null;

				if (a == null) continue;

				String systemName = se.getNameOfSystemelement();
				Node inP =  Platform.getInstance().getNodeOfSystemname(systemName);

				Node n1 = NodeUtils.getChildByName(inP, "needAssociation");
				String requiredAssociation = n1.getAttributes().getNamedItem("name").getTextContent();


				String systemDevice = ((Association)a).getUsername().getNameOfSystemelement();
				nodeInPlatform = Platform.getInstance().getNodeOfSystemname(systemDevice);

				Node providers = NodeUtils.getChildByName(nodeInPlatform, "associationProvider");
				String providedAssociations = "";
				NodeList nl = providers.getChildNodes();
				boolean found = false;
				for (int i=0; i< nl.getLength() && ! found; i++) {
					if (nl.item(i).getNodeName() == "associationType") {
						String p = nl.item(i).getAttributes().getNamedItem("name").getTextContent();
						providedAssociations += p + " ";
						if (p.equals(requiredAssociation)){
							found = true;
						}
					}
				}
				if (! found) {
					Log.setLocation(module.getSourceFileName(), se.getLine());
					Log.error("type of requested association not supported\n\trequested: "+requiredAssociation+
							"\n\tsupported: "+providedAssociations);
				}
			}
		}	
	}
}
