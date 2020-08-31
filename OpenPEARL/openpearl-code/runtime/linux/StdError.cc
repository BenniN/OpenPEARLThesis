/*
 [A "BSD license"]
 Copyright (c) 2013-2014 Holger Koelle
 Copyright (c) 2014-2016 Rainer Mueller
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

/**
\file
\brief Implementation of generic non-Basic Linux Systemdation

*/

#include "StdError.h"
#include "Character.h"
#include "RefChar.h"
#include "Dation.h"
#include "Log.h"
#include "Signals.h"
#include "Task.h"
#include <stdio.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/stat.h>

namespace pearlrt {

   StdError::StdError() :
      InterruptableSystemDationNB() {
      /* ctor is called before multitasking starts --> no mutex required */
      mutex.name("StdError");
      inUse = false;
      cap = FORWARD;
      cap |= PRM;
      cap |= ANY;

      cap |= OUT;
      fp = stderr;
   }

   int StdError::capabilities() {
      return cap;
   }

   StdError* StdError::dationOpen(const char * idf, int openParams) {
      if (openParams & (Dation::IDF | Dation::CAN)) {
         Log::error("StdError: does not support IDF and CAN");
         throw theDationParamSignal;
      }

      mutex.lock();
      inUse = true;
      mutex.unlock();
      return this;
   }

   void StdError::dationClose(int closeParams) {
      int ret;

      //
      mutex.lock();
      inUse = true;
      ret = fflush(fp);

      if (ret != 0) {
         Log::error("StdError: error at close (%s)", strerror(ferror(fp)));
         mutex.unlock();
         throw theCloseFailedSignal;
      }

      if (closeParams & Dation::CAN) {
         Log::error("StdError: CAN not supported");
         mutex.unlock();
         throw theDationParamSignal;
      }

      mutex.unlock();
   }

   void StdError::dationRead(void * destination, size_t size) {
      Log::error("StdError: does not support read");
      throw theDationNotSupportedSignal;
   }


   void StdError::dationWrite(void * source, size_t size) {

      int ret;
      int errnoCopy;

      mutex.lock();
      clearerr(fp);
      errno = 0;

      // perform the read() inside a try-catch block
      // treatCancelIO will throw an expection if the current
      // task should become terminated. In the catch block,
      // the mutex becomes released
      try {
         do {
            ret = fwrite(source, size, 1, fp);

            // safe the value of errno for further evaluation
            errnoCopy = errno;

            if (ret < 1) {
               if (errnoCopy == EINTR) {
                  Task::currentTask()->treatCancelIO();
                  Log::info("StdError: treatCancelIO finished");
               } else {
                  // other write errors
                  Log::error("StdError: error at write (%s)",
                       strerror(errnoCopy));
                  mutex.unlock();
                  throw theWritingFailedSignal;
               }
            }
         } while (ret <= 0);
      } catch (TerminateRequestSignal s) {
         mutex.unlock();
         throw;
      }

      mutex.unlock();
   }


   void StdError::dationUnGetChar(const char x) {
      Log::error("StdError: does not support unget");
      throw theDationNotSupportedSignal;
   }


   void StdError::translateNewLine(bool doNewLineTranslation) {
      // do nothing
   }

}
