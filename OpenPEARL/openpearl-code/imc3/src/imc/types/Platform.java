package imc.types;
/*
 [A "BSD license"]
 Copyright (c) 2016 Rainer Mueller
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import imc.main.ReadXml;
import imc.utilities.Log;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 * read xml definition for target system resources and provide specific
 * operations, like search for names as well as required and valid parameters.
 * 
 * In the Platform class accumulates information about the system elements.
 * The information about the system elements remain in the DOM-tree.
 * In other words, the Platform class provides a dictionary about the available system
 * elements.
 * 
 * 
 * @author mueller
 * 
 */

public class Platform {
	
	public static final String DATION = "DATION";
	public static final String CONNECTION = "CONNECTION";
	public static final String CONFIGURATION = "CONFIGURATION";
	public static final String INTERRUPT = "INTERRUPT";
	public static final String SIGNAL = "SIGNAL";
	
	private Document targetPlatform;
	private List<PlatformSystemElement> systemNames;
    private static Platform instance = null;
    
	public static Platform getInstance(String fileName, boolean verbose, String installationPath) {
		if (instance == null) {
			instance = new Platform( fileName,  verbose,  installationPath);
		}
		return instance;
	}

	public static Platform getInstance() {
		if (instance == null) {
			Log.error("no platform present");
		}
		return instance;
	}
	
	/**
	 * create an object for the query operations for the target platform
	 * elements, like SIGNAL, DATION and INTERRUPT
	 * 
	 * @param fileName
	 *            platform definition file name
	 * @param verbose
	 *            flag for verbose output; if true lot of messages are sent to
	 *            System.out
	 * @param installationPath path to the folder of the OpenPEARL system files (e.g /usr/local) 
	 */
	private Platform(String fileName, boolean verbose, String installationPath) {
		Log.info("Platform: parse "+fileName);
		
		ReadXml tgt = new ReadXml(fileName, verbose,  installationPath + "/lib/");

		targetPlatform = tgt.getDocument(); // readXMLDocumentFromFile(fileName);
		if (targetPlatform == null) {
			System.err.println("error reading target definition file ("
					+ fileName + ")");
			System.exit(1);
			return;
		}

		if (verbose) {
			tgt.dumpDomTree();
		}
		
		// create dictionary
		systemNames = new ArrayList<PlatformSystemElement>();
		
		// get entry point in tree for signals
		NodeList nl = targetPlatform.getElementsByTagName("platform");
		nl = nl.item(0).getChildNodes();
		if (nl.getLength() > 0) {
			for (int i = 0; i < nl.getLength(); i++) {
				Node n = nl.item(i);
				String sName;
				sName = null;
				if (n.getNodeType() == Node.ELEMENT_NODE
						&& n.getAttributes() != null) {
					if (n.getAttributes() != null) {
						if (n.getAttributes().getNamedItem("name") != null) {
							sName = n.getAttributes().getNamedItem("name")
									.getTextContent();
						}
					}

					if (sName != null) {
						PlatformSystemElement se = new PlatformSystemElement(sName, n.getNodeName().toUpperCase(), n);
						systemNames.add(se);
						Log.setLocation(fileName, -1);
						Log.debug(sName + " is available and has type "
								+ n.getNodeName().toUpperCase());
					}
				}
			}
		}

	}

	public Node getNodeOfSystemname(String systemName) {
		for (PlatformSystemElement pse : systemNames) {
			if (systemName.equals(pse.getSystemName())) {
				return pse.getNode();
			}
		}
		return null;
		
	}
	
	public PlatformSystemElement getSystemElement(String systemName) {
		for (PlatformSystemElement pse : systemNames) {
			if (systemName.equals(pse.getSystemName())) {
				return pse;
			}
		}
		return null;
		
	}

	public List<PlatformSystemElement> getSystemNames() {
		return systemNames;
	}





	
}
