package imc.checks;

import imc.types.Association;
import imc.types.Module;
import imc.types.ModuleEntrySystemPart;
import imc.types.Parameter;
import imc.types.Platform;
import imc.types.PlatformSystemElement;
import imc.utilities.EvaluateNamedExpression;
import imc.utilities.Log;

import java.util.List;
import java.util.ArrayList;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;



/**
 * check whether some pins are used multiple.
 * 
 * Run thru all ModuleSystemEntry and check if their PlatformEntry contains a check with name pinDoesNotCollide.
 * A pin is a representation of a physical i/o pin of a device. The number are choosen usually according the 
 * logic function. The name pin may represent a digital i/o bit as well as a multiplexer channel of a adc device.
 *   
 * The xml tag looks like 
 *    <check pinDoesNotCollide="PCF8574xx(I2cAdr=$iicAdr)" start="$start" width= "$width" />
 *  The attribute of the pinDoesNotCollide  defines a so called 'PinProvider'.
 *  If the platform device needs an associationProvider, the concrete username of the association provider
 *  is added to the given provider string.
 *    
 *  Each PinProvider maintains a list of all used bits and the using ModuleEntrySystemPart.
 *    
 *  After collecting all elements of a module, the list for each PinProvider is checked for duplicate pin numbers.
 *    
 *  Note that in the PinProvider definition the same parametric expressions are allowed with the named parameters of the 
 *  platform element. By this way PCF8574xx(I2cAdr=$iicAdr)" will be substituted into  "PCF8574xx(I2cAdr='40'B4)"
 *     
 * @author mueller
 *
 */

public class CheckPinsDoNotCollide {
	private ArrayList<PinProvider> pinProviders=new ArrayList<PinProvider>();
	private String errorMsg = "pin";
	/**
	 * Container for a used pin.
	 * 
	 * @author mueller
	 *
	 */
	private class UsedPin {
		/** the pin number */
		public int pin;
		
		/** the system part element which uses this pin */
		public ModuleEntrySystemPart usedBy;
		
		/**
		 * ctor for a given pin and the corresponding system part element
		 * 
		 * @param pin  number of the pin, which is used
		 * @param se   the system part element which uses the pin
		 */
		public UsedPin(int pin, ModuleEntrySystemPart se) {
			this.pin = pin;
			this.usedBy = se;
		}
	};
	

	private PinProvider getProvider(String provider) {
		for (int i = 0; i<pinProviders.size(); i++) {
			if (pinProviders.get(i).providerName.equals(provider)) {
				return pinProviders.get(i);
			}
		}
		
		PinProvider pp = new PinProvider(provider);
		pinProviders.add(pp);
		return pp;
	}

	
	/**
	 * the list of used pins of a system element
	 * 
	 * 
	 * @author mueller
	 *
	 */

	class PinProvider{
		public String providerName;
		private ArrayList<UsedPin> usedBits;


		/**
		 * create an empty list of used bits for the named  provider 
		 * @param name name of the provider
		 */
		public PinProvider(String name) {
			providerName = name;
			usedBits=new ArrayList<UsedPin>();
		}

		/**
		 * mark a range of pins as used; this is useful for digital i/o, when several bits
		 * are used as i/o device for bit strings
		 * 
		 * @param start starting number of the pin range; note numbering starts with the most significant pin/bit 
		 * @param width number of pins or bits
		 * @param se the {@link ModuleEntrySystemPart} which uses these pins or bits
		 */
		public void usePins(int start, int width, ModuleEntrySystemPart se) {
			while(width > 0) {
				usedBits.add(new UsedPin(start, se));
				start--;
				width--;
			}
		}		
	}
	

