/**
 * Der Wurzelknoten wird in der Warteschlange eingefügt. Dann wird zuerst der linke und dann der rechte Knoten in die Warteschlange eingefügt.
 * @param <K> K ist der Knoten des Baumes
 */

class LevelorderIterator<K extends Comparable <K>>
        implements Iterator<K> {

    java.util.Queue<TreeNode<K>> q =
            new java.util.LinkedList<TreeNode<K>>();

    public LevelorderIterator(BinarySearchTree<K> tree){
        TreeNode<K> node = tree.head.getRight();
        if (node != nullNode)
            q.addLast(node);}

    public K next(){
        TreeNode<K> node = q.getFirst();
        K obj = node.getKey();
        if (node.getLeft() != nullNode)
            q.addLast(node.getLeft());
        if (node.getRight() != nullNode)
            q.addLast(node.getRight());
        return obj;
    }
}