/*
 [A "BSD license"]
 Copyright (c) 2012-2013 Holger Koelle
 Copyright (c) 2014-2017 Rainer Mueller
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

#ifndef USERDATIONNB_INCLUDED
#define USERDATIONNB_INCLUDED

#include "Dation.h"
#include "UserDation.h"
#include "SystemDationNB.h"
#include "Character.h"
#include "Fixed.h"
#include "RefChar.h"
#include "DationDim.h"
#include "Log.h"
#include "TFUBuffer.h"
#include "IOJob.h"

/**
\file

\brief userdation interface

*/

namespace pearlrt {

   /**
   \brief basic class for Userdations (DationRW and DationPG).
          general interface for the variatons of userdations.

          This class defines the common methods and attributes for the
          two types of userdations (ALPHIC and TYPE).
          These common methods are the absolut
          positioning formats (POS,SOP) and ADV.
   */

   class UserDationNB: public UserDation {

   protected:
      /**
      The type of the dation is ether ALPHIC or TYPE;
      */
      enum DationType {ALPHIC, TYPE};

      /** the TFU buffer management

      the buffer storage is allocated by the compiler
      */
      TFUBuffer  tfuBuffer;


   private:
      static const Fixed<31> one;
      static const Fixed<31> zero;
      const DationType dationType;
      char paddingElement; // ether ' ' for ALPHIC or 0 for TYPE DATIONs

      void fill(const Fixed<31> n, char fillChar);
      void skipX(const Fixed<31> n);
      void skipUntil(const Fixed<31> n, char fillChar);
      void skipAny(const Fixed<31> n);
      void internalSop(Fixed<31>* page, Fixed<31>* row, Fixed<31>* element);
      Fixed<31> internalAdv(Fixed<31> p, Fixed<31> r, Fixed<31> c);
      void doTfuAndSeekStuff();

   public:
      /**
      mark the  TFU buffer to be empty

      This method is called at the beginning of a new GET/PUT/READ/WRITE
      statement and at SKIP or PAGE
      */
      void markTFUBufferUsed();

      /**
      prepare TFU buffer for new input record

      Internally checks, if <ul>
         <li>a TFU buffer is set
         <li>the buffer is marked to be empty
      </ul>
      A complete record is fetched from the system dation
      */
      void getTFUBuffer();

      /**
      hook method to be called at each beginSequence call

      This is needed by DationRW and DationPG for their TFU support
      and not for DationTS.

      Internally checks, if <ul>
         <li>a TFU buffer is set
         <li>the buffer is marked to be empty
         <li>the type of the dation to select the padding character
      </ul>
      \param me the pointre to the calling task
      */
      void beginSequenceHook(TaskCommon * me);

      /**
      hook method to be called at each endSequence call

      This is needed by DationRW and DationPG for their TFU support
      and not for DationTS.

      Here it marks the TFU buffer to be empty
      */
      void endSequenceHook(void);


      /**
      return a character back to the input (ether source or device)
      \param c the character to be returned
      */
      virtual void dationUnGetChar(const char c) = 0;

   public:
      /** advance routine 1 dimensional

        just update to logical file position
        has no effect on the real file/stream
        \note public only for test reasons
        \param c number of elements to skip
        \returns the number of elements to skip
      */
      Fixed<31> adv(Fixed<31> c);

      /** advance routine 2 dimensional

        just update to logical file position
        has no effect on the real file/stream
        \note public only for test reasons
        \param r number of rows to skip
        \param c number of elements to skip
        \returns the number of elements to skip
      */
      Fixed<31> adv(Fixed<31> r, Fixed<31> c);

      /** advance routine 3 dimensional

        just update to logical file position
        has no effect on the real file/stream
        \note public only for test reasons
        \param p number of pages to skip
        \param r number of rows to skip
        \param c number of elements to skip
        \returns the number of elements to skip
      */
      Fixed<31> adv(Fixed<31> p, Fixed<31> r, Fixed<31> c);

   protected:
      /**
         Pointer to the dimension object for this dation.
         Each user dation needs a dimension specification.
      */
      DationDim * dim;

   public:
      /**
        Reference to the parent systemdation

        The two types of userdations (ALPHIC and TYPE) has
        to hold a reference of
        their parent systemdation.

      */
      SystemDationNB* parent;

#if 0
      /**
      Current system dation object during operation - this may be different
      to system if the system dation supports multiple i/o-channels
      */
      SystemDationNB* systemDation;
#endif

      /**
        Size of the elements to read/write from/to the dation
        in bytes.
        This value is needed for the calculation of the byte offsets
        when doing positioning commands.
      */
      Fixed<31> stepSize;


