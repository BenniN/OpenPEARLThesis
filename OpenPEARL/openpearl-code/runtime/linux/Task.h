/*
 [A "BSD license"]
 Copyright (c) 2012-2018 Rainer Mueller
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

#ifndef TASK_INCLUDED
#define TASK_INCLUDED

/**
\file

\brief tasking functionality with posix threads

*/


#include <stdio.h>
#include <pthread.h>

#include "BitString.h"
#include "PrioMapper.h"
#include "Prio.h"
#include "CSema.h"
#include "Clock.h"
#include "Duration.h"
#include "Interrupt.h"
#include "Log.h"
#include "Fixed.h"
#include "TaskCommon.h"
#include "TaskTimer.h"

namespace pearlrt {
   /**
   \addtogroup tasking_linux
   @{
   */

   /**
   \brief wrapper object to map PEARL tasks on posix threads

   This class provides all necessary operations for the compiler to realize the
   tasking functionality required by the PEARL language.

   All tasks use the same mutex from TaskCommon to enshure thread safety.

   The user code of each task is capsulated in a function [task name]_body.
   This body code is called from a wrapper function named [task name]_entry.
   This wrapper function, which is realized in task.h by a C-define,
   enshures the existence of the exeption handler and the propper
   initialization for thread cancellation.

   Task suspending is solved by reading from a pipe (pipeResume),
   continuation is solved by writing a 'c' to the pipe.

   Asynchronous SUSPEND and TERMINATE are realized by setting some flags
   inside a signal handler (without MUTEX-protection) and setting the
   priority of the target task to maximum. The operation ist done in
   the scheduleCallback-method, which is clled at each call of
   TaskCommon::setLocation(). The result is the following task state
   diagram. Note that the operations suspend and terminate differ
   whether they are called from the task itself, or by another task.
   The calling from another task leads to a differed state and
   completion of the action at a suitable point of execution from the
   task itself.

   \note The type REF TASK should be handled inside the compiler

   */
   class Task : public TaskCommon {

   private:
      static int useNormalSchedulerFlag;
      static int schedPrioMax;
      static int numberOfCores;
      Task();
      // cpuset is presetted with NULL; no special core
      cpu_set_t  * cpuset;

   public:
      /**
      pointer to a task function defined for easier coding
      */
      typedef void (*TaskEntry)(Task *);

      void scheduleCallback(void);
   private:

      void (*entryPoint)(Task * me); //< C function containing the code

#if 0
      /** Semaphor for completion of suspend call (inits by default to 0)   */
      CSema suspendDone;
      int suspendWaiters;

      /** Semaphor for completion of continue call (inits by default to 0)   */
      CSema continueDone;
      int continueWaiters;

      /** Semaphor for completion of terminate call
           (inits by default to 0)   */
      CSema terminateDone;
      int terminateWaiters;
#endif

      /** for Thread suspend resume
          the suspended thread waits for data on this pipe
          to continue a 'c' is written to the pipe
          to terminate a 't' is written to the pipe
      */
      int pipeResume[2];


      /* suspend and terminate from other tasks is is conflict
         with the pthread library.
         Thus these requests are set a flags to the thread.
         They are polled in setLocation() and treated from the
         target thread istself.

         The flag for task termination is located in TaskCommon, since
         this flag is also required for the termination of a task
         which resides in a wait queue
      */
//      volatile bool asyncSuspendRequested;


      pthread_t threadPid; //thread id
      pthread_attr_t attr;  // pthread scheduling parameters
      struct sched_param param; // -"-

      TaskTimer activateTaskTimer;  // the concrete timer
      TaskTimer continueTaskTimer;  // the concrete timer

      /**
      set the threads priority to normal operation (current prio).

      This method is needed to resume normal operation after priority
      ceiling to OS-level to run tasking call completly.
      */
      void setNormalPriority();

      /**
      set the threads priority to maximum.

      This method is needed to complete a tasking method call without
       disturbance of other threads.
      */
      void setOSPriority();

   public:


   private:
      /**
      suspend the task
      */
      void suspendMySelf();


      /**
      terminate the own thread
      */
      void terminateMySelf();

