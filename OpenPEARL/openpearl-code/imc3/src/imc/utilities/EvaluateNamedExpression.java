package imc.utilities;

import imc.types.Parameter;

import java.util.ArrayList;


/**
 * evaluate simple integer expression with named items
 * 
 * The names are marked with a '$' symbol. Each name is replaced by the value, which is 
 * taken from an actual parameter value
 * 
 * @author mueller
 *
 */
public class EvaluateNamedExpression {
	ArrayList<Parameter> parameters;

	public EvaluateNamedExpression(ArrayList<Parameter> parameters){
		this.parameters = parameters;
	}

	private String getNameValue(String name) {
		for (int i = 0; i < parameters.size(); i++) {
			Parameter p = parameters.get(i);
			String n = p.getName();
			if (n != null && n.equals(name)) {
				return parameters.get(i).getValue();
			}
		}
		return null;
	}

	/**
	 * substitute names by their current values and evaluate the resulting
	 * expression as integer. The names are prefixed with '$' and may
	 * consist only of upper and lower case letters. Expressions must be braced
	 * with square brackets. e.g. 'anyText[1+4*$name + $name]anyotherText'
	 * 
	 * multiple expressions are allowed
	 * 
	 * @param expr
	 *            the expression with names and operators
	 * @return the value of the expression 
	 */
	public String evaluateExpression(String expr) {

		int nameEnd;
		String expanded = expr;
		int nameStart = expanded.indexOf('$');
		while (nameStart >= 0) {
			nameEnd = nameStart;
			boolean isLetter;
			do {
				nameEnd++;
				char currentChar = expanded.charAt(nameEnd);
				isLetter = (currentChar >= 'A' && currentChar <= 'Z')
						| (currentChar >= 'a' && currentChar <= 'z');
			} while (isLetter && nameEnd < expanded.length()-1);
			if (isLetter) nameEnd ++;

			String name = expr.substring(nameStart + 1, nameEnd);
			String nameValue = getNameValue(name);
			expanded = expr.substring(0, nameStart) + nameValue
					+ expr.substring(nameEnd);
			//System.out.println("evaluateExpression: expanded="+expanded);
			nameStart = expanded.indexOf('$');
		}

		int exprStart = expanded.indexOf('[');
		while (exprStart >= 0) {
			int exprEnd = exprStart;
			while (exprEnd < expr.length() && expanded.charAt(exprEnd) != ']') {
				exprEnd++;
			}
			if (expanded.charAt(exprEnd) != ']') {
				Log.internalError("missing ']' in expression in target plattform definition file");
				return null;
			}

			// evaluate the expression
			String nakedExpression = expanded.substring(exprStart + 1, exprEnd);

			int result;

			try {
				result = SimpleIntegerExpressionEvaluator.eval(nakedExpression);
				//System.out.println("result="+result+"  result1="+result1);
			} catch (RuntimeException e) {
				Log.internalError("wrong expression in target plattform definition file ("
						+ nakedExpression + ")");
				return null;
			}

			// insert expression result in the expanded string
			expanded = expanded.substring(0, exprStart) + result
					+ expanded.substring(exprEnd + 1);
			//System.out.println("new expended="+expanded);

			// test for further expressions in the expanded string
			exprStart = expanded.indexOf('[');
		}
		return expanded;

	}

	// evaluator of very simple integer expressions
	//
	// Grammar:
	// expression = term 
	//              | expression `+` term 
	//              | expression `-` term
	// term = factor 
	//        | term `*` factor 
	//        | term `/` factor
	// factor = `+` factor i
	//          | `-` factor 
	//          | `(` expression `)`
	//          | number 
	//
	// evaluate the expression by recursive function calls
	// the evaluation is executed in a stateless way - thus no object
	// instantiation is required
	private static class SimpleIntegerExpressionEvaluator {
		static int pos;
		static char currentCharacter; 
		static String expr;

		static int eval(final String str) {
			expr = str;
			pos = -1;

			getNextCharacterInCh();  // get first character of the expression
			
			int x = expressionResult();
			if (pos < expr.length()) throw new RuntimeException("Unexpected: " + (char)currentCharacter);
			return x;

		}


		static void getNextCharacterInCh() {
			currentCharacter = '\255';  // this code is never expected  
			if (++pos < expr.length()) {
				currentCharacter = expr.charAt(pos);
			}
		}

		static boolean skipWhitespaceAndTakeIfPresent(int expectedChar) {
			while (currentCharacter == ' ') {
				getNextCharacterInCh();
			}
			if (currentCharacter == expectedChar) {
				getNextCharacterInCh();
				return true;
			}
			return false;
		}



		static int expressionResult() {
			int x = getTerm();
			while(true) {
				if (skipWhitespaceAndTakeIfPresent('+')) {
					x += getTerm(); 
				} else if (skipWhitespaceAndTakeIfPresent('-')) {
					x -= getTerm(); 
				} else {
					return x;
				}
			}
		}

		static int getTerm() {
			int x = getFactor();
			while(true) {
				if (skipWhitespaceAndTakeIfPresent('*')) {
					x *= getFactor(); 
				} else if (skipWhitespaceAndTakeIfPresent('/')) {
					x /= getFactor(); 
				} else {
					return x;
				}
			}
		}

		static int getFactor() {
			// unary plus
			if (skipWhitespaceAndTakeIfPresent('+')) return getFactor(); 

			// unary minus
			if (skipWhitespaceAndTakeIfPresent('-')) return -getFactor();

			int x;
			int startPos = pos;
			if (skipWhitespaceAndTakeIfPresent('(')) {
				// parentheses
				x = expressionResult();
				skipWhitespaceAndTakeIfPresent(')');
			} else if (currentCharacter >= '0' && currentCharacter <= '9') {
				// get complete number 
				while (currentCharacter >= '0' && currentCharacter <= '9') {
					getNextCharacterInCh();
				}
				x = Integer.parseInt(expr.substring(startPos, pos));
			} else {
				// unexpected character found
				throw new RuntimeException("unexpected character in expression: " + (char)currentCharacter);
			}

			return x;
		}
	}
}
