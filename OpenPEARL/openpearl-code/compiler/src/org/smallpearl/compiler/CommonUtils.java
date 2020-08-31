/*
 * [The "BSD license"]
 *  Copyright (c) 2016 Marcel Schaible
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

package org.smallpearl.compiler;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.smallpearl.compiler.Exception.IllegalCharacterException;
import org.smallpearl.compiler.Exception.InternalCompilerErrorException;
import org.smallpearl.compiler.Exception.ValueOutOfBoundsException;
import org.smallpearl.compiler.SymbolTable.*;
import org.smallpearl.compiler.*;
import org.smallpearl.compiler.SmallPearlParser.TypeReferenceContext;
import java.io.File;
import java.io.UnsupportedEncodingException;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.smallpearl.compiler.SymbolTable.VariableEntry;


import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommonUtils {

    public static Long convertBitStringToLong(String bitstring) {
        int base = 0;
        int bitsPerPosition = 0;
        int noOfBits = 0;
        String postfix = "";

        if (bitstring.startsWith("'")) {
            bitstring = bitstring.substring(1, bitstring.length());
        }

        if (bitstring.endsWith("'")) {
            bitstring = bitstring.substring(0, bitstring.length() - 1);
        }

        postfix = bitstring.substring(bitstring.indexOf("'") + 1, bitstring.length());
        bitstring = bitstring.substring(0, bitstring.indexOf("'"));

        if (postfix.equals("B") || postfix.equals("B1")) {
            base = 2;
            bitsPerPosition = 1;
        } else if (postfix.equals("B2")) {
            base = 4;
            bitsPerPosition = 2;
        } else if (postfix.equals("B3")) {
            base = 8;
            bitsPerPosition = 3;
        } else if (postfix.equals("B4")) {
            base = 16;
            bitsPerPosition = 4;
        }

        noOfBits = bitstring.length() * bitsPerPosition;

        Long i = 0L;
        for (int j = 0; j < bitstring.length(); j++) {
            int num = 0;
            switch (bitstring.charAt(j)) {
                case '0':
                    num = 0;
                    break;
                case '1':
                    num = 1;
                    break;
                case '2':
                    num = 2;
                    break;
                case '3':
                    num = 3;
                    break;
                case '4':
                    num = 4;
                    break;
                case '5':
                    num = 5;
                    break;
                case '6':
                    num = 6;
                    break;
                case '7':
                    num = 7;
                    break;
                case '8':
                    num = 8;
                    break;
                case '9':
                    num = 9;
                    break;
                case 'A':
                    num = 10;
                    break;
                case 'B':
                    num = 11;
                    break;
                case 'C':
                    num = 12;
                    break;
                case 'D':
                    num = 13;
                    break;
                case 'E':
                    num = 14;
                    break;
                case 'F':
                    num = 15;
                    break;
            }

            i *= base;
            i += num;
        }

        return i;
    }

    public static String formatBitStringConstant(Long l, int numberOfBits) {
        String bitStringConstant;

        String b = Long.toBinaryString(l);
        String bres = "";

        int l1 = b.length();

        if (numberOfBits < b.length()) {
            bres = "";
            for (int i = 0; i < numberOfBits; i++) {
                bres = bres + b.charAt(i);
            }
            Long r = Long.parseLong(bres, 2);
            if (Long.toBinaryString(Math.abs(r)).length() < 15) {
                bitStringConstant = "0x" + Long.toHexString(r);
            } else if (Long.toBinaryString(Math.abs(r)).length() < 31) {
                bitStringConstant = "0x" + Long.toHexString(r) + "UL";
            } else {
                bitStringConstant = "0x" + Long.toHexString(r) + "ULL";
            }
        } else if (numberOfBits > b.length()) {
            bres = b;
            Long r = Long.parseLong(bres, 2);
            if (Long.toBinaryString(Math.abs(r)).length() < 15) {
                bitStringConstant = "0x" + Long.toHexString(r);
            } else if (Long.toBinaryString(Math.abs(r)).length() < 31) {
                bitStringConstant = "0x" + Long.toHexString(r) + "UL";
            } else {
                bitStringConstant = "0x" + Long.toHexString(r) + "ULL";
            }
        } else {
            if (Long.toBinaryString(Math.abs(l)).length() < 15) {
                bitStringConstant = "0x" + Long.toHexString(l);
            } else if (Long.toBinaryString(Math.abs(l)).length() < 31) {
                bitStringConstant = "0x" + Long.toHexString(l) + "UL";
            } else {
                bitStringConstant = "0x" + Long.toHexString(l) + "ULL";
            }
        }

        return bitStringConstant;
    }

    public static int getBitStringLength1(String bitstring) {
        int base = 0;
        StringBuilder sb = new StringBuilder(bitstring.length());

        if (bitstring.startsWith("'")) {
            bitstring = bitstring.substring(1, bitstring.length());
        }

        if (bitstring.endsWith("'")) {
            bitstring = bitstring.substring(0, bitstring.length() - 1);
        }

        if (bitstring.charAt(bitstring.length() - 1) == '1') {
            base = 2;
        } else if (bitstring.charAt(bitstring.length() - 1) == '2') {
            base = 4;
        } else if (bitstring.charAt(bitstring.length() - 1) == '3') {
            base = 8;
        } else if (bitstring.charAt(bitstring.length() - 1) == '4') {
            base = 16;
        }

        Long i = 0L;
        for (int j = 0; j < bitstring.length() - 3; j++) {
            int num = 0;
            switch (bitstring.charAt(j)) {
                case '0':
                    num = 0;
                    break;
                case '1':
                    num = 1;
                    break;
                case '2':
                    num = 2;
                    break;
                case '3':
                    num = 3;
                    break;
                case '4':
                    num = 4;
                    break;
                case '5':
                    num = 5;
                    break;
                case '6':
                    num = 6;
                    break;
                case '7':
                    num = 7;
                    break;
                case '8':
                    num = 8;
                    break;
                case '9':
                    num = 9;
                    break;
                case 'A':
                    num = 10;
                    break;
                case 'B':
                    num = 11;
                    break;
                case 'C':
                    num = 12;
                    break;
                case 'D':
                    num = 13;
                    break;
                case 'E':
                    num = 14;
                    break;
                case 'F':
                    num = 15;
                    break;
            }

            i *= base;
            i += num;
        }

        return Long.toBinaryString(i).length();
    }

    public static int getBitStringLength(String bitstring) {
        int bitsPerPosition = 0;
        int noOfBits = 0;
        String postfix = "";

        if (bitstring.startsWith("'")) {
            bitstring = bitstring.substring(1, bitstring.length());
        }

        if (bitstring.endsWith("'")) {
            bitstring = bitstring.substring(0, bitstring.length() - 1);
        }

        postfix = bitstring.substring(bitstring.indexOf("'") + 1, bitstring.length());
        bitstring = bitstring.substring(0, bitstring.indexOf("'"));

        if (postfix.equals("B") || postfix.equals("B1")) {
            bitsPerPosition = 1;
        } else if (postfix.equals("B2")) {
            bitsPerPosition = 2;
        } else if (postfix.equals("B3")) {
            bitsPerPosition = 3;
        } else if (postfix.equals("B4")) {
            bitsPerPosition = 4;
        }

        noOfBits = bitstring.length() * bitsPerPosition;
        return noOfBits;
    }

    public static String convertBitStringToBitStringLong(String bitstring) {
        int base = 0;
        int bitsPerPosition = 0;
        int noOfBits = 0;
        String postfix = "";

        if (bitstring.startsWith("'")) {
            bitstring = bitstring.substring(1, bitstring.length());
        }

        if (bitstring.endsWith("'")) {
            bitstring = bitstring.substring(0, bitstring.length() - 1);
        }

        postfix = bitstring.substring(bitstring.indexOf("'") + 1, bitstring.length());
        bitstring = bitstring.substring(0, bitstring.indexOf("'"));

        if (postfix.equals("B") || postfix.equals("B1")) {
            base = 2;
            bitsPerPosition = 1;
        } else if (postfix.equals("B2")) {
            base = 4;
            bitsPerPosition = 2;
        } else if (postfix.equals("B3")) {
            base = 8;
            bitsPerPosition = 3;
        } else if (postfix.equals("B4")) {
            base = 16;
            bitsPerPosition = 4;
        }

        noOfBits = bitstring.length() * bitsPerPosition;

        Long i = 0L;
        for (int j = 0; j < bitstring.length(); j++) {
            int num = 0;
            switch (bitstring.charAt(j)) {
                case '0':
                    num = 0;
                    break;
                case '1':
                    num = 1;
                    break;
                case '2':
                    num = 2;
                    break;
                case '3':
                    num = 3;
                    break;
                case '4':
                    num = 4;
                    break;
                case '5':
                    num = 5;
                    break;
                case '6':
                    num = 6;
                    break;
                case '7':
                    num = 7;
                    break;
                case '8':
                    num = 8;
                    break;
                case '9':
                    num = 9;
                    break;
                case 'A':
                    num = 10;
                    break;
                case 'B':
                    num = 11;
                    break;
                case 'C':
                    num = 12;
                    break;
                case 'D':
                    num = 13;
                    break;
                case 'E':
                    num = 14;
                    break;
                case 'F':
                    num = 15;
                    break;
            }

            i *= base;
            i += num;
        }

        String result = Long.toBinaryString(Math.abs(i));
        while (result.length() < noOfBits) {
            result = "0" + result;
        }

        return result;
    }

    public static String getUUIDString() {
        UUID uuid = UUID.randomUUID();
        String s = uuid.toString();
        s = s.replaceAll("-", "_");
        return s;
    }

    public static String getArrayDescriptorName(VariableEntry entry) {
        String name = "ad_";
        TypeArray type = ((TypeArray) entry.getType());

        return name;
    }

    public static String unescapeCppString(String st) {
        StringBuilder sb = new StringBuilder(st.length());

        for (int i = 0; i < st.length(); i++) {
            char ch = st.charAt(i);
            if (ch == '\\') {
                char nextChar = (i == st.length() - 1) ? '\\' : st.charAt(i + 1);

                switch (nextChar) {
                    case '"':
                        ch = '"';
                        i++;
                        break;
                    case '\\':
                        ch = '\\';
                        i++;
                        break;

                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }

//    public static String unescapePearlString(String st) {
//        StringBuilder sb = new StringBuilder(st.length());
//        int state = 0;
//        String value = "";
//        String octalValue = "";
//        String val = "";
//
//        for (int i = 0; i < st.length(); i++) {
//            char ch = st.charAt(i);
//
//            switch (state) {
//                case 0:
//                    if (ch == '\'') {
//                        state = 1;
//                    } else {
//                        sb.append(ch);
//                    }
//                    break;
//
//                case 1:
//                    if (ch == '\\') {
//                        state = 2;
//                    } else {
//                        state = 0;
//                        sb.append(ch);
//                    }
//                    break;
//
//                case 2:
//                    if (ch == ' ') {
//                        state = 2;
//                    } else {
//                        value += ch;
//                        state = 3;
//                    }
//                    break;
//
//
//                case 3:
//                    value += ch;
//                    val = Integer.toString(Integer.parseInt(value, 16), 8);
//                    while (val.length() < 3) {
//                        val = "0" + val;
//                    }
//                    octalValue = "\\" + val;
//                    sb.append(octalValue);
//                    value = "";
//                    state = 4;
//                    break;
//
//                case 4:
//                    if (ch == '\\') {
//                        state = 5;
//                    } else if (ch == ' ') {
//                        state = 4;
//                    } else if (ch == '\n') {
//                        state = 4;
//                    } else {
//                        value += ch;
//                        state = 3;
//                    }
//                    break;
//
//                case 5:
//                    state = 0;
//                    if (ch == '\'') {
//                    } else {
//                        sb.append(ch);
//                    }
//                    break;
//            }
//        }
//
//        return sb.toString();
//    }

    // st is already without the limiting single quotes 
    // remove spaces and newlines between escaped control codes
    // and place exactly one space between control codes
    // replace double single quotes by one single quote
    public static String compressPearlString(String st) {
      StringBuilder sb = new StringBuilder(st.length());
      int state = 0;


      for (int i = 0; i < st.length(); i++) {
          char ch = st.charAt(i);

          switch (state) {
              case 0:  // normal string content 
                sb.append(ch);

                  if (ch == '\'') {
                      state = 1;
                  }
                  break;

              case 1:   // string single quote detected 
                  if (ch == '\\') {
                      state = 2;
                      sb.append(ch);
                  } else if (ch == '\'') {
                    // double single quotes detected --> replace by 1 single quote
                    // single quote is already in sb
                    state = 0;
                  } else {
                    // error: should never reach this point
                    System.err.println("compressPearlString: how did you reach this point?");
                      state = 0;
                  }
                  break;

              case 2: // control characters start detected
                  if (Character.isWhitespace(ch)) {
                      state = 2;
                  } else {
                      sb.append(ch);
                      state = 3;
                  }
                  break;


              case 3:  // second control nibble detected
                  sb.append(ch);
                  state = 4;
                  break;

              case 4:  // skip spaces between control characters
                  if (ch == '\\') {
                      state = 5;
                      sb.append(ch);
                  } else if (Character.isWhitespace(ch)) {
                      state = 4;
                  } else {
                      sb.append(' ');
                      sb.append(ch);
                      state = 3;
                  }
                  break;

              case 5:
                  if (ch == '\'') {
                     state = 0;
                     sb.append(ch);
                  }
                  break;
          }
      }

      return sb.toString();
  }
    

    // st has no limiting single quotes
    // is already compressed
    // now: replace '\23 34\' with the octal numbers
    //      and quote containing backslash and double quotes
    public static String convertPearl2CString(String st) {
      StringBuilder sb = new StringBuilder(st.length());
      int state = 0;
      String value="";

      
      for (int i = 0; i < st.length(); i++) {
          char ch = st.charAt(i);

          switch (state) {
              case 0:  // normal string content 
                  if (ch == '\'') {
                      state = 1;
                  } else if (ch == '"' || ch == '\\') {
                       sb.append('\\');
                       sb.append(ch);
                  } else {
                    sb.append(ch);
                  }
                  break;

              case 1:   // string single quote detected 
                  if (ch == '\\') {
                      state = 2;
                  } else {
                    sb.append('\'');
                    sb.append(ch);
                    state = 0;   
                  }
                  break;

              case 2: // control characters start detected
                  if (Character.isWhitespace(ch)) {
                      state = 2;
                  } else {
                      value +=ch;
                      state = 3;
                  }
                  break;


              case 3:  // second control nibble detected
                  value += ch;
                  int hexValue = Integer.valueOf(value, 16).intValue();
                  String octal = Integer.toOctalString(hexValue);
                  while (octal.length()<3) {
                    octal = "0"+octal;
                  }
                  octal = "\\"+octal;
                  sb.append(octal);
                  state=4;
                  value="";
                  break;

              case 4:  // skip spaces between control characters
                  if (ch == '\\') {
                      state = 5;
                  } else if (Character.isWhitespace(ch)) {
                      state = 4;
                  } else {
                      value+=ch;
                      state = 3;
                  }
                  break;

              case 5:
                  if (ch == '\'') {
                     state = 0;
                  }
                  break;
          }
      }
      if (state == 1) {
        sb.append('\'');
      }

      return sb.toString();
  }
    
    public static String removeQuotes(String st) {
      if( st.startsWith("'") && st.endsWith("'")) {
        st = st.substring(1, st.length()-1);
      }
        return st;
    }

    // st is already without limiting single quotes and compressed
    // double single quotes are already reduced to one single quote
    public static int getStringLength(String st) {
      StringBuilder sb = new StringBuilder(st.length());
      int state = 0;
      int length=0;

      for (int i = 0; i < st.length(); i++) {
        char ch = st.charAt(i);

        switch (state) {
          case 0:  // normal string content 
            
            if (ch == '\'') {
              state = 1;
            } else {
              sb.append(ch);
            }
            break;

          case 1:   // string single quote detected 
            //sb.append(ch);
            if (ch == '\\') {
              state = 2;
            } else {
              // two single quotes are ok --> deliver a single quote
              sb.append('\'');
              sb.append(ch);
              state = 0;
            } 
            break;

          case 2: // control characters start detected
            if (Character.isWhitespace(ch)) {
              // discard white spaces in control character context
              state = 2;
            } else {
              // first nibble detected
              state = 3;
            }
            break;


          case 3:  // second control nibble detected
            sb.append('?');  // substitude control code value with '?'
            state = 4;
            break;

          case 4:  // skip spaces between control characters
            if (ch == '\\') {
              state = 5;
              // terminating backslash detected
            } else if (Character.isWhitespace(ch)) {
              state = 4;
            } else {
              // first nibble of next code reached
              state = 3;
            }
            break;

          case 5:
            if (ch == '\'') {
              state = 0;
              //sb.append(ch);
            }
            break;
        }
      }
      if (state == 1) {
        sb.append('\'');
      }
      
      try {
        byte arr[] = sb.toString().getBytes("UTF8");
        length = arr.length;
      } catch (UnsupportedEncodingException ex) {
        //ErrorStack.add("illegal character in string constant");
      }

      return length;
    }
//    public static int getStringLength(String st) {
//        int state = 0;
//        int len = 0;
//        String s = "";
//
//        for (int i = 0; i < st.length(); i++) {
//            char ch = st.charAt(i);
//
//            switch (state) {
//                case 0:
//                    if (ch == '\\') {
//                        state = 1;
//                    } else {
//                        len++;
//                    }
//                    break;
//
//                case 1:
//                    // first digit of an octal value
//                    if (ch >= '0' && ch <= '7') {
//                        state = 2;
//                    } else {
//                        state = 0;
//                        len++;
//                    }
//                    break;
//
//                case 2:
//                    if (ch >= '0' && ch <= '7') {
//                        state = 3;
//                    } else {
//                        state = 0;
//                        len++;
//                    }
//                    break;
//
//                case 3:
//                    if (ch >= '0' && ch <= '7') {
//                        state = 0;
//                    } else if (ch == '\\') {
//                        state = 1;
//                    } else {
//                        state = 0;
//                        len++;
//                    }
//                    break;
//            }
//        }

// 20202-02-06; merge error
//        try {
//            byte arr[] = s.getBytes("UTF8");
//            len = arr.length;
//        } catch (UnsupportedEncodingException ex) {
//        }
//
//        if (len == 0) {
//            len = 1;
//        }
//
//        return len;
//    }

    public static String printContext(ParserRuleContext ctx) {
      if (ctx != null) {
        int a = ctx.start.getStartIndex();
        int b = ctx.stop.getStopIndex();

        Interval interval = new Interval(a, b);
        return ctx + ":" + ctx.start.getInputStream().getText(interval);
      } else {
	return "ctx is NULL";
      }
    }

    public static String getFileExtension(File file) {
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        return name.substring(lastIndexOf);
    }

    public static String getTypeOf(SymbolTableEntry se) {

        String type = null;

        if (se instanceof VariableEntry) {
            VariableEntry ve = (VariableEntry) se;
            type = ve.getType().toString();

        } else if (se instanceof SemaphoreEntry) {
            type = "SEMA";
        } else if (se instanceof BoltEntry) {
            type = "BOLT";
        } else if (se instanceof TaskEntry) {
            type = "TASK";
        } else if (se instanceof InterruptEntry) {
            type = "INTERRUPT";

        } else {
            System.err.println("CommonUtils.getTypeOf: untreated type " + se.getName());
        }

        return type;
    }


    // we must know that the expression is really of type ConstantFixedExpression!
    // this should be guaranteed by the grammar
    public static int getConstantFixedExpression(SmallPearlParser.ConstantFixedExpressionContext ctx, SymbolTable st) {
        int verbose = 0;
        boolean debug = false;
        ConstantFixedExpressionEvaluator evaluator = new ConstantFixedExpressionEvaluator(verbose, debug, st, null, null);
        ConstantFixedValue constant = (ConstantFixedValue) (evaluator.visit(ctx));
        return (int) (constant.getValue());
    }


    public static TypeDefinition getTypeDefinitionForSimpleType(SmallPearlParser.SimpleTypeContext simpleType) {
        TypeDefinition td = null;
        if (simpleType.typeInteger() != null) {

            if (simpleType.typeInteger().mprecision() != null) {
                String s = simpleType.typeInteger().mprecision().integerWithoutPrecision().getText();
                int precision = Integer.parseInt(s);
                td = new TypeFixed(precision);
            } else {
                td = new TypeFixed();
            }
        } else if (simpleType.typeFloatingPointNumber() != null) {
            if (simpleType.typeFloatingPointNumber().IntegerConstant() != null) {
                String s = simpleType.typeFloatingPointNumber().IntegerConstant().getText();
                int precision = Integer.parseInt(s);
                td = new TypeFloat(precision);
            } else {
                td = new TypeFloat();
            }

        } else if (simpleType.typeBitString() != null) {
            if (simpleType.typeBitString().IntegerConstant() != null) {
                String s = simpleType.typeBitString().IntegerConstant().getText();
                int precision = Integer.parseInt(s);
                td = new TypeBit(precision);
            } else {
                td = new TypeBit();
            }
        } else if (simpleType.typeCharacterString() != null) {
            if (simpleType.typeCharacterString().IntegerConstant() != null) {
                String s = simpleType.typeCharacterString().IntegerConstant().getText();
                int precision = Integer.parseInt(s);
                td = new TypeChar(precision);
            } else {
                td = new TypeChar();
            }
        } else if (simpleType.typeDuration() != null) {
            td = new TypeDuration();
        } else if (simpleType.typeTime() != null) {
            td = new TypeTime();
        }
        return td;
    }

    /**
     * Get the ConstantDurationValue from a given DurationConstantContext, taken into account a possible sign
     *
     * @param ctx  the DurationConstantContext
     * @param sign the sign +1 or -1
     * @return ConstantDurationValue
     */
    public static ConstantDurationValue getConstantDurationValue(SmallPearlParser.DurationConstantContext ctx, int sign) {
        long hours = 0;
        int minutes = 0;
        double seconds = 0.0;

        if (ctx != null) {
            if (ctx.hours() != null) {
                hours = getHours(ctx.hours());
            }

            if (ctx.minutes() != null) {
                minutes = getMinutes(ctx.minutes());
            }

            if (ctx.seconds() != null) {
                seconds = getSeconds(ctx.seconds());
            }
        }
        
        
        ConstantDurationValue c=null;
        try {
          c = new ConstantDurationValue(hours, minutes, seconds, sign);
        } catch (ArithmeticException e) {
          ErrorStack.enter(ctx);
          ErrorStack.add("duration constant exeeds limit");
          ErrorStack.leave();
        }
        return c; 
    }

    /**
     * Get the hours as integer from a given HoursContext
     *
     * @param ctx the HoursContext
     * @return number of hours
     */
    private static Long getHours(SmallPearlParser.HoursContext ctx) {
        Long hours = (long) 0;

        if (ctx.IntegerConstant() != null) {
          try {
            hours = Long.parseLong(ctx.IntegerConstant().getText());
            if (hours < 0) {
              ErrorStack.enter(ctx);
              ErrorStack.add("hours must be >= 0");
              ErrorStack.leave();
            }
          } catch (NumberFormatException e) {
            ErrorStack.enter(ctx);
            ErrorStack.add("value for hours too large");
            ErrorStack.leave();            
          }
        }

        return hours;
    }

    /**
     * Get the minutes as integer from a given MinutesContext
     *
     * @param ctx the MinutesContext
     * @return number of minutes
     */
    private static Integer getMinutes(SmallPearlParser.MinutesContext ctx) {
        Integer minutes = 0;

        if (ctx.IntegerConstant() != null) {
          try {
            minutes = Integer.parseInt(ctx.IntegerConstant().getText());
            if (minutes < 0 || minutes > 59) {
              ErrorStack.enter(ctx);
              ErrorStack.add("minutes must be in range [0..59]");
              ErrorStack.leave();
            }
          } catch (NumberFormatException e) {
            ErrorStack.enter(ctx);
            ErrorStack.add("value for minutes too large");
            ErrorStack.leave();
          }
        }

        return minutes;
    }

    /**
     * Get the seconds as double from a given SecondsContext
     * Note: The seconds can contain also fraction of a seconds
     * <
     *
     * @param ctx the SecondsContext
     * @return seconds
     */
    private static Double getSeconds(SmallPearlParser.SecondsContext ctx) {
        Double seconds = 0.0;

        if (ctx.IntegerConstant() != null) {
          try {
            seconds = (double) Integer.parseInt(ctx.IntegerConstant().getText());
            if (seconds < 0) {
              ErrorStack.enter(ctx);
              ErrorStack.add("seconds must be >= 0");
              ErrorStack.leave();
            }
          } catch (NumberFormatException e) {
            ErrorStack.enter(ctx);
            ErrorStack.add("value for seconds too large");
            ErrorStack.leave();
          }
        } else if (ctx.floatingPointConstant() != null) {
          String s = ctx.floatingPointConstant().getText();
            if (s.contains("(")   ) {
              ErrorStack.enter(ctx);
              ErrorStack.add("seconds must not contain a precision");
              ErrorStack.leave();
            }
            if (s.contains("E")   ) {
              ErrorStack.enter(ctx);
              ErrorStack.add("seconds must not contain an exponent");
              ErrorStack.leave();
            }
            seconds = CommonUtils.getFloatingPointConstantValue(ctx.floatingPointConstant());

            if (seconds < 0 || seconds >= 60) {
              ErrorStack.enter(ctx);
              ErrorStack.add("seconds must be in range [0..59]");
              ErrorStack.leave();
            }
        }

        return seconds;
    }
    
   /**
     * Retrieve the rightmost identifier of a name
     *
     * @param ctx NameContext
     * @return ParserRuleContext of rightmost identifier
     */
    public static ParserRuleContext getRightMostID(SmallPearlParser.NameContext ctx) {
        if ( ctx != null ) {
            String s = ctx.ID().getText();
            if (ctx.name() != null) {
                return getRightMostID(ctx.name());
            } else {
                return ctx;
            }
        }
        return null;
    }

    /**
     * Get the Value of a floating point constant from a given FloatingPointConstant context
     *
     * @param ctx  the FloatingPointConstantContext
     * @return double value
     */

    public static double getFloatingPointConstantValue(SmallPearlParser.FloatingPointConstantContext ctx ) {
        double value =0.0;
//        String regex = "([+-]?(\\d*[.])?\\d+)(\\(\\d+\\))?";
//        Pattern pattern = Pattern.compile(regex);
//        Matcher matcher = pattern.matcher(ctx.FloatingPointNumber().getText());
//
//        if ( matcher.find() ) {
//            value = Double.valueOf(matcher.group(1));
//        } else {
//            throw new InternalCompilerErrorException(ctx.getText(), ctx.start.getLine(), ctx.start.getCharPositionInLine());
//        }
        
        // we must remove the optional part 'precision '(ddd)' at the end
        String nbr = ctx.FloatingPointNumber().getText();
        int openBrace = nbr.indexOf('(');
        if (openBrace >= 0) {
          nbr = nbr.substring(0, openBrace);
        }
        try {
          value = Double.valueOf(nbr); 
        } catch (NumberFormatException e) {
          ErrorStack.enter(ctx);
          ErrorStack.add("illegal floating point constant");
          ErrorStack.leave();
        }
        if (value == Double.POSITIVE_INFINITY ||
            value == Double.NEGATIVE_INFINITY) {
          ErrorStack.enter(ctx);
          ErrorStack.add("illegal floating point constant to large");
          ErrorStack.leave();
          
        }
        return value;
    }

    public static int getFloatingPointConstantPrecision(SmallPearlParser.FloatingPointConstantContext ctx, int defaultPrecision) {
        int precision = defaultPrecision;

        String regex = "\\((\\d+)\\)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(ctx.FloatingPointNumber().getText());

        if ( matcher.find() ) {
            precision = Integer.valueOf(matcher.group(1));
        }

        return precision;
    }

    public static TypeDefinition getBaseTypeForReferenceType(
        TypeReferenceContext typeReference) {
      TypeDefinition type = null;
      
      if (typeReference.simpleType() != null) {
        type = CommonUtils.getTypeDefinitionForSimpleType(typeReference.simpleType());
      } else if (typeReference.typeStructure() != null) {
        System.err.println("CommunUtils.getTypeDefinitionForReferenceType: missing alternative STRUCT");
      } else if (typeReference.typeDation() != null) {
        type = new TypeDation();
      } else if (typeReference.typeReferenceSemaType() != null) {
        type = new TypeSemaphore();
      } else if (typeReference.typeReferenceBoltType() != null) {
        type = new TypeBolt();
//      } else if (typeReference.typeProcedure() != null) {
        // typeProcedure not defined yet
      } else if (typeReference.typeReferenceTaskType() != null) {
         type = new TypeTask();
      } else if (typeReference.typeReferenceInterruptType() != null) {
         type = new TypeInterrupt();
      } else if (typeReference.typeReferenceSignalType() != null) {
          type = new TypeSignal();
      } else if (typeReference.typeRefChar()!= null) {
//        type = new TypeRefChar();
        System.err.println("CommunUtils.getTypeDefinitionForReferenceType: missing alternative REF CHAR()");
      } else {
        System.err.println("CommunUtils.getTypeDefinitionForReferenceType: missing alternative ???");
      }

      return type;
    }
}
