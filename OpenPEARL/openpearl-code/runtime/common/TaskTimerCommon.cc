//#define _POSIX_TIMER
#include <time.h>
#include "TaskCommon.h"
#include "TaskTimerCommon.h"
#include <stdio.h>

extern "C" {
   extern int timer_gettime(timer_t timerid, struct itimerspec *value);
};


namespace pearlrt {
   void TaskTimerCommon::detailedStatus(char *id, char * line) {
      struct itimerspec its;
      float next, rept;

      timer_gettime(timer, &its);
      next = its.it_value.tv_sec + its.it_value.tv_nsec / 1.e9;
      rept = its.it_interval.tv_sec + its.it_interval.tv_nsec / 1.e9;

      if (counts > 0) {
         sprintf(line,
                 "%s next %.1f sec : all %.1f sec : %d times remaining",
                 id, next, rept, counts);
      } else {
         sprintf(line,
                 "%s next %.1f sec : all %.1f sec : eternally",
                 id, next, rept);
      }

   }
}