      /**
      Constructor for a UserDationNB (non basic), will be called by
      DationRW and DationPG.

      \param parent pointer to system dation, which provides the i/o-
              operations
      \param dationParams dation attributes as defined in Dation.h.
             Different attributes may be set by oring them
      \param dimensions pointer to dimension object.
             Each userdation need a dimension specification.
      \param dt dation type (ALPHIC or TYPE)
      */
      UserDationNB(SystemDationNB* parent,
                   int & dationParams,
                   DationDim * dimensions,
                   DationType dt);


      /**
        Method for 1-dimensional positioning

        The attribute DIRECT is required.

        \param c absolute column position

        \note throws various exceptions
      */
      void pos(Fixed<31> c);

      /**
        Method for 2-dimensional positioning

        The attribute DIRECT is required.


        \param row absolute postion of the row on current page
        \param col absolute column in row

        \note throws various exceptions
      */

      void pos(Fixed<31> row, Fixed<31> col);

      /**
       Method for 3-dimensional positioning on a rw-userdation
       depends on setdim and stepsize

        The attribute DIRECT is required.


       \param page absolute position for the page to seek
       \param row absolute postion of the row on page
       \param col absolute column in row

       \note throws various exceptions
      */
      void pos(Fixed<31> page, Fixed<31> row, Fixed<31> col);

      /**
        Counterpart of one dimensional pos().
         Returns current position
        of element(column) and write it to element.

        In case of the last position, the return value may be larger
        than the dimension specifies!

        \param element contains current position of element after
               execution
      */
      void sop(Fixed<31>* element);

      /**

        In case of the last position, the return value may be larger
        than the dimension specifies!

        \param element contains current position of element after
               execution

        \param row contains current row after execution

        \throws InternalDationSignal if dimension of the dation is one

        \note throws various exceptions
      */
      void sop(Fixed<31>* row, Fixed<31>* element);

      /**
        Counterpart of Pos. Returns current Position of element(column) and
        row(line) write it to element.

        In case of the last position, the return value may be larger
        than the dimension specifies!

        \param element contains current position of element after
               execution

        \param row contains current row after execution

        \param page contains current page after execution

        \throws InternalDationSignal if the dimension of the dation is
                smaller than three

        \note throws various exceptions
      */
      void sop(Fixed<31>* page, Fixed<31>* row, Fixed<31>* element);

      /**
        position to the given column in the current row
 
        \param element contains current position of element after
               execution

        \throws InternalDationSignal if the dimension of the dation is
                smaller than three, or if the dation is not DIRECT
      */
      void col(Fixed<31> element);

      /**
        position to the given first poistion of the given row
 
        \param element contains current position of element after
               execution

        \throws InternalDationSignal if the dimension of the dation is
                smaller than three, or if the dation is not DIRECT
      */
      void line(Fixed<31> r);

      /**
        Method for 1-dimensional relative positioning

        If the dimension of the dation has more dimensions, the
        upper dimensions are treated as 0

        FORWARD dations fill the gap with 0/space.

        \throws DationIndexBoundary if the modification would
               violate the boundaries and NOCYCLIC is set
        \throw PositioningFailedSignal if dation is FORWARD and c < 1

        \param c offset column versus current location

        \note throws various exceptions
      */
      void toAdv(Fixed<31> c);

      /**
        Method for 2-dimensional relative positioning

        If the dimension of the dation has more dimensions, the
        upper dimensions are treated as 0

        FORWARD dations fill the gap with 0/space.


        \throws DationIndexBoundary if the modification would
               violate the column boundaries and NOSTREAM is set
        \throws DationIndexBoundary if the modification would
               violate the boundaries and NOCYCLIC is set
        \throw PositioningFailedSignal if dation is FORWARD and
               the new position would be backward

        \param row offset position of the row versus the current location
        \param c offset column versus current location

        \note throws various exceptions if no RST-Variable is set
      */
      void toAdv(Fixed<31> row, Fixed<31> c);

      /**
        Method for 3-dimensional relative positioning

        If the dimension of the dation has more dimensions, the
        upper dimensions are treated as 0

        FORWARD dations fill the gap with 0/space.


        \throws DationIndexBoundary if the modification would
               violate the column boundaries and NOSTREAM is set
        \throws DationIndexBoundary if the modification would
               violate the row boundaries and NOSTREAM is set
        \throws DationIndexBoundary if the modification would
               violate the boundaries and NOCYCLIC is set
        \throw PositioningFailedSignal if dation is FORWARD and
               the new position would be backward

        \param page offset position of the row versus the current location
        \param row offset position of the row versus the current location
        \param col offset column versus current location

        \note throws various exceptions
      */
      void toAdv(Fixed<31> page, Fixed<31> row, Fixed<31> col);

