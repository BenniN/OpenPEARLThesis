package imc.checks;

import imc.types.Module;

import java.util.List;

/**
 * PEARL90 allows only 1 <b>SYSTEM</b> part in an PEARL application. OpenPEARL allows multiple <b>SYSTEM</b> parts.
 * 
 * We should emit a warning if more than one <b>SYSTEM</b> part is detected
 * 
 * @author mueller
 *
 */
public class CheckMultipleSystemParts {

	public CheckMultipleSystemParts(List<Module> modules) {
		int systemparts = 0;
		
		String eMsg = "multiple system parts detected in: ";
		for (Module m: modules) {
			if (!m.getSystemElements().isEmpty()) {
				eMsg += m.getSourceFileName()+" ";
		   	    systemparts++;
			}
		}
		
		if (systemparts>1) {
			imc.utilities.Log.warnWithoutLocation(eMsg);
		}
		
		
	}

}