      /**
      terminate the thread of this object as an action from another task
      */
      void terminateFromOtherTask();

   public:
      void terminateIO();
      void terminateSuspended();
      void terminateSuspendedIO();
      void terminateRunning();

      /**
      setup a new task

      <ol>
      <li> Initialize all attributes required by the tasking system.
      <li> setup the posix timers
      <li> setup thread attributes
      <li> register the task in the TaskList object
      </ol>

      \param entry a pointer to the task body function
      \param n a pointer to the task name
      \param prio the priority (in PEARL meaning)
      \param isMain 1, if the task should be started automatically
              at system start <br>0 else

      \throws InternalTaskSignal if system ressources are not available
      */
      Task(void (*entry)(Task*), char * n, Prio prio,
           BitString<1> isMain);

      void suspendRunning();
      void suspendIO();

      /**
        fullfill the suspend request of the current task

        The task is suspended by waiting for a 'c' on a pipe.
        The continuation is done by sending a 'c' to the pipe.

        \note the mutexTask is expected to be locked
        \note the taskState must be checked by the calling method

      */
      void continueSuspended();

      /**
          Perform the dynamic part of task initialising

          This method is called automatically in the DCLTASK macro
      */
      void entry();

   private:
      /**
      immediate  activation of the task

      \param prio the priority of the task

      \todo{add configuration parameter for thread stacksize

      The stack size of a thread limits the number of possible threads.
      By default each thread gets 8MB of stack.
      This leads to a limit of approximatelly 300 threads at one time.
      If this value is set to a smaller value, the thread limit increases.

      As interim solution the stacksize ist set to PTHREAD\_STACK\_MIN.
      For the final implementation this parameter should be set via
      a configuration parameter.

      */
      void directActivate(const Fixed<15>& prio);

   public:

      /**
         retrieve the tasks thread id

         The thread id changed at each activation. Linux
         guarantees no monotonic behavior.

         \returns the posix thread id (pid), if activated<br>
                  or 0 if terminated
      */
      pthread_t getPid();

      /**
         retrieve the tasks entry

         required internally for run time task identification

         \return the function pointer of the posix thread function
      */
      TaskEntry getEntry();

      /**
         switch to normal scheduling

         In case of starting the system without root priviledges,
         there is currently no possibility
         to use the realtime scheduler of linux.
         For many applications normal scheduling policy
         is sufficient. This is set by this method.
      */
      static void useNormalScheduler();

      /**
        set the number of cores to use according the setting 
        in '.pearlrc'
      */
      static void setNumberOfCores(int nbrOfCores);

      /**
         store best priority in the system

         Several code segments need to run without dusturbance
         of other user threads. They are executed with "OS"-like
         priority, if SCHED_RR is avaliable. To reduce the number
         of system calls, the maximum priority is stored internally.

         \param p the best priority in the system - in system internal
                  representation
      */
      static void setThreadPrioMax(int p);

      /**
         set the threads priority to the best priority in the system

         Several code segments need to run without dusturbance
         of other user threads. They are executed with "OS"-like
         priority, if SCHED_RR is avaliable. To reduce the number
         of system calls, the maximum priority is stored internally.
      */
      void switchToThreadPrioMax();

      /**
         (re-)set the threads priority to the current priority

         Several code segments need to run without dusturbance
         of other user threads. They are executed with "OS"-like
         priority, if SCHED_RR is avaliable. At the end of these
         segments, the priority must be adjusted.
      */
      void switchToThreadPrioCurrent();

      /**
      set the threads priority

      \param prio new threads priority - in PEARL representation
      */
      void setPearlPrio(const Fixed<15>& prio);

      /**
      set the pthreads priority

      \param p the priority for the RR-scheduler
      */
      void setThreadPrio(int p);

      /**
      deliver pointer to current task object

      This feature is solved via a static  task local data element
      named 'mySelf' in Task.cc

      \returns the pointer of task object which calling any
           other method
      */
      static Task* currentTask(void);

      /**
      the suspend and terminate while doing an io statement is solved
      via a signal mechanism. This causes the linux kernel to abort
      the system call with EINTR.
      The device driver calls this method to do all necessary operations
      for suspend/terminate.
      Thbis methods return on continuation, dies with the thread
      on termination.

      The task mutex is locked whne the signal is emitted.
      The mutex becomes unlocked in case of trmination.
      */
      void treatCancelIO(void);

