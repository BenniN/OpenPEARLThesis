package imc.checks;


import imc.types.Module;
import imc.types.ModuleEntrySystemPart;
import imc.types.Platform;
import imc.types.PlatformSystemElement;
import imc.utilities.*;


import java.util.List;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * check the instances tags of all platform entries for all module system part elements
 *  instances="1"
 *  instances="oncePerSet"
 * 
 * @author mueller
 *
 */
public class CheckInstances {

	private List<Module> modules;
	
	public CheckInstances(List<Module> modules) {
		Platform pl = Platform.getInstance();
		this.modules = modules;

		for (Module m: modules) {

			for (ModuleEntrySystemPart se: m.getSystemElements()) {
				String systemName = se.getNameOfSystemelement();
				pl.getSystemElement(systemName).add(se);
			}
		}

		// let's check the <check instance=".." tags
		for (PlatformSystemElement pse : pl.getSystemNames()) {

			// search node <check and it's attributes
			Node n = pse.getNode();
			//System.out.println("System: "+pse.getSystemName());
			Node checkNode = NodeUtils.getChildByName(n, "checks");
			if (checkNode == null) continue;
			NodeList nl = checkNode.getChildNodes();
			for (int i=0; i < nl.getLength(); i++) {
				//System.out.println("child="+nl.item(i).getNodeName());
				if (nl.item(i).getNodeName().equals("check")) {
					Node nodeOfCheck=nl.item(i).getAttributes().getNamedItem("instances");
					if (nodeOfCheck != null) {
						if (nodeOfCheck.getTextContent().equals("1")) {
							// 
							if (pse.getUsedBy().size()> 1) {
								Log.setLocation(
										getModuleOf(pse.getUsedBy().get(0)).getSourceFileName(),
										pse.getUsedBy().get(0).getLine());
								Log.error("system element '"+ pse.getSystemName()+"' may only defined once");
								for (int j=1; j< pse.getUsedBy().size(); j++) {
								   Log.note(getModuleOf(pse.getUsedBy().get(j)).getSourceFileName(), pse.getUsedBy().get(j).getLine(),
										"already used by '"+pse.getUsedBy().get(j).getUserName());
								}
							}
						} else if (nodeOfCheck.getTextContent().equals("oncePerSet")) {
							Log.info("check oncePerSet "+pse.getSystemName());
							Node nodeOfSet = nl.item(i).getAttributes().getNamedItem("set");
							if (nodeOfSet == null) {
								Log.error("internal error: missing attribute set for oncePerSet in "+pse.getSystemName());
								return;
							}
							String set = nodeOfSet.getTextContent();
							if (pse.getUsedBy().size() > 1) {
								for (int j=0; j<pse.getUsedBy().size()-1; j++) {
									boolean errorPrinted = false;
									// evaluate the string for set expression
									EvaluateNamedExpression expr1 = new EvaluateNamedExpression(pse.getUsedBy().get(j).getParameters());
									String sj = expr1.evaluateExpression(set);
									for (int k=j+1; k<pse.getUsedBy().size(); k++) {
										EvaluateNamedExpression expr2 = new EvaluateNamedExpression(pse.getUsedBy().get(k).getParameters());
										String sk = expr2.evaluateExpression(set);
										if (sj.equals(sk)) {
											Log.setLocation(getModuleOf(pse.getUsedBy().get(k)).getSourceFileName(),
																		pse.getUsedBy().get(k).getLine());
											Log.error("multiple definition of: '"+sk+"'");
											errorPrinted=true;
										}
									}
									if (errorPrinted) {
										Log.note(getModuleOf(pse.getUsedBy().get(j)).getSourceFileName(),
												pse.getUsedBy().get(j).getLine(),
												"previous definition was here");
										
									}
								}
							}
						} else {
							Log.internalError("unknown kind of instances check in "+pse.getSystemName()+ "  check: "+ nodeOfCheck.getTextContent());
							return;
						}
					}
				}

			}
		}
	}

	private Module getModuleOf(ModuleEntrySystemPart se) {
		for (Module m : modules) {
			if (m.getSystemElements().contains(se)){
				return m;
			}
		}
		return null;
	}
}
