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

public class ConstantDurationValue extends ConstantValue {
    private long m_hours ;
    private int m_minutes;
    private double m_seconds;
    private int m_sign = 1;

// never  used 2020-03-06 (rm) 
//    ConstantDurationValue(long hours, int minutes, int seconds) {
//        m_hours = hours;
//        m_minutes = minutes;
//        m_seconds = seconds;
//        checkValue();     // check range
//    }
//
//    ConstantDurationValue(long hours, int minutes, int seconds, int sign) {
//        m_hours = hours;
//        m_minutes = minutes;
//        m_seconds = seconds;
//        m_sign = sign;
//        checkValue();     // check range
//    }
//
    ConstantDurationValue(long hours, int minutes, double seconds) {
        m_hours = hours;
        m_minutes = minutes;
        m_seconds = seconds;
        checkValue();     // check range
    }

    ConstantDurationValue(long hours, int minutes, double seconds, int sign) {
        m_hours = hours;
        m_minutes = minutes;
        m_seconds = seconds;
        m_sign = sign;
        checkValue();     // check range
    }

    public int getSign() { return m_sign;}

    public void setSign(int sign) {
        m_sign = sign;
    }

    public long getHours() { return m_hours;}

    public int getMinutes() { return m_minutes;}

    public double getSeconds() { return m_seconds;}

    /**
     * return the duration in microseconds
     * 
     * @return
     */
    private void checkValue() {
      // calculate to avoid int overflows
      long result = m_hours;
      int isec = (int)m_seconds;
      int usec = (int)((m_seconds-isec)*1000000);
      
      // throws ArithemticException in case of overflow 
      result = Math.multiplyExact(result, 60);
      result = Math.addExact(result, m_minutes);
      result = Math.multiplyExact(result, 60);
      result = Math.addExact(result, isec);
      result = Math.multiplyExact(result, 1000000);
      result = Math.addExact(result, usec);
      result = Math.multiplyExact(result, m_sign);
      return;
//        return m_sign * ( m_hours * 3600 + m_minutes * 60 + m_seconds);
    }
    
    /* 
     * uses in Semantic checks and ConstantPoll dump
    * 
    * @return duration in seconds 
    */
   public double getValue() {
     // calculate to avoid int overflows
     double result = m_hours;
     int isec = (int)m_seconds;
     int usec = (int)((m_seconds-isec)*1000000);
     
     // throws ArithemticException in case of overflow 
     result = result*60+m_minutes;
     result = result*60+m_seconds;
     result *= m_sign;
     return result;
//       return m_sign * ( m_hours * 3600 + m_minutes * 60 + m_seconds);
   }
    /**
     * return the duration microseconds part
     * 
     * @return
     */
    public int getValueUsec() {
      // calculate to avoid int overflows
      int isec= (int)m_seconds;
      int usec = (int)((m_seconds-isec)*1000000);
      
      return usec;
//        return m_sign * ( m_hours * 3600 + m_minutes * 60 + m_seconds);
    }
    
    /**
     * deliver the seconds part of the duration
     * 
     * @return
     */
    public long getValueSec() {
      // calculate to avoid int overflows
      long result = m_hours;
      int isec = (int)m_seconds;
      
      // throws ArithemticException in case of overflow 
      result = Math.multiplyExact(result, 60);
      result = Math.addExact(result, m_minutes);
      result = Math.multiplyExact(result, 60);
      result = Math.addExact(result, isec);

      return result;
    }
    
    

    public String getBaseType() {
        return "Duration";
    }

    public String toString() {
        String name = "CONST_" + getBaseType().toUpperCase();
        double value = this.getValue();

        if ( m_sign == -1 ) {
            name += "_N";
        }
        else if (m_sign == 1 ) {
            name += "_P";
        }

        name += "_" + m_hours + "_" + m_minutes + "_" + m_seconds;
        return name.replaceAll("\\.", "_");
    }

    @Override
    public boolean equals(Object o) {
        // self check
        if (this == o)
            return true;

        // null check
        if (o == null)
            return false;

        // type check and cast
        if (getClass() != o.getClass())
            return false;

        ConstantDurationValue other = (ConstantDurationValue) o;

        // field comparison
        return this.m_sign == other.m_sign &&
               this.m_hours == other.m_hours &&
               this.m_minutes == other.m_minutes &&
               this.m_seconds == other.m_seconds;

    }
}


