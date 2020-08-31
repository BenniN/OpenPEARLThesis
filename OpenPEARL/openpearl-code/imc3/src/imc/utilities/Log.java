package imc.utilities;
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

/**
 * provide static methods to print messages and count the created error
 * messages. The error message counter is used to stop compilation after each
 * step of check.
 * 
 * @author mueller
 * 
 */
public class Log {
	private static int numberOfErrors = 0;
	private static String file;
	private static int lineNbr;
	private static boolean showInfo = true;
	private static long start;
	
	public static void startLogging() {
		start = System.currentTimeMillis();;
	}

	/**
	 * set the location of the error (sourceFile:line)
	 * 
	 * @param fileName
	 *            the source file name
	 * @param line
	 *            the line number of the error producing element
	 */
	public static void setLocation(String fileName, int line) {
		file = fileName;
		lineNbr = line;
	}

	/**
	 * print an error message in the proper format with location and text
	 * 
	 * The error counter is automatically incremented.
	 * 
	 * @param message
	 *            the error message
	 */
	public static void error(String message) {
		numberOfErrors++;
		System.err.println(location()+ "error: " + message+"\n");
	}

	public static void internalError(String message) {
		numberOfErrors++;
		System.err.println(location()+ "internal error: " + message+"\n");
	}

	/**
	 * print an error descriptive message in the proper format with location and text
	 * 
	 * The error counter is NOT incremented.
	 * 
	 * @param message
	 *            the error message
	 */
	public static void note(String message) {
		System.err.println(location() + "note: " + message+"\n");
	}

	/**
	 * print an error descriptive message in the proper format with location and text
	 * 
	 * The error counter is NOT incremented.
	 * 
	 * @param message
	 *            the error message
	 */
	public static void note(String fileName, int lineNbr,String message) {
		System.err.println(location(fileName,lineNbr) + "note: " + message+"\n");
	}

	/**
	 * print info message, which is only printed in verbose mode. The verbose
	 * mode is set in the module by showInfo()
	 * 
	 * @param message
	 *            the info message
	 */
	public static void info(String message) {
		if (showInfo) {
			System.err.println(String.format("%.3f ms",(System.currentTimeMillis()-start)/1000.0) +": info: " + message);
		}
	}

	/**
	 * print debug message, which is only printed in verbose mode. The verbose
	 * mode is set in the module by showInfo()
	 * 
	 * @param message
	 *            the info message
	 */
	public static void debug(String message) {
		if (showInfo) {
			System.err.println( "debug: " + message);
		}
	}

	/**
	 * test if the error counter is non zero and exit in this case
	 */
	public static void exitIfErrors() {
		if (numberOfErrors > 0) {
			System.err.println("*** check aborted due to errors");
			System.exit(1);
		}
	}

	/**
	 * set verbose mode to enable the info- and debug-messages
	 * 
	 * @param v
	 *            must be true for verbose mode; if false: onlyerror and note messages are
	 *            printed
	 */
	public static void setShowInfo(boolean v) {
		showInfo = v;

	}

	/**
	 * print warning
	 * 
	 * The error counter is NOT incremented!
	 * 
	 * @param message
	 */
	public static void warn(String message) {
		System.err.println(location()+ "warning: " + message);

	}
	
	/**
	 * print warning without file location
	 * 
	 * The error counter is NOT incremented!
	 * 
	 * 
	 * @param message
	 */
	public static void warnWithoutLocation(String message) {
		System.err.println("warning: " + message);

	}


	private static String location() {
		return location(file, lineNbr);
	}
	
	private static String location(String file, int lineNbr) {
		return (file + ":" + lineNbr + ": ");
	}
}
