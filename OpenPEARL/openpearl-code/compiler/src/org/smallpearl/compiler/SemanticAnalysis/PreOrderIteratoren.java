/**
 *
 * @param <K>Der Wurzelknoten wird auf den Stack gelegt, anschließend der rechte Knoten und dann der linke Knoten.
 */

class PreorderIterator<K extends Comparable <K>>
        implements Iterator<K> {

    java.util.Stack<TreeNode<K>> st =


    public PreorderIterator(BinarySearchTree<K> tree){
        st.push(tree.head.getRight());

    }
    public boolean hasNext() {
        return !st.isEmpty();
    }

    public K next(){
        TreeNode<K> node = st.pop();
        K obj = node.getKey();
        node = node.getRight();
        if(node != nullNode) {
            st.push(node);
        }
        if(node != nullNode) {
            st.push(node);
        }
        return obj;
    }
}