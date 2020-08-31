#ifndef FULLDUPLEXDATIONABORTNB_INCLUDED
#define FULLDUPLEXDATIONABORTNB_INCLUDED
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

#include "SystemDationNB.h"
#include "TaskCommon.h"

namespace pearlrt {
   /**
   \addtogroup io_FreeRTOS
   @{
   */
 
   /**
   \brief Abort for read/write opeation on a full duplex system dation

   In case of suspending or terminating an i/o operation on a full
   duplex operating device we must consider if we are in the read or in the
   write operation. In maximum one task may be active with the read and  
   another in write operation.
   Thus we must compare the task pointers weith the task pointer of the last 
   read and write operation and delegate teh abort operation to the device.

   This class overwrites the default mathodes terminate() and suspend()
   of the SystemDationNB class.

   */
   class FullDuplexDationAbortNB : public SystemDationNB {
   private:
      TaskCommon * readingTask;
      TaskCommon * writingTask;

   public:
      /** the ctor */
      FullDuplexDationAbortNB(void);
     
      /**
      set the task pointer to the reader task.
      To unset the reader task after competion of the dationRead method, call 
      this method with NULL

      \param rt pointer to the task which executes the datioRead
      */ 
      void setReaderTask(TaskCommon * rt); 

      /**
      set the task pointer to the writer task.
      To unset the writer task after competion of the dationWrite method, call 
      this method with NULL

      \param wt pointer to the task which executes the dationWrite
      */ 
      void setWriterTask(TaskCommon * wt); 

      /**
      delegate the terminate opration to ether terminateReader or
      terminateWriter, which must be provides by the dation class.

      \throws internalTaskSignal if nether reader or writer matches
      \param ioPerformingTask pointer to the task which should stop
		 the i/o operation
      */
      void terminate(TaskCommon* ioPerformingTask);

      /**
      terminate operation for read method
      \param ioPerformingTask pointer to the task which should stop
		 the i/o operation
      */
      virtual void terminateReader(TaskCommon* ioPerformingTask)=0;

      /**
      terminate operation for write method
      \param ioPerformingTask pointer to the task which should stop
		 the i/o operation
      */
      virtual void terminateWriter(TaskCommon* ioPerformingTask)=0;

      /**
      delegate the suspend opration to ether terminateReader or
      terminateWriter, which must be provides by the dation class.

      \throws internalTaskSignal if nether reader or writer matches
      \param ioPerformingTask pointer to the task which should suspend
	 the i/o operation
      */
      void suspend(TaskCommon* ioPerformingTask);

      /**
      suspend operation for read method
      \param ioPerformingTask pointer to the task which should suspend
	 the i/o operation
      */
      virtual void suspendReader(TaskCommon* ioPerformingTask)=0;

      /**
      suspend operation for write method
      \param ioPerformingTask pointer to the task which should suspend
	 the i/o operation
      */
      virtual void suspendWriter(TaskCommon* ioPerformingTask)=0;

   };
   /**
   @}
   */
}
#endif

