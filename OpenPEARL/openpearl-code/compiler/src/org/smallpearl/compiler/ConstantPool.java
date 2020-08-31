/*
 * [The "BSD license"]
 *  Copyright (c) 2012-2016 Marcel Schaible
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

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.stringtemplate.v4.ST;

import java.util.ArrayList;
import java.util.List;


public class ConstantPool {

    static List<ConstantValue> constantPool = new ArrayList<ConstantValue>();

    public ConstantPool() {
    }

    public ConstantValue add(ConstantValue value) {
        int i;
        int constantBitNo = 0;
        ConstantValue retValue = value;

        boolean found = false;

        for (i = 0; i < constantPool.size(); i++) {
            if ( value instanceof ConstantFixedValue) {
                if (constantPool.get(i) instanceof ConstantFixedValue) {
                    ConstantFixedValue a = (ConstantFixedValue)(value);
                    ConstantFixedValue b = (ConstantFixedValue)(constantPool.get(i));

                    if ( a.getValue() == b.getValue() && a.getPrecision() == b.getPrecision()) {
                        found = true;
                        retValue = constantPool.get(i);
                        break;
                    }
                }
            }
            else if ( value instanceof ConstantFloatValue) {
                if (constantPool.get(i) instanceof ConstantFloatValue) {
                    if ( Double.compare( ((ConstantFloatValue)(value)).getValue(), ((ConstantFloatValue)(constantPool.get(i))).getValue()) == 0) {
                        ConstantFloatValue a = (ConstantFloatValue)value;
                        ConstantFloatValue b = (ConstantFloatValue)(constantPool.get(i));

                        if ( Double.compare(a.getValue(),b.getValue()) == 0 && a.getPrecision() == b.getPrecision()) {
                            found = true;
                            retValue = constantPool.get(i);
                            break;
                        }
                    }
                }
            }
            else if ( value instanceof ConstantCharacterValue) {
                if (constantPool.get(i) instanceof ConstantCharacterValue) {
                    String s1 = ((ConstantCharacterValue)(value)).getValue();
                    String s2 = ((ConstantCharacterValue)(constantPool.get(i))).getValue();

                    if ( s1.equals(s2)) {
                        found = true;
                        retValue = constantPool.get(i);
                        break;
                    }
                }
            }
            else if ( value instanceof ConstantBitValue) {
                if (constantPool.get(i) instanceof ConstantBitValue) {
                    constantBitNo = constantBitNo + 1;
                    String s1 = ((ConstantBitValue)(value)).getValue();
                    String s2 = ((ConstantBitValue)(constantPool.get(i))).getValue();

                    int l1 = ((ConstantBitValue)(value)).getLength();
                    int l2 = ((ConstantBitValue)(constantPool.get(i))).getLength();

                    if ( s1.equals(s2) && l1 == l2) {
                        found = true;
                        retValue = constantPool.get(i);
                        break;
                    }
                }
            }
            else if ( value instanceof ConstantDurationValue) {
                if (constantPool.get(i) instanceof ConstantDurationValue) {
                    ConstantDurationValue other = (ConstantDurationValue)constantPool.get(i);

                    if ( value.equals(other) ) {
                        found = true;
                        retValue = constantPool.get(i);
                        break;
                    }
                }
            }
            else if ( value instanceof ConstantClockValue) {
                if (constantPool.get(i) instanceof ConstantClockValue) {
                    ConstantClockValue other = (ConstantClockValue)constantPool.get(i);

                    if ( value.equals(other) ) {
                        found = true;
                        retValue = constantPool.get(i);
                        break;
                    }
                }
            } else if (value instanceof ConstantNILReference) {
              if (constantPool.get(i) instanceof ConstantNILReference) {
                found = true;  // check for equality for NIL symbol not necessary
                break;
              }
            }

        }

        if (!found) {
            if ( value instanceof ConstantBitValue) {
                constantBitNo = constantBitNo + 1;
                ((ConstantBitValue) (value)).setNo(constantBitNo);
            }

            constantPool.add(value);
        } else {
            if ( value instanceof ConstantBitValue) {
                ((ConstantBitValue) (value)).setNo(constantBitNo);
            }
        }

        return retValue;
    }

    static
    public Void dump(){
        int i;
        System.out.println();
        System.out.println("ConstantPool: (Entries:" + constantPool.size() + ")");

        for ( i = 0; i < constantPool.size(); i++) {
            if ( constantPool.get(i) instanceof ConstantCharacterValue ) {
                ConstantCharacterValue str = (ConstantCharacterValue)constantPool.get(i);
                String output = String.format("  %-40s : %s",  constantPool.get(i).toString(), str.getValue());
                System.out.println(output);
            }
            else if ( constantPool.get(i) instanceof ConstantFixedValue ) {
                ConstantFixedValue v  = (ConstantFixedValue)constantPool.get(i);
                String output = String.format("  %-40s : %d(%d)",  constantPool.get(i).toString(), v.getValue(),v.getPrecision());
                System.out.println(output);
            }
            else if ( constantPool.get(i) instanceof ConstantBitValue) {
                ConstantBitValue v  = (ConstantBitValue)constantPool.get(i);
                String output = String.format("  %-40s : %s(%d)",  constantPool.get(i).toString(), v.getValue(), v.getLength());
                System.out.println(output);
            }
            else if ( constantPool.get(i) instanceof ConstantDurationValue) {
                ConstantDurationValue v  = (ConstantDurationValue)constantPool.get(i);
                String output = String.format("  %-40s : %s",  constantPool.get(i).toString(), v.getValue());
                System.out.println(output);
            }
            else if ( constantPool.get(i) instanceof ConstantClockValue) {
                ConstantClockValue v  = (ConstantClockValue)constantPool.get(i);
                String output = String.format("  %-40s : %s",  constantPool.get(i).toString(), v.getValue());
                System.out.println(output);
            }
            else if ( constantPool.get(i) instanceof ConstantFloatValue) {
                ConstantFloatValue v  = (ConstantFloatValue)constantPool.get(i);
                String output = String.format("  %-40s : %s(%d)",  constantPool.get(i).toString(), v.getValue(), v.getPrecision());
                System.out.println(output);
            }
        }

        return null;
    }

    static public ConstantCharacterValue lookupCharacterValue(String value) {
        int i;

        for (i = 0; i < constantPool.size(); i++) {
            if (constantPool.get(i) instanceof ConstantCharacterValue) {
                String s = ((ConstantCharacterValue)(constantPool.get(i))).getValue();

                if ( s.equals(value)) {
                    return (ConstantCharacterValue)(constantPool.get(i));
                }
            }
        }

        return null;
    }

    static public ConstantBitValue lookupBitValue(long value, int noOfBits) {
        int i;

        for (i = 0; i < constantPool.size(); i++) {
            if (constantPool.get(i) instanceof ConstantBitValue) {
                ConstantBitValue bitConst = ((ConstantBitValue) (constantPool.get(i)));
                long v1 = bitConst.getLongValue();

                if (v1 == value && bitConst.getLength() == noOfBits) {
                    return bitConst;
                }
            }
        }

        return null;
    }

    static public ConstantDurationValue lookupDurationValue(long hours, int minutes, double seconds, int sign) {
        int i;
        ConstantDurationValue other = new ConstantDurationValue(hours,minutes,seconds,sign);

        for (i = 0; i < constantPool.size(); i++) {
            if (constantPool.get(i) instanceof ConstantDurationValue) {
                ConstantDurationValue constant = ((ConstantDurationValue) (constantPool.get(i)));

                if (constant.equals(other)) {
                    return constant;
                }
            }
        }

        return null;
    }

    static public ConstantClockValue lookupClockValue(int hours, int minutes, double seconds) {
        int i;
        ConstantClockValue other = new ConstantClockValue(hours,minutes,seconds);

        for (i = 0; i < constantPool.size(); i++) {
            if (constantPool.get(i) instanceof ConstantClockValue) {
                ConstantClockValue constant = ((ConstantClockValue) (constantPool.get(i)));

                if (constant.equals(other)) {
                    return constant;
                }
            }
        }

        return null;
    }

    static public ConstantFloatValue lookupFloatValue(double value, int precision) {
        int i;
        ConstantFloatValue other = new ConstantFloatValue(value,precision);

        for (i = 0; i < constantPool.size(); i++) {
            if (constantPool.get(i) instanceof ConstantFloatValue) {
                ConstantFloatValue constant = ((ConstantFloatValue) (constantPool.get(i)));

                if ( constant.getValue() == other.getValue() &&
                     constant.getPrecision() == other.getPrecision()) {
                    return constant;
                }
            }
        }

        return null;
    }

    static public ConstantFixedValue lookupFixedValue(long value, int length) {
        int i;
        ConstantFixedValue other = new ConstantFixedValue(value,length);

        for (i = 0; i < constantPool.size(); i++) {
            if (constantPool.get(i) instanceof ConstantFixedValue) {
                ConstantFixedValue constant = ((ConstantFixedValue) (constantPool.get(i)));

                if ( constant.getValue() == other.getValue() &&
                        constant.getPrecision() == other.getPrecision()) {
                    return constant;
                }
            }
        }

        return null;
    }


    static public ConstantNILReference lookupConstantNILReference() {
        int i;

        for (i = 0; i < constantPool.size(); i++) {
            if (constantPool.get(i) instanceof ConstantNILReference) {
               return (ConstantNILReference) (constantPool.get(i));
           }
        }

        return null;
    }
    
}
