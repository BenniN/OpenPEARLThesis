/*
 [The "BSD license"]
 Copyright (c) 2015-2015 Rainer Mueller
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
#ifndef DATIONTS_INCLUDED
#define DATIONTS_INCLUDED
/**
\file

\brief userdation interface for userdations
       with the class attribute BASIC

*/
#include "UserDation.h"
#include "SystemDationB.h"
#include "DationDim.h"
#include "Fixed.h"
#include "TaskCommon.h"
#include "IOJob.h"


namespace pearlrt {
   /**
   \addtogroup io_common
   @{
   */

   /**
   \brief userdation interface for userdations
           with the class attribute BASIC.

    The complete operation is treated by a list of data elements and a list
     of format elements. See IODataList and IOFormatList about details.
     For expression results intermediate variable must be defined locally and
     used in the lists. The evaluation of the expressions must be done after
     creation of the lists and the invocation of the send- or take-method.

   Struct must not become rolled out.


   PEARL Example TS Dation

   \code
   SYSTEM;
      smotor: OctopusDigitalOut('A',3,4);

   PROBLEM;
      SPC smotor DATION OUT SYSTEM BASIC BIT(4) GLOBAL;
      DCL motor DATION OUT BASIC BIT(4) CREATED(smotor);

   Start: TASK MAIN
      DCL x BIT(4) INIT('0'B4);
      DCL error FIXED(31);

      OPEN motor ;
      SEND x TO MOTOR BY RST(error);
      CLOSE motor;

   END;
   \endcode

    C++
   \code
   // SYSTEM
   pearlrt::OctopusDigitalOut s_motor('A',3,4);
   pearlrt::Device* d_motor = & s_motor;

   // PROBLEM
   extern Device* d_motor;

   // on the basis of the keyword DATION and the keyword SYSTEM BASIC,
   //  we know it's a Basic Systemdation, so we cast it
   SystemDationB* _smotor = static_cast<SystemDationB*>(d_motor);

   // Userdation table is a DationTS (key datatype specified BASIC),
   //  parent system dation is _motor, direction is OUT (write only),
   DationTS _motor (_smotor,
                   Dation::OUT);

   Task(start,255,09 {
       _motor.dationOpen();

       {
          // setup io lists
          pearlrt::IODataList dataEntries[]= {
            {.dataType={pearlrt::IODataEntry::BIT,4}},
            {.dataPtr{.outPtr=&_x},
          };
          pearlrt::IOFormatList formatEntries[]= {
            {.dataType={pearlrt::IOFormatEntry::RST,4}},
            {.fp1{.inParam=&_error},
            {.fp2{.intValue=31},
          };
          pearlrt::IODataList dataList= {
               .nbrOfEntries=sizeof(dataEntries, dataEntries[0]),
               .entry=dataEntries,
          };
          pearlrt::IOFormatList formatList= {
               .nbrOfEntries=sizeof(formatEntries, formatEntries[0]),
               .entry=formatEntries,
          };
 
          _motor.send(me, &dataList, & formatList);
        }
       _motor.dationClose();
   }
   \endcode

      */

   class DationTS: public UserDation {

   private:
      /**
         Reference to the parent systemdation

         The two types of userdations (ALPHIC and TYPE) has
         to hold a reference of
         their parent systemdation.

       */
      SystemDationB* system;

      /**
      Current system dation object during operation - this may be different
      to system if the system dation supports multiple i/o-channels
      */
      SystemDationB* work;


      /**
        internal open function. build path to device, performs
        pre and post Open and creates the FILE descriptor (dev)

      */
      void internalOpen();

      /**
        internal close function. Close dev and delete it
        (if closeParam CAN is set)

      */
      void internalClose();

   private:
      /**
       Implementation of the internal Open-interface.

       \param p open parameters (with default setting
       \param rc RefChar with file name

       \note throws various exceptions if no RST-Variable is set
      */
      void internalDationOpen(int p,
                              RefCharacter* rc);

   public:


      /**
        create a new TAKE/SEND userdation (class attribute BASIC)

        \param parent pointer to the "parent" systemdation
        \param dationParams possible dation parameters like
               IN,INOUT, OUT,
      */
      DationTS(SystemDationB* parent,
               int dationParams);

      /**
      interface to close the BASIC or NON-Basic system dation
      \param dationParams contains tge dation parameters
      \note may throw exceptions
      */
      void closeSystemDation(int dationParams);

      /**
        Method for reading from a ts-userdation

        \param data pointer to the variable
        \param size number of bytes of the variable to read

        \note throws various exceptions
      */
      void dationRead(void* data, size_t size);

      /**
        Method for writing data to a ts-userdation

        \param data pointer to the variable
        \param size number of bytes of the variable to write

        \note throws various exceptions
      */
      void dationWrite(void* data, size_t size);

     /**
     provide an empty implementation of the hook function which 
     is required for non basic dations
     \param me pointer to the calling task
     */
     void beginSequenceHook(TaskCommon * me);
 
     /**
     provide an empty implementation of the hook function which 
     is required for non basic dations
     */
     void endSequenceHook(void);

    /**
      check, if the dation supports multiple io requests with it own
      scheduling.

      This is used in the consol device, which allowes the adressing of
      text input to waiting tasks. The tasks may becom blocked via their
      semaBlock semaphore until their data arrive or is processed.

      \return true if the dation supports multiple io-operations at thesame time
      \return false, if onky on operation is allowed at one time. In this
         case the tasks are added into a priority based wait queue
      */
      bool allowMultipleIORequests();

      /**
      send data via the IOJob interface

      \param me the pointer to the calling task
      \param dataList an array of data elements
      \param formatList an array of positioning formats (incl. RST)
      */
      void send(TaskCommon*me,
                 IODataList * dataList, IOFormatList * formatList);

      /**
      read data via the IOJob interface

      \param me the pointer to the calling task
      \param dataList an array of data elements
      \param formatList an array of positioning formats (incl. RST)
      */
      void take(TaskCommon*me,
                IODataList * dataList, IOFormatList * formatList);

   };
   /** @} */
}
#endif