      /**
      delay the current task by the given amount of time.
      This method must be implemented by the platform specific code
      in Task.cc

      \param usec  number of micro seconds to delay
      \return true, if the delay was interrupted<br>
              false, if the delay passed without disturbion
      */
      static bool delayUs(uint64_t usecs);

      /**
      define the set of cpus for the execution of this thread

      \param set is a bitmap of useable cpus
      */
      void setCpuSet(cpu_set_t *set);

      /**
      read the set of cpus for the execution of this thread

      \return set is a bitmap of useable cpus
      */
      cpu_set_t * getCpuSet();

      /**
      convert cpu_set_t value into a comma separated list of integers
      
      If the list becomes longer than the given size, the list is aborted silently

      \param set the cpu_set_t to convert
      \param setAsText  the string to capture the list
      \param size the size of the list 
      */
      static void getCpuSetAsText(cpu_set_t * set, char* setAsText, size_t size);

   private:
      void enableCancelIOSignalHandler(void);
   };

}

/**
C++ code for the forward declaration of a task

\param t the name of the task without any additions
*/
#define   SPCTASK(t) \
extern pearlrt::Task t;

#if 0
#define   SPCTASKGLOBAL(t,ns) \
extern pearlrt::Task #ns::t;
#endif

/**
create to C++  code for the task definition

this macros also creates the task-body function with the
default exception handler

Target systems without standard output must redefine these macros
in a conventient way in there task-specification file

\param x the name of the task
\param prio the tasks priority (must be set to 255 if not given)
\param ismain 1, if the task is of type MAIN,<br> 0,else
*/
#define DCLTASK(x, prio, ismain) 			\
namespace pearlrt {					\
   static void x ## _entry (pearlrt::Task * me) ;	\
   static void x ## _body (pearlrt::Task * me) ;        \
}							\
pearlrt::Task x ( pearlrt::x ## _entry, ((char*)#x)+1,  \
                       prio, ismain);	                \
namespace pearlrt {					\
static void x ## _entry (pearlrt::Task * me) { 		\
      me->entry();  					\
      try {						\
         x ## _body (me);	 			\
      } catch (pearlrt::Signal & p) {			\
         char line[256];                                \
         printf("++++++++++++++++++++++++++++++\n");	\
         sprintf(line,"%s:%d Task: %s   terminated due to: %s",\
             me->getLocationFile(), me->getLocationLine(), \
             me->getName(), p.which());			\
         printf("%s\n",line);				\
         pearlrt::Log::error(line);			\
         printf("++++++++++++++++++++++++++++++\n");	\
      }							\
      me->terminate(me);  				\
   } 							\
}							\
							\
static void pearlrt::x ## _body (pearlrt::Task * me)

#if 0
#define DCLTASKGLOBAL(x, prio, ismain,ns) 		\
}							\
namespace pearlrt {					\
   static void x ## _entry (pearlrt::Task * me) ;	\
   static void x ## _body (pearlrt::Task * me) ;	\
}							\
namespace #x {						\
pearlrt::Task x ( pearlrt::x ## _entry, (char*)#x, 	\
                       prio, ismain);			\
}							\
namespace pearlrt {					\
static void x ## _entry (pearlrt::Task * me) { 		\
      me->entry();  					\
      try {						\
         x ## _body (me);	 			\
      } catch (pearlrt::Signal & p) {			\
         char line[256];                                \
         printf("++++++++++++++++++++++++++++++\n");	\
         sprintf(line,"%s:%d Task: %s   terminated due to: (%d) %s",\
             me->getLocationFile(), me->getLocationLine(), \
             me->getName(), p.whichRST(), p.which());			\
         printf("%s\n",line);				\
         pearlrt::Log::error(line);			\
         printf("++++++++++++++++++++++++++++++\n");	\
      }							\
      me->terminate(me);  				\
   } 							\
}							\
							\
namespace #x {						\
static void pearlrt::x ## _body (pearlrt::Task * me)
#endif

/**
@}
*/

#endif
