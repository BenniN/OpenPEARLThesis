/**
 * Bei der Traversierung werden systematisch alle Knoten des Baumes durchlaufen.
 * @param <K> K ist der Knoten
 */

public class BinarySearchTree<K extends Comparable<K>>
      implement Iterable<K> {

public static final int INORDER=1;
public static final int PREORDER=2;
public static final int POSTORDER=3;
public static final int LEVELORDER=4;

private int iteratorOrder;
//...

public void setIterationOrder(int io){
        if(io<i ||io>4)
        return;
        iteratorOrder=io;
        }
public Iterator<K> iterator(){
        switch(iterationOrder){
        case INORDER:
        return new InorderIterator<K>(this);
        case PRORDER:
        return new PreorderIterator<K>(this);
        case POSTORDER:
        return new PostorderIterator<K>(this);
        case LEVELORDER:
        return new LevelorderIterator<K>(this);
default:
        return new InorderIterator<K>(this);
        }
}