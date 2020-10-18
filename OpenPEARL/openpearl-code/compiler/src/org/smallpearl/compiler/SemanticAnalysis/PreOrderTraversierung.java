static class TreeNode<K extends Comparable<K>> {
  //Bei der Preorder Traversierung wird der aktuelle Knoten zuerst behandelt und dann der linke oder rechte Teilbaum.
    public void traverse() {
        if (key==null)
            return;
        System.out.print("" + key);
        left.traverse();
        right.traverse();
    }