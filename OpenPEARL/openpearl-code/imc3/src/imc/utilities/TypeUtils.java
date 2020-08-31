package imc.utilities;

public class TypeUtils {
	
	/**
	 * returns the base type of the given type
	 * 
	 * <ul>
	 * <li>simple types are returned without length or precission
	 * <li>in case of structs or dation the value is STRUCT or DATION
	 * </ul>
	 * 
	 * @param openPearlType the type as specified in the xml input file
	 * @return return only the short form of the type like FIXED instead of FIXED(15)
	 */
	public static String shortType(String openPearlType) {
		return openPearlType;
	}
	
	/**
	 * return the fill type info
	 * 
	 * <ul>
	 * <li>simple types are returned with length or precission
	 * <li>in case of structs the value is STRUCT with all components
	 * </ul>
	 * 
	 * @param openPearlType
	 * @return the complete type information with precision; STRUCTs are completely unrolled
	 */
	public static String longType(String openPearlType) {
		return openPearlType;
	}

}
