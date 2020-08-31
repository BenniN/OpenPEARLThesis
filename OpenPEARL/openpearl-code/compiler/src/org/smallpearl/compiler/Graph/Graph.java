/*
 * [The "BSD license"]
 *  Copyright (c) 2012-2020 Marcel Schaible
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 *  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 *  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.smallpearl.compiler.Graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

final public class Graph<T> {
    private HashMap<T, Node<T>> m_nodes = new HashMap<T, Node<T>>();
    private List<Node<T>> m_visitNodes = new ArrayList<Node<T>>();
    private List<Node<T>> m_dependencyList = new ArrayList<Node<T>>();

    public void addDependency(T from, T to) {
        Node<T> source = null;
        Node<T> dest = null;

        if (m_nodes.containsKey(from)) {
            source = m_nodes.get(from);
        } else {
            source = createNode(from);
            m_nodes.put(from, source);
        }

        if (m_nodes.containsKey(to)) {
            dest = m_nodes.get(to);
        } else {
            dest = createNode(to);
            m_nodes.put(to, dest);
        }

        source.addInputNode(dest);
        dest.addOutputNode(source);
    }

    private Node<T> createNode(T value) {
        Node<T> node = new Node<T>();
        node.m_value = value;
        return node;
    }

    public List<Node<T>> generateDependencies() {
        List<Node<T>> nextNodeList = new ArrayList<Node<T>>();
        List<Node<T>> orphanNodeList = getOrphanNodesList();

        if ( orphanNodeList != null ) {
            for (Node<T> node : orphanNodeList) {
                m_dependencyList.add(node);
                m_visitNodes.add(node);
                nextNodeList.addAll(node.getOutputNodes());
            }
        }

        generateDependencies(nextNodeList);

        return m_dependencyList;
    }

    private void generateDependencies(List<Node<T>> nodes) {
        List<Node<T>> nextNodesToVisitList = null;
        for (Node<T> node : nodes) {
            if (!isAlreadyVisited(node)) {
                List<Node<T>> comingInNodes = node.getInputNodes();

                if (areAlreadyVisited(comingInNodes)) {
                    m_dependencyList.add(node);
                    m_visitNodes.add(node);
                    List<Node<T>> outputNodes = node.getOutputNodes();

                    if (outputNodes != null) {
                        if (nextNodesToVisitList == null) {
                            nextNodesToVisitList = new ArrayList<Node<T>>();
                        }

                        nextNodesToVisitList.addAll(outputNodes);
                    }
                } else {
                    if (nextNodesToVisitList == null) {
                        nextNodesToVisitList = new ArrayList<Node<T>>();
                    }

                    nextNodesToVisitList.add(node);
                }
            }
        }
        if (nextNodesToVisitList != null) {
            generateDependencies(nextNodesToVisitList);
        }
    }

    private List<Node<T>> getOrphanNodesList() {
        List<Node<T>> orphanNodes = null;
        Set<T> keys = m_nodes.keySet();
        for (T key : keys) {
            Node<T> node = m_nodes.get(key);
            if (node.getInputNodes() == null) {
                if (orphanNodes == null) {
                    orphanNodes = new ArrayList<Node<T>>();
                }

                orphanNodes.add(node);
            }
        }
        return orphanNodes;
    }

    private boolean areAlreadyVisited(List<Node<T>> nodes) {
        return m_visitNodes.containsAll(nodes);
    }

    private boolean isAlreadyVisited(Node<T> node) {
        return m_visitNodes.contains(node);
    }
}
