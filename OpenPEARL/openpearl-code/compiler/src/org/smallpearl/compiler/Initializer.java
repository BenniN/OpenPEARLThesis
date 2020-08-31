package org.smallpearl.compiler;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

import java.util.LinkedList;

public class Initializer  {
	public ParserRuleContext m_context;

    public Initializer(ParserRuleContext ctx) {
        m_context = ctx;
    }

	public ParserRuleContext getContext() { return m_context; }

	public String toString() {
		return "???";
	}
}
