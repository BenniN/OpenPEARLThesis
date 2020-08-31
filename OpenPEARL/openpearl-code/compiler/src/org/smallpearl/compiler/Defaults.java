/*
 * [The "BSD license"]
 *  Copyright (c) 2012-2019 Marcel Schaible
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

public class Defaults  {
    public static final int FIXED_LENGTH              = 31;
    public static final int FIXED_MIN_LENGTH          = 0;
    public static final int FIXED_MAX_LENGTH          = 63;

    public static final int FLOAT_SHORT_PRECISION    = 23;
    public static final int FLOAT_LONG_PRECISION     = 52;
    public static final int FLOAT_PRECISION          = FLOAT_SHORT_PRECISION;

    public static final int BIT_LENGTH               =  1;

    public static final int CHARACTER_LENGTH         =  1;
    public static final int CHARACTER_MAX_LENGTH     =  32767;

    public static final int DEFAULT_TASK_PRIORITY    = 255;

    public static final int DEFAULT_ARRAY_LWB        = 1;

    public static final int LOWEST_PRIORITY			= 255;
    public static final int BEST_PRIORITY			= 1;
    
}
