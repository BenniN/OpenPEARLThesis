#include "InterruptableSystemDationNB.h"
#include "TaskCommon.h"

namespace pearlrt {

   void InterruptableSystemDationNB::suspend(TaskCommon * ioPerformingTask) {
      // just delegate this to Task.cc for all linux system dations
      // which react on the SIG_CANCEL_IO
      ioPerformingTask->suspendIO();
   }
   void InterruptableSystemDationNB::terminate(TaskCommon * ioPerformingTask) {
      // just delegate this to Task.cc for all linux system dations
      // which react on the SIG_CANCEL_IO
      ioPerformingTask->terminateIO();
   }
}

