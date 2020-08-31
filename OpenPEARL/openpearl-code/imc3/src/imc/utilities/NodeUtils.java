package imc.utilities;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * some helper function for treatment of a DOM tree
 * 
 * @author mueller
 */

public class NodeUtils {

	/**
	 * return the child node which has the given name
	 * 
	 * @param node the node, who's childrens are subject on interrest
	 * @param name the desired node name
	 * @return the requested node, or null
	 */
	public static Node getChildByName(Node node, String name) {
		//System.out.println("getChildByName of node "+ node.getNodeName()+" " +node.getAttributes().getNamedItem("name")+ "  looking for "+name);
		NodeList nList = node.getChildNodes();
		for (int i = 0; i < nList.getLength(); i++) {
			
//			 if (nList.item(i).getNodeType() == Node.ELEMENT_NODE) {
//			    System.out.println(nList.item(i).getNodeName());
//			 }
//			
			if (nList.item(i).getNodeType() == Node.ELEMENT_NODE
					&& nList.item(i).getNodeName().equals(name)) {
				return nList.item(i);
			}
		}
		return null;
	}

	/**
	 * get the number of ELEMENT_NODEs of the given node
	 * Text-nodes are skipped
	 * 
	 * @param node
	 * @return returns the number of child nodes which type ELEMENT_NODE
	 */
	public static int getNumberOfChilds(Node node) {
		int result = 0;
		if (node != null) {
			NodeList nl = node.getChildNodes();
//			System.out.println("childs: "+ nl.getLength());
			for (int i=0; i< nl.getLength(); i++) {
//				System.out.println("param#"+i+" "+nl.item(i).getNodeName());
				if (nl.item(i).getNodeType() == Node.ELEMENT_NODE) {
					result ++;
				}
			}
		}
		return result;
	}
}