      /**
       Method to fill elements with default value (0/space)

       \param n number of elements to skip
       \throws ... various exceptions from underlying system
      */
      void toX(const Fixed<31> n);

      /**
       Method to fill rows with default value (0/space)

       \param n number of rows to skip
       \throws ... various exceptions from underlying system
      */
      void toSkip(const Fixed<31> n);

      /**
       Method to fill pages with default value (0/space)

       \param n number of pages to skip
       \throws ... various exceptions from underlying system
      */
      void toPage(const Fixed<31> n);


      /**
        Method for 1-dimensional relative positioning

        If the dimension of the dation has more dimensions, the
        upper dimensions are treated as 0

        FORWARD dations skip the gap by dummy reads.

        \throws DationIndexBoundary if the modification would
               violate the boundaries and NOCYCLIC is set
        \throw PositioningFailedSignal if dation is FORWARD and c < 1

        \param c offset column versus current location

        \note throws various exceptions
      */
      void fromAdv(Fixed<31> c);

      /**
        Method for 2-dimensional relative positioning

        If the dimension of the dation has more dimensions, the
        upper dimensions are treated as 0

        FORWARD dations skip the gap by dummy reads.


        \throws DationIndexBoundary if the modification would
               violate the column boundaries and NOSTREAM is set
        \throws DationIndexBoundary if the modification would
               violate the boundaries and NOCYCLIC is set
        \throw PositioningFailedSignal if dation is FORWARD and
               the new position would be backward

        \param row offset position of the row versus the current location
        \param c offset column versus current location

        \note throws various exceptions if no RST-Variable is set
      */
      void fromAdv(Fixed<31> row, Fixed<31> c);

      /**
        Method for 3-dimensional relative positioning

        If the dimension of the dation has more dimensions, the
        upper dimensions are treated as 0

        FORWARD dations skip the gap by dummy reads.


        \throws DationIndexBoundary if the modification would
               violate the column boundaries and NOSTREAM is set
        \throws DationIndexBoundary if the modification would
               violate the row boundaries and NOSTREAM is set
        \throws DationIndexBoundary if the modification would
               violate the boundaries and NOCYCLIC is set
        \throw PositioningFailedSignal if dation is FORWARD and
               the new position would be backward

        \param page offset position of the row versus the current location
        \param row offset position of the row versus the current location
        \param col offset column versus current location

        \note throws various exceptions
      */
      void fromAdv(Fixed<31> page, Fixed<31> row, Fixed<31> col);

      /**
       Method to fill elements with default value (0/space)

        FORWARD dations skip the gap by dummy reads.

       \param n number of elements to skip
       \throws ... various exceptions from underlying system
      */
      void fromX(const Fixed<31> n);

      /**
       Method to skip rows

        FORWARD dations skip the gap by dummy reads.

       \param n number of rows to skip
       \throws ... various exceptions from underlying system
      */
      void fromSkip(const Fixed<31> n);

      /**
       Method to skip pages

        FORWARD dations skip the gap by dummy reads.

       \param n number of pages to skip
       \throws ... various exceptions from underlying system
      */
      void fromPage(const Fixed<31> n);

      /**
      position to EOF (end of file)
      */
      void eof();

      /**
        internal open function. build path to device, performs
        pre and post Open and creates the FILE descriptor (dev)

        \note have to be implemented in system
               specific Dation implementation
      */

      virtual void internalOpen() = 0;

      /**
        internal close function. Close dev and delete it
        (if closeParam CAN is set)

        \note have to be implemented in system
              specific Dation implementation

      */
      virtual void internalClose() = 0;


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
      interface to close the BASIC or NON-Basic system dation
      \param dationParams contains tge dation parameters
      \note may throw exceptions
      */
      void closeSystemDation(int dationParams);

      /**
        treat one output job entry, which must be a positioning element

        \param me pointer to the calling task
        \param jobFormat  pointer to the current entry
        \returns 0, if done normally<br>
                 1, if record wasd left
        */
      int toPositioningFormat(TaskCommon * me, IOFormatEntry * jobFormat);

      /**
        treat one input job entry, which must be a positioning element

        \param me pointer to the calling task
        \param jobFormat  pointer to the current entry
        \returns 0, if done normally<br>
                 1, if record wasd left
        */
      int fromPositioningFormat(TaskCommon * me, IOFormatEntry * jobFormat);


   protected:
      /** assert dation properties

       \throw InternalDationSignal if dation is not direct
       \throw DationNotOpenSignal if dation is not opened
      */

      void assertOpenDirect();

      /** assert dation properties

       \throw DationNotSupportedSignal if dation is nether DIRECT nor FORWARD
       \throw DationNotOpenSignal if dation is not opened
      */
      void assertOpenDirectOrForward();


   };
}
#endif
