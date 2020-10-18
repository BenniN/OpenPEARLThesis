static class TreeNode<K extends Comparable<K>> {
 //Bei der Inorder Traversierung wird zuerst der linke Teilbaum behandelt, dann der aktuelle Knoten und dann der rechte Teilbaum. Als Ergebnis erhällt man den Baum in sortierter Reihenfolge.
    public void traverse() {
        if (key==null)
            return;
        left.traverse();
        System.out.print(" " + key);
        right.traverse();
    }