	public CheckPinsDoNotCollide(List<Module> modules) {
		// check, if we have a configuration elements with attribute "autoInstanciate" 
		// and check pinDoesNotCollide
		// if yes, we define the reserved gpio bits as used
		for (PlatformSystemElement pse: Platform.getInstance().getSystemNames()) {
		   if (pse.getNode().getAttributes().getNamedItem("autoInstanciate") != null) {
			   Node checks = imc.utilities.NodeUtils.getChildByName(pse.getNode(), "checks");
			   if (checks==null) continue;
			   NodeList check = checks.getChildNodes();
			   for (int ni=0; ni< check.getLength(); ni++) {
				   if (check.item(ni).getNodeType() != Node.ELEMENT_NODE) continue;
				   if (!check.item(ni).getNodeName().equals("check")) continue;
				   Node pinDoesNotCollide = check.item(ni).getAttributes().getNamedItem("pinDoesNotCollide");
				   if (pinDoesNotCollide==null) continue;
				   String provider = pinDoesNotCollide.getTextContent();
				   Node nbl = check.item(ni).getAttributes().getNamedItem("bitList");
				   if (nbl == null) {
					   Log.internalError("autoInstanciated elements with pinDoesNotcollide need 'bitList' attribute");
					   continue;
				   }
 				   String bitList = nbl.getTextContent();
 					Log.info("automatic system entry with pinDoesNotCollide: "+ provider+ ":" +bitList);
 					PinProvider pp = getProvider(provider);
 			        String[] pins;
 			 
 			        /* given string will be split by the argument delimiter provided. */
 			        pins = bitList.split(",");
 			 
 			         /* print substrings */
 			        for (int i = 0; i < pins.length; i++) {
 			            int start = Integer.parseInt(pins[i]);
 					    pp.usePins(start, 1, null);
 			        }
 				} 				   
			   }
		   }

		for (Module m: modules) {
			for (ModuleEntrySystemPart se: m.getSystemElements()) {
				// find all i2c bus providers and store them into a vector
				
				String sysname = se.getNameOfSystemelement();

				String associationProvider="";
				if (se.getAssociation() != null) {
					associationProvider = se.getAssociation().getUsername().getUserName();
				}
				
				Node inPlatform = Platform.getInstance().getNodeOfSystemname(sysname);
				Node checks = imc.utilities.NodeUtils.getChildByName(inPlatform, "checks");
				if (checks == null) continue;
				
				NodeList nl = checks.getChildNodes();
				for (int i=0; i< nl.getLength(); i++) {
				   if(nl.item(i).getNodeType() != Node.ELEMENT_NODE) continue;
				   
				   // must be associationType
				   if (!nl.item(i).getNodeName().equals("check")) continue;
				   Node n =  nl.item(i).getAttributes().getNamedItem("pinDoesNotCollide");
				   if (n==null) continue;
				   
				   String provider = nl.item(i).getAttributes().getNamedItem("pinDoesNotCollide").getTextContent();
				   n = nl.item(i).getAttributes().getNamedItem("start");
				   if (n == null) {
					   imc.utilities.Log.internalError("'"+sysname+"': missing attribute 'start'");
					   continue;
				   }
				   
				   int start=getIntFromNodeForAttribute(nl.item(i), se, "start");
				   
				   int width = 1;  // default
				   n = nl.item(i).getAttributes().getNamedItem("width");
				   if (n != null) {
					   width = getIntFromNodeForAttribute(nl.item(i), se, "width");
				   }
				   
				   n = nl.item(i).getAttributes().getNamedItem("errorText");
				   if (n == null) {
					   imc.utilities.Log.info("'"+sysname+"': use of default error message");
				   } else {
					   errorMsg = n.getTextContent();
				   }
				   EvaluateNamedExpression expr = new EvaluateNamedExpression(se.getParameters());
				   provider = expr.evaluateExpression(provider);
				   
				   if (!associationProvider.isEmpty()) {
					   provider += "---"+associationProvider;
				   }
				   
				   //System.out.println(sysname+" provider: " + provider + " pins needed "+start+" "+ width);
				   
				   PinProvider pp = getProvider(provider);
				   pp.usePins(start, width, se);
				   
				}
			}
			
			for (PinProvider pp: pinProviders) {
				int usedBits = pp.usedBits.size();
				for (int i=0; i<usedBits -1; i++) {
					for (int j=i+1; j<usedBits; j++) {
						if (pp.usedBits.get(i).pin == 
						    pp.usedBits.get(j).pin)  {
							ModuleEntrySystemPart mse1 = pp.usedBits.get(i).usedBy;
							ModuleEntrySystemPart mse2 = pp.usedBits.get(j).usedBy;
							
							imc.utilities.Log.setLocation(m.getSourceFileName(), mse2.getLine());
							Log.error("'"+ definitionFromUsername(mse2) + " "+ errorMsg+ " " + pp.usedBits.get(j).pin
									+ " already in use");
							if (mse1 != null) {
							   Log.note(m.getSourceFileName(), mse1.getLine(),
									"previous usage in '" + definitionFromUsername(mse1)+"'");
							} else {
							   Log.note("is reserved for system driver or other application");								
							}
						}
					}
				}
			}
			
		}
	}

	private String definitionFromUsername(ModuleEntrySystemPart se) {
		String result;
		result = se.getUserName()+": "+ se.getNameOfSystemelement();
		ArrayList<Parameter> p = se.getParameters();
		result += addParameters(p);


		Association a = se.getAssociation();
		while (a != null) {
			result += " --- ";
			ModuleEntrySystemPart aun = a.getUsername();
			result += aun.getNameOfSystemelement();
			p = aun.getParameters();
			result += addParameters(p);
			a = aun.getAssociation();
		}
		return result;
	}


	private String addParameters(ArrayList<Parameter> p) {
		String result = "";
		if (!p.isEmpty()) {
			result += "(";
			for (int i=0; i<p.size()-1; i++) {
				result += p.get(i).getValue() +", ";
			}
			result += p.get(p.size()-1).getValue() + ")";
		}
		return result;
	}

	private int getIntFromNodeForAttribute(Node node, ModuleEntrySystemPart se, String string) {
		int result=0;
		
			   	String s = node.getAttributes().getNamedItem(string).getTextContent();
			   	
			   	
				EvaluateNamedExpression expr = new EvaluateNamedExpression(se.getParameters());
				s = expr.evaluateExpression(s);
				
			   	try {
					   result = Integer.parseInt(s);
				} catch (NumberFormatException e) {
					imc.utilities.Log.internalError("bit number is non numerical ("+string+")");
				
		   }
		   
		return result;
	}			

}
