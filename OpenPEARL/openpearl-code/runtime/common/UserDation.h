/*
 [A "BSD license"]
 Copyright (c) 2012-2017 Rainer Mueller
 Copyright (c) 2013-2014 Holger Koelle
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
   class UserDation;
}
#ifndef USERDATION_INCLUDED
#define USERDATION_INCLUDED

/**
\file

\brief class which provides mutual exclusion locking for dation operations

*/

#include "Mutex.h"
#include "Rst.h"
#include "SystemDation.h"
#include "Signals.h"
#include "TaskCommon.h"
#include "RefChar.h"
#include "Dation.h"
#include "Log.h"
#include "PriorityQueue.h"

namespace pearlrt {
   /**
     This class provides mutual exclusion locking for dation operations.

     Single PEARL I/O-statements are mapped to multiple C++ methods call.
     These C++ statements should not become interrupted by others tasks
     working on the same dation.
     Therefore a locking mechanism is needed.

     The dation operations may throw exceptions. The mutual excluded
     block must be wrapped in a try atch block.

     All exceptions in the try catch block will be handed towards
     the updateRst()-method. If the rst-formatting element was set,
     the exception is treated, else it is rethrown.
     The call of endSequence() in the catch handler enshures proper
     unlocking of the dation object.

     A simple mutex variable is defined and some methods to lock
     and unlock this variable according the dation operations.

     Furthermore, this class is responsible to cooperate with Task.h
     to realize suspend/continue/.. while dation operations are in progress.
     The current task working with this dation is specified by 'me' at
     the call of beginSequence().
   */
   class UserDation : public Dation, public Rst {

   protected:
     /**
      this mutex enhures that only one PEARL io statement is
      executed at the same time on the same user dation.

      There may be several user dations created upon the same system
      dation. This must be treated inside the system dation.
      */
      Mutex mutexUserDation;

      /** pointer to the task, which performs an i/o-operation on this
          dation
      */
//      TaskCommon* currentTask;

      /** current transfer direction.
          This is either Dation::IN or Dation::OUT
      */
      DationParams currentDirection;

      /** the system dation which performs the io processing
      */
      SystemDation * systemDation;
      /**
      queue of waiting tasks for operations on this user dation

      New jobs are added to the wait queue, if the user dation is busy.
      At the end of a job, the next (best priority) task will be taken
      from the waitQueue
      */
      PriorityQueue waitQueue;

      /**
      flag if this user dation has an operation in progress

      This value ist true, if an operation is in progress. The next
      jobs will be added in the waitQueue. At the end of a job, the next
      (best priority) task will be taken from the waitQueue
      */
      bool isBusy;

   public:
      /**
      ctor presets the attributes
      */
      UserDation();

   private:
      /**
       Implementation of the internal Open-interface.

       \param p specified open parameters
       \param rc RefChar with file name

       \note throws various exceptions
      */
      virtual void internalDationOpen(int p,
                                      RefCharacter* rc) = 0;

   public:

      /**
        Implementation of the Open-interface.

        If nether OLD,NEW,ANY or PRM,CAN is given the default is set,
        which is ANY + PRM


        \param p open parameters as given
        \param idf file name (used if IDF is set in p)
        \param rst pointer to rst-variable; required, if RST is set in p

        \note throws various exceptions if no RST-Variable is set
      */
      template <size_t S, int R>
      void dationOpen(int p,
                      Character<S> * idf,
                      Fixed<R> * rst) {
         try {
            if (p & RST) {
               if (! rst) {
                  Log::error("UserDation: RST is set but no"
                             " variable given");
                  throw theInternalDationSignal;
               }

               *rst = 0; // clear error variable
            }

            if ((!!(p & Dation::IDF)) != (idf != 0)) {
               Log::error("UserDation: ether both or non of IDF and filename");
               throw theInternalDationSignal;
            }

            if (S > 64) {
               Log::error("filename exceeds 64 characters");
               throw theDationParamSignal;
            }

            RefCharacter rc;
            Character<64>  fileName;
            rc.setWork(fileName);
            rc.clear();

            if (p & Dation::IDF) {
               rc.add(*idf);
            }

            internalDationOpen(p, &rc);
         } catch (Signal & s) {
            if (rst) {
               *rst = s.whichRST();
            } else {
               throw;
            }
         }
      }

