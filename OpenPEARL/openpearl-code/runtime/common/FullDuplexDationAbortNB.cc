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

#include "FullDuplexDationAbortNB.h"
#include "Log.h"
#include "Signals.h"

namespace pearlrt {


   FullDuplexDationAbortNB::FullDuplexDationAbortNB(void) {
      readingTask = NULL;
      writingTask = NULL;
   }
     
      void FullDuplexDationAbortNB::setReaderTask(TaskCommon * rt) {
          if (readingTask && rt) {
              Log::error("FullDuplexDationAbortNB:setReaderTask readingTask was not empty");
              throw theInternalTaskSignal;
          }
          readingTask = rt;
      }

      void FullDuplexDationAbortNB::setWriterTask(TaskCommon * wt) {
          if (writingTask && wt) {
              Log::error("FullDuplexDationAbortNB:setWriterTask writingTask was not empty");
              throw theInternalTaskSignal;
          }
          writingTask = wt;
      }

      /**
      delegate the terminate opration to ether terminateReader or terminateWriter
      which must be provides by the dation class.

      \throws internalTaskSignal if nether reader or writer matches
      \param ioPerformingTask pointer to the task which should stop the i/o operation
      */
      void FullDuplexDationAbortNB::terminate(TaskCommon* ioPerformingTask) {
printf("FullDuplexDationAbort: terminate\n");
          if (ioPerformingTask == readingTask) {
             terminateReader(ioPerformingTask);
          } else if (ioPerformingTask == writingTask) {
             terminateWriter(ioPerformingTask);
          } else {
              Log::error("FullDuplexDationAbortNB::terminate: nether reader or writer match");
              throw theInternalTaskSignal;
          } 
      }


      void FullDuplexDationAbortNB::suspend(TaskCommon* ioPerformingTask) {
Log::info("FullDuplexDationAbortNB::suspend called");
          if (ioPerformingTask == readingTask) {
             suspendReader(ioPerformingTask);
          } else if (ioPerformingTask == writingTask) {
             suspendWriter(ioPerformingTask);
          } else {
              Log::error("FullDuplexDationAbortNB::suspend: nether reader or witer match");
              throw theInternalTaskSignal;
          } 
      }

}

