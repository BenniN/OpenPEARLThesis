/*
 [A "BSD license"]
 Copyright (c) 2020 Rainer Mueller
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

#ifndef REFARRAY_H_INCLUDED
#define REFARRAY_H_INCLUDED

#include "Signals.h"
#include "Ref.h"
#include "Array.h"
#include "Log.h"

/**
\file
*/

namespace pearlrt {
   /**
   \addtogroup datatypes
   @{
   */

   /**

   Reference to an array variable

   The OpenPEARL system translates REF into native pointers, which are
   initialized with NULL. There is not explicit initialisation enforced
   by the language definition.
   Thus each access to the object via the  reference must check, if the
   pointer is valid. This test is done in this template method, which
   works for all kinds of references (except REF CHAR()) of the PEARL
   application.

   Usage in the C++ code (without explicit namespace):

   \code
   Fixed<15> x;
   Fixed<15> data_x(10);
   RefArray< Fixed<15> > y;
   ...
   RefArray< Fixed<15> > y1(ad_1_1_10, data_x);  // auto initialization
   ...
   y = RefArray(ad_1_1_10,data_x);
   x = *(y.getArrayData()+y.getArrayDescriptor()->offset(1));
   // Usage in an assignment
   *(y.getArrayData()+y.getArrayDescriptor()->offset(1)) = x;

   \endcode

   \tparam C the class of the referenced variable. This may be any
             kind of class.
   */

   template<class C> class RefArray {
   public:
      C * data;   ///< the pointer to the real object
      Array * descriptor;

   public:
      /**
      ctor for plain REF variable declaration
      */
      RefArray() {
         data = NULL;
         descriptor = NULL;
      }

      /**
      ctor for REF variable declaration with preset
      \param pValue the variable which should by adressed
                     by this REF
      */

      RefArray(Array* descr, C* data) {
         this-> data = data;
         this-> descriptor = descr;
      }

      /**
      obtain descriptor of a RefArray variable

      This may be used in expressions and as target of assignments

      \returns the object as reference type of C++.

      \throws   RefNotInitializedSignal if the reference is not
                           initialized
      */
      Array* getArrayDescriptor() {
         if (descriptor) {
            return descriptor;
         }

         Log::error("Ref::use of uninitialized reference");
         throw theRefNotInitializedSignal;
      }

      /**
      obtain data of a RefArray variable

      This may be used in expressions and as target of assignments

      \returns the object as reference type of C++.

      \throws   RefNotInitializedSignal if the reference is not
                           initialized
      */
      C* getArrayData() {
         if (data) {
            return data;
         }

         Log::error("Ref::use of uninitialized reference");
         throw theRefNotInitializedSignal;
      }

      /**
      set new reference

      \param rhs the new object to point to
      */
      void operator=( RefArray<C> rhs) {
       // * this = rhs;
        this->data = rhs.getArrayData();
        this->descriptor = rhs.getArrayDescriptor();
      }

      /**
      set reference to NIL

      \param rhs the new object to point to
      */
      void operator=( Ref<char> &rhs) {
        data = (C*)0;
        descriptor = NULL;
      }

   };
   /** @} */
}
#endif

