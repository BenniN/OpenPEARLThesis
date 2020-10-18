static class TreeNode<K extends Comparable<K>> {
  //Bei der Postorder Traversierung wird zuerst der linke Teilbaum behandelt und dann der aktuelle Knoten.
    public void traverse() {
        if (key==null)
            return;
        left.traverse();
        right.traverse();
        System.out.print("" + key);
    }