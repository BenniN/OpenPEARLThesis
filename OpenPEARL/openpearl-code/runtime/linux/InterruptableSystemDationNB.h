#ifndef INTERRUPTABLESYSTEMDATIONNB_INCLUDED
#define INTERRUPTABLESYSTEMDATIONNB_INCLUDED

#include "TaskCommon.h"
#include "SystemDationNB.h"

namespace pearlrt {

   /**
   Interrupt an i/o operation.

   This is required for SUSPEND or TERMINATE a task, which is currently
   doing a read or write.

   The read/write operation is interrupted by sending a unix signal to
   the thread. The read/write is only interrupted, if there is a signal hendler
   registered for the thread. The signal handler may by empty.
   The return code of the read/write statements is checked.
   In case of EINTR, the treatment is delegated be the concrete OpenPearl
   device driver to the common routine treatCancelIO(), which ether suspends 
   the thread or invokes a OpenPEARL signal TerminateRequestSignal, which
   is caught in the io-statement clause in order to release any blocking
   semaphores. 
   */
   class InterruptableSystemDationNB: public SystemDationNB {
      public:
         /**
         realize a suspend operation whil the task performs an i/o operation.

         \param ioPerformingTask  pointer to the task, which performs the
             io statement
 
         */
         void suspend(TaskCommon* ioPerformingTask);

         /**
         realize a terminate operation while the task performs an i/o operation.

         \param ioPerformingTask  pointer to the task, which performs the
             io statement
 
         */
         void terminate(TaskCommon* ioPerformingTask);
   };
}
#endif

