/**

 * @param <K> * Der Knoten head hat immer einen rechten Nachfolger. Es wird vom Wurzelknoten begonnen alle linken Knoten auf den Stack zu legen.
 */

class InorderIterator<K extends Comparable <K>>
        implements Iterator<K> {

    java.util.Stack<TreeNode<K>> st =
            new java.util.Stack<TreeNode<K>>();

    public InorderIterator(BinarySearchTree<K> tree) {
        TreeNode<K> node = tree.head.getRight();
        while (node != nullNode) {
            st.push(node);
            node = node.getLeft();
        }
    }
    public boolean hasNext() {
        return !st.isEmpty();
    }

    public K next(){
        TreeNode<K> node = st.pop();
        K obj = node.getKey();
        node = node.getRight();
        while (node != nullNode) {
            st.push(node);
            node = node.getLeft();
        }
        return obj;
    }

}