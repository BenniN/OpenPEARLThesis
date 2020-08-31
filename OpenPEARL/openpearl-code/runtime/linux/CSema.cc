/*
 [The "BSD license"]
 Copyright (c) 2012-2013 Rainer Mueller
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

\brief counting semaphore implementation

\author R. Mueller
*/
#include <pthread.h>
#include <errno.h>
#include <string.h>  // strerror
#include <semaphore.h>

#include "CSema.h"
#include "Log.h"

namespace pearlrt {

   CSema::CSema(int preset) {
      sem_init(&sem, 0, preset);
      id =  "?";
   }

   CSema::~CSema() {
      int ret;
      bool again = false;

      do {
         ret = sem_destroy(&sem);

         if (ret != 0) {
            if (errno == EINTR) {
                again = true;
            } else {
               Log::error("CSema destroy (%s): errno=%d %s",
                    id, ret, strerror(ret));
           }
         }
      } while (again == true);
   }

   void CSema::name(const char * s) {
      id = s;
   }

   void CSema::request() {
      int ret;
      ret = sem_wait(&sem);

      if (ret != 0) {
         Log::error("CSema request (%s): errno=%d %s",
                    id, errno, strerror(errno));
      }
   }

   void CSema::release() {
      int ret;
      ret = sem_post(&sem);

      if (ret != 0) {
         Log::error("CSema release (%s): errno=%d %s",
                    id, ret, strerror(ret));
      }
   }
}
