#ifndef GENERICUARTDATION_INCLUDED
#define GENERICUARTDATION_INCLUDED

/*
 [A "BSD license"]
 Copyright (c) 2019 Rainer Mueller
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

namespace pearlrt {

   /**
   This interface extends a system dation with features required by the GenericUart
   class.
   Any uart related driver which uses the GenericUart interface must implement 
   the virtual functions defined in this interface.
   These functions are usually called from the uart-interrupt service routine.

   We need access to the dationRead()-data storage and dationWrite() data.
   */
   class GenericUartDation {
      public:
         /**
           pass the received character to the active dation input buffer.
         
          \param ch the received character
          \returns true, if the character is stored in the input buffer
          \returns false, if there is no input request pending.
         */
         virtual bool addReceivedChar(char ch)=0;

         /**
         retrieve the next character from an output request 
         
         \param ch pointer to the storage for the next character to be
                   transmitted
         \returns true, if there was a character to transmit
         \returns false, if no output data is available
         */
         virtual bool getNextTransmitChar(char * ch)=0;
   };


}
#endif
