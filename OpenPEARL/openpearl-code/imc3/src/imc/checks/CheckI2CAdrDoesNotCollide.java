package imc.checks;


import imc.types.Module;
import imc.types.ModuleEntrySystemPart;
import imc.types.Platform;
import imc.utilities.EvaluateNamedExpression;
import imc.utilities.Log;

import java.util.List;
import java.util.ArrayList;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * check if an I2C address is not used conflicting with other devices on the same bus
 * 
 * Some devices allow multiple dations (like port expanders) -&gt;
 *	 their address is "shareable"
 * 
 * Other devices allow to be used by one dation -&gt;
 *       their address is "nonSharable"
 * 
 * @author mueller
 *
 */
public class CheckI2CAdrDoesNotCollide {
	private class I2CDevice {
		// let's keep this class simple - no setter/getter required for
		// the public attributes
		public String device;
		public String adr;
		public boolean isSharable;
		public ModuleEntrySystemPart se;
			
		
		public I2CDevice(String device, String adr, boolean isShareable, ModuleEntrySystemPart se) {
			this.device = device;
			this.adr = adr;
			this.isSharable = isShareable;
			this.se = se;
		}
	}

	public CheckI2CAdrDoesNotCollide(List<Module> modules) {
		for (Module m: modules) {
			List<ModuleEntrySystemPart> i2cProvider = new ArrayList<ModuleEntrySystemPart>();

			for (ModuleEntrySystemPart se: m.getSystemElements()) {
				// find all i2c bus providers and store them into a vector
				
				String sysname = se.getNameOfSystemelement();
				Node inPlatform = Platform.getInstance().getNodeOfSystemname(sysname);
				Node associationProvider = imc.utilities.NodeUtils.getChildByName(inPlatform, "associationProvider");
				if (associationProvider == null) continue;
				
				NodeList nl = associationProvider.getChildNodes();
				for (int i=0; i< nl.getLength(); i++) {
				   if(nl.item(i).getNodeType() != Node.ELEMENT_NODE) continue;
				   
				   // must be associationType
				   if (!nl.item(i).getNodeName().equals("associationType")) continue;
				   String typeOfAssociation = nl.item(i).getAttributes().getNamedItem("name").getTextContent();
				   if (typeOfAssociation.equals("I2CBusProvider")) {
					   i2cProvider.add(se);
				   }
				}
			}
			
			imc.utilities.Log.info("found i2c_providers: ");
			for (ModuleEntrySystemPart p : i2cProvider) {
				List<I2CDevice> connectedDevices = new ArrayList<I2CDevice>();
				
				imc.utilities.Log.info("treat provider: " + p.getUserName()+"  ");
				for (ModuleEntrySystemPart se : m.getSystemElements()) {
					if (se.getAssociation()!= null && se.getAssociation().getUsername().equals(p) ) {
						imc.utilities.Log.info("\t"+se.getUserName() + " uses "+ p.getUserName());
						
						// locate i2cAdrDoesNotCollide in platform definition of the platform element of se
						Node nPlatform = Platform.getInstance().getNodeOfSystemname(se.getNameOfSystemelement());
						Node checkNode = imc.utilities.NodeUtils.getChildByName(nPlatform, "checks");
						if (checkNode == null) continue;
						NodeList nl = checkNode.getChildNodes();
						for (int i=0; i < nl.getLength(); i++) {
							//System.out.println("child="+nl.item(i).getNodeName());
							if (!nl.item(i).getNodeName().equals("check")) continue;
							Node nodeOfCheck=nl.item(i).getAttributes().getNamedItem("i2cAdrDoesNotCollide");
							if (nodeOfCheck == null) continue;
							
							String device = nodeOfCheck.getTextContent();
							
							Node nShare= nl.item(i).getAttributes().getNamedItem("shareable");
							Node nonShare= nl.item(i).getAttributes().getNamedItem("nonShareable");
							if ( (nShare == null) == (nonShare==null)) {
								imc.utilities.Log.internalError(se.getNameOfSystemelement()+" check i2cAdrDoesNotCollide needs ether attribute 'shareable' or 'nonShareable'");
								return;
							}
							String adr;
							if (nShare!= null) {
							   adr = nShare.getTextContent();
							} else {
							   adr = nonShare.getTextContent();
							}
							
							EvaluateNamedExpression expr = new EvaluateNamedExpression(se.getParameters());
							adr = expr.evaluateExpression(adr);
							connectedDevices.add(new I2CDevice(device, adr, nShare!= null,se));
							
						}
						
					}
				}
				imc.utilities.Log.info("connected devices on "+p.getUserName() );
				for (I2CDevice i : connectedDevices) {
					imc.utilities.Log.info("\t"+i.device+" "+i.adr + " sharable: "+i.isSharable);
				}
				// test each i2c device against all others
				for (int i=0; i<connectedDevices.size()-1; i++) {
					for (int j=i+1; j<connectedDevices.size(); j++) {
						// search for same address
						if (!connectedDevices.get(i).adr.equals(connectedDevices.get(j).adr)) continue;
						//check sharable
						if(connectedDevices.get(i).isSharable &&
						   connectedDevices.get(j).isSharable) {
							// both devices must have the same name
							if (connectedDevices.get(i).device.equals(connectedDevices.get(i).device)) continue;

							// same address different device name
							imc.utilities.Log.setLocation(m.getSourceFileName(), connectedDevices.get(j).se.getLine());
							imc.utilities.Log.error("I2C address of "+ connectedDevices.get(j).device + "already used.");
							Log.note(m.getSourceFileName(), connectedDevices.get(i).se.getLine(),
									"previous usage by "+ connectedDevices.get(i).device);
						} else {		
							// at least one non sharable address
							imc.utilities.Log.setLocation(m.getSourceFileName(), connectedDevices.get(j).se.getLine());
							imc.utilities.Log.error("I2C address "+ connectedDevices.get(j).adr + 
									" of '"+ connectedDevices.get(j).se.getUserName() + "' is already used");
							Log.note(m.getSourceFileName(), connectedDevices.get(i).se.getLine(),
									"previous usage from '" + connectedDevices.get(i).se.getUserName() + "'");
						}
					}
				}
			}
		}
	}

}