   private:
      void internalDationClose(const int  p = 0);

   public:
      /**
        Implementation of the Close-interface, which is inherited
        from UserDation Basic-class

       \param p close parameters if given, else 0
       \param rst pointer to rst-variabla; required, if RST is set in p

        \note throws various exceptions if no RST-Variable is set

      */
      template<int S> void dationClose(const int  p, Fixed<S> * rst) {
         Fixed<S>* intRst = NULL;

         try {
            if (p & RST) {
               if (! rst) {
                  Log::error("UserDation: RST is set but no variable given");
                  throw theInternalDationSignal;
               }

               *rst = 0;  // clear RST value
               intRst  = rst;
            }

            internalDationClose(p);
         } catch (Signal &  s) {
            if (intRst != NULL) {
               *intRst = (Fixed<31>)s.whichRST();
            } else {
               throw;
            }
         }
      }

      /**
      interface to close the BASIC or NON-Basic system dation
      \param dationParams contains tge dation parameters
      \note may throw exceptions
      */
      virtual void closeSystemDation(int dationParams) = 0;

   protected:
      /** assert dation properties

       \throw DationNotOpenSignal if condition is not met
      */
      void assertOpen();

   public:
      /**
      restart the i/o operation after suspenion from wait queue

      \param me pointer to the task which performs the i/o.
                    May be NULL for testing purpose. Then no suspend and
                    terminate is done during the i/o-operation
      \param dir indicates the transer direction.
                 Allowed values are: Dation::IN and Dation::OUT
      */
      void restart(TaskCommon * me,
                         Dation::DationParams dir);

      /**
      Aquired the mutex to enshure atomic operation on the dation.

      This method may throw an exception in case of problems with the mutex
      operations.

      This method clears the rstValue attribute.

      \param me pointer to the task which performs the i/o.
                    May be NULL for testing purpose. Then no suspend and
                    terminate is done during the i/o-operation
      \param dir indicates the transer direction.
                 Allowed values are: Dation::IN and Dation::OUT
      */
      void beginSequence(TaskCommon * me,
                         Dation::DationParams dir);

      /**
      Free the mutex to mark the end of the atomic operation on the dation.

      This method may throw an exception in case of problems with the mutex
      operations.

      \param me pointer to the task which performs the i/o.
                    May be NULL for testing purpose. Then no suspend and
                    terminate is done during the i/o-operation
      */
      void endSequence(TaskCommon * me);

      /**
      get access to the wait queue

      \return pointer to the PriorityQueue of waiting tasks for this UserDation
      */
      PriorityQueue* getWaitQueue();


   protected:
      /**
      hook method to be called at each beginSequence call

      This is needed by DationRW and DationPG for their TFU support
      and not for DationTS.

      \param me pointer to th current executing task
      */
      virtual void beginSequenceHook(TaskCommon* me) = 0;

      /**
      hook method to be called at each endSequence call

      This is needed by DationRW and DationPG for their TFU support
      and not for DationTS.
      */
      virtual void endSequenceHook(void) = 0;

   public:
      /**
      suspend i/o operation

      the given task shall suspend all i/o operation until it 
      becomes continued or terminated

      The userdation must delegate this to the corresponding system dation

      \param ioPerformingTask pointer to the task which becomes suspended
      */
      void suspend(TaskCommon * ioPerformingTask);

      /**
      terminate task during an i/o operation

      the given task shall stop all i/o operation end terminate

      The userdation must delegate this to the corresponding system dation

      \param ioPerformingTask pointer to the task which becomes terminated
      */
      void terminate(TaskCommon * ioPerformingTask);

      /** obtain the current i/0 directiontransfer direction.

          \return the current transfer direction.
          This is either Dation::IN or Dation::OUT
      */
      DationParams getCurrentDirection();
   };
}
#endif
