package imc.main;




import imc.checks.CheckI2CAdrDoesNotCollide;
import imc.checks.CheckInstances;
import imc.checks.CheckMultipleSystemParts;
import imc.checks.CheckPinsDoNotCollide;
import imc.checks.CheckSpcDcl;
import imc.checks.CheckSystempart;
import imc.types.Module;
import imc.types.Platform;
import imc.utilities.Log;

import java.util.ArrayList;

import java.util.List;





/**
 * utility to check the compatibility of system part and problem part resources.
 * 
 * The basic principle of operation is:
 * <ol>
 * <li>read platform definition and module interface definition xml-files and create individual DOM trees
 * <li>create dictionaries of the elements in these trees
 * <li>run checks form
 *   <ol>
 *   <li>correctness of the used parameters and applied associations {@link CheckSystempart}
 *   <li>the number of instances of each system element {@link CheckInstances}
 *   <li>if no errors occurred up to now:
 *   	<ul>
 *      <li> check the usage of i2c addresses {@link CheckI2CAdrDoesNotCollide}
 *      <li> check if no pins are used in multiple functions {@link CheckPinsDoNotCollide}
 *      <li> .. further checks may be added ... 
 *      <li> .. check if more than one <b>SYSTEM</b> part is defined (compliance check with PEARL90)
 *      <li> .. check if identifiers over different modules export/import fit 
 *      
 *      </ul>
 *   </ol>
 * <li> if no errors occurred: create code of the system part {@link CodeGenerator}
 * </ol>
 * 
 *
 * @author mueller
 *
 */
public class InterModuleChecker {
	static String target;
	static String outputFile;
	static String installationPath = "/usr/local";
	static Platform targetXml;

	static List<String> inputFiles = new ArrayList<String>();
	static List<Module> modules = new ArrayList<Module>();

	static boolean verbose = false;
	static boolean useNameSpace = true;

	/**
	 * the main entry
	 * 
	 * command line arguments are documented by the option --help
	 * 
	 * @param args command line arguments (details see the code of help)
	 * @throws Exception hopefully not
	 */
	public static void main(String[] args) throws Exception {
			
		Log.startLogging();
		
		if (args.length < 1) {
			printHelp();
			System.exit(1);
			return;
		}
 
		if (!checkAndProcessArguments(args)) {
			System.exit(1);
			return;
		}
		
		// set default output file name
		if (outputFile == null) {
			outputFile="system.cc";
		}
		
		Log.setShowInfo(verbose);
		if (target == null) {
			System.err.println("no target specified -- use -b option");
			System.exit(1);
		}
		
		if (inputFiles.isEmpty()) {
			System.err.println("no input file specified -- error exit");
			System.exit(1);
		}
			
		Log.info("Target=" + target);
		for (int i = 0; i < inputFiles.size(); i++) {
			Log.info("Input file: " + inputFiles.get(i));
		}

		// read target-xml
		targetXml = Platform.getInstance(target + ".xml", verbose, installationPath);

		// read all input files
		for (int i = 0; i < inputFiles.size(); i++) {
			Module mXml = new Module(inputFiles.get(i) + ".xml", verbose);
			modules.add(mXml);
		}

		Log.exitIfErrors();
		
		// start checks ...
		CheckSystempart.checkSystemelementsForSystemParts(modules);
		CheckSystempart.checkAssociationType(modules);
		Log.exitIfErrors();
		
		new CheckInstances(modules);
		Log.exitIfErrors();

		
		new CheckI2CAdrDoesNotCollide(modules);
		new CheckPinsDoNotCollide(modules);
		Log.exitIfErrors();
		
		new CheckMultipleSystemParts(modules);
		CheckSpcDcl cSpcDcl = new CheckSpcDcl(modules,useNameSpace);
		cSpcDcl.spcHasOneDcl();
		cSpcDcl.dclHasSpc();
		Log.exitIfErrors();
		
		cSpcDcl.warnUnusedDcl();
		
		// create system.cpp
		CodeGenerator.create(outputFile, modules, useNameSpace);
		
		//SystemEntries.searchUnUsed();
		Log.info("imc finished");

	}

	private static boolean checkAndProcessArguments(String[] args) {
		int i = 0;
		boolean returnValue = true;
		useNameSpace = true;
		while (i < args.length) {
			String arg = args[i++];
			if (arg.equals("--help")) {
				printHelp();
			} else if (arg.equals("--verbose")) {
				verbose = true;
			} else if (arg.equals("-b")) {
				target = args[i++];
			} else if (arg.equals("-o")) {
				outputFile = args[i++];
			} else if (arg.equals("-I")) {
				installationPath = args[i++];
			} else if (arg.equals("-noNameSpace")) {
				useNameSpace = false;
			} else if (arg.charAt(0) != '-') {
				// normal input file
				inputFiles.add(arg);
				continue;
			} else {
				System.err.println("unknown option (" + arg + ")");
				returnValue = false;
			}
		}

		return returnValue;
	}

	private static void printHelp() {

		System.err
				.println("java InterModuleChecker                           \n"
						+ " Options:                                                     \n"
						+ "  --help                      Print this help message         \n"
						+ "  --verbose                   Print lot of information        \n"
						+ "  -b <target>                 Disable semantic checker        \n"
						+ "  -o <output>                 output file (default: system.cc)\n"
						+ "  -noNameSpace                no special namespace code in output file \n"
						+ "  -I <installation path>      path to the installation folder \n"
						+ "                              (defaults to /usr/local)        \n"
						+ "  infile ...                  input files					 \n");
	}

}
