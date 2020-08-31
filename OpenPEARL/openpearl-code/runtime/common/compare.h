/*
 [The "BSD license"]
 Copyright (c) 2012-2017 Rainer Mueller
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

#ifndef COMPARE_H_INCLUDED
#define COMPARE_H_INCLUDED
#include "Fixed.h"
#include "Float.h"
#include "BitString.h"
#include "Character.h"
#include "CharSlice.h"
#include "Ref.h"
#include "RefArray.h"

/**
\file
compare functions for data types Fixed, (Float, Character, Clock, Duration),
Ref and RefArray

The compare functions must return a BitString<1> value. This make lot
of problems in the corresponding classes.
The definition as non member methods overcomes these C++ language problems.
*/

namespace pearlrt {
   /* -------------------------------------------------------------- */
   /* Fixed<S> comparisons                                           */
   /* -------------------------------------------------------------- */

   /**
   compare equal operation for type Fixed<S>

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if both are equal<br> else '0'B1
   */
   template<int S, int P>
   BitString<1> operator== (const Fixed<S> lhs, const Fixed<P> & rhs)  {
      Fixed < (S > P) ? S : P > l(lhs.x), r(rhs.x);
      return BitString<1>(lhs.x == rhs.x);
   }

   /**
   compare not equal operation for type Fixed<S>

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if both are not equal<br> else '0'B1
   */
   template<int S, int P>
   BitString<1> operator!= (const Fixed<S> lhs, const Fixed<P> & rhs)  {
      Fixed < (S > P) ? S : P > l(lhs.x), r(rhs.x);
      return BitString<1>(lhs.x != rhs.x);
   }

   /**
   compare less  operation for type Fixed<S>

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if the left side is smaller than the right side<br>
           '0'B1 else
   */
   template<int S, int P>
   BitString<1> operator< (const Fixed<S> lhs, const Fixed<P> & rhs)  {
      Fixed < (S > P) ? S : P > l(lhs.x), r(rhs.x);
      return BitString<1>(lhs.x < rhs.x);
   }

   /**
   compare less or equal  operation for type Fixed<S>

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if the left side is smaller than the right side<br>
           '0'B1 else
   */
   template<int S, int P>
   BitString<1> operator<= (const Fixed<S> lhs, const Fixed<P> & rhs)  {
      Fixed < (S > P) ? S : P > l(lhs.x), r(rhs.x);
      return BitString<1>(lhs.x <= rhs.x);
   }

   /**
   compare greater  operation for type Fixed<S>

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if the left side is smaller than the right side<br>
           '0'B1 else
   */
   template<int S, int P>
   BitString<1> operator> (const Fixed<S> lhs, const Fixed<P> & rhs)  {
      Fixed < (S > P) ? S : P > l(lhs.x), r(rhs.x);
      return BitString<1>(lhs.x > rhs.x);
   }

   /**
   compare greater equal  operation for type Fixed<S>

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if the left side is smaller than the right side<br>
           '0'B1 else
   */
   template<int S, int P>
   BitString<1> operator>= (const Fixed<S> lhs, const Fixed<P> & rhs)  {
      Fixed < (S > P) ? S : P > l(lhs.x), r(rhs.x);
      return BitString<1>(lhs.x >= rhs.x);
   }

   /* -------------------------------------------------------------- */
   /* Float<S> comparisons                                           */
   /* -------------------------------------------------------------- */


   /**
   compare less than

   \param lhs left hand side of the comparison
   \param rhs right hand side of the comparison
   \tparam S length of the lhs Float value
   \tparam R type of the rhs value (Fixed or Float expected)
   \tparam T length of the rhs value

   \returns BitString<1> value according to the comparison result
   */
   template <int S, int T, template<int> class R>
   BitString<1> operator<(const Float<S> & lhs, const R<T> & rhs) {
      return lhs.x < rhs.x;
   }

   /**
   compare less or equal

   \param lhs left hand side of the comparison
   \param rhs right hand side of the comparison
   \tparam S length of the lhs Float value
   \tparam R type of the rhs value (Fixed or Float expected)
   \tparam T length of the rhs value

   \returns BitString<1> value according to the comparison result
   */
   template <int S, int T, template<int> class R>
   BitString<1> operator<=(const Float<S> & lhs, const R<T> & rhs) {
      return lhs.x <= rhs.x;
   }

   /**
   compare not equal

   \param lhs left hand side of the comparison
   \param rhs right hand side of the comparison
   \tparam S length of the lhs Float value
   \tparam R type of the rhs value (Fixed or Float expected)
   \tparam T length of the rhs value

   \returns BitString<1> value according to the comparison result
   */
   template <int S, int T, template<int> class R>
   BitString<1> operator!=(const Float<S> & lhs, const R<T> & rhs) {
      return lhs.x != rhs.x;
   }

   /**
   compare equal

   \param lhs left hand side of the comparison
   \param rhs right hand side of the comparison
   \tparam S length of the lhs Float value
   \tparam R type of the rhs value (Fixed or Float expected)
   \tparam T length of the rhs value

   \returns BitString<1> value according to the comparison result
   */
   template <int S, int T, template<int> class R>
   BitString<1> operator==(const Float<S> & lhs, const R<T> & rhs) {
      return lhs.x == rhs.x;
   }

   /**
   compare greater or equal

   \param lhs left hand side of the comparison
   \param rhs right hand side of the comparison
   \tparam S length of the lhs Float value
   \tparam R type of the rhs value (Fixed or Float expected)
   \tparam T length of the rhs value

   \returns BitString<1> value according to the comparison result
   */
   template <int S, int T, template<int> class R>
   BitString<1> operator>=(const Float<S> & lhs, const R<T> & rhs) {
      return lhs.x >= rhs.x;
   }

   /**
   compare greater

   \param lhs left hand side of the comparison
   \param rhs right hand side of the comparison
   \tparam S length of the lhs Float value
   \tparam R type of the rhs value (Fixed or Float expected)
   \tparam T length of the rhs value

   \returns BitString<1> value according to the comparison result
   */
   template <int S, int T, template<int> class R>
   BitString<1> operator>(const Float<S> & lhs, const R<T> & rhs) {
      return lhs.x > rhs.x;
   }

   /**
   compare less than

   \param lhs left hand side of the comparison
   \param rhs right hand side of the comparison
   \tparam S length of the lhs Float value
   \tparam T length of the rhs value

   \returns BitString<1> value according to the comparison result
   */
   template <int S, int T>
   BitString<1> operator<(const Fixed<S> & lhs, const Float<T> & rhs) {
      return lhs.x < rhs.x;
   }

   /**
   compare less or equal

   \param lhs left hand side of the comparison
   \param rhs right hand side of the comparison
   \tparam S length of the lhs Float value
   \tparam T length of the rhs value

   \returns BitString<1> value according to the comparison result
   */
   template <int S, int T>
   BitString<1> operator<=(const Fixed<S> & lhs, const Float<T> & rhs) {
      return lhs.x <= rhs.x;
   }

   /**
   compare not equal

   \param lhs left hand side of the comparison
   \param rhs right hand side of the comparison
   \tparam S length of the lhs Float value
   \tparam T length of the rhs value

   \returns BitString<1> value according to the comparison result
   */
   template <int S, int T>
   BitString<1> operator!=(const Fixed<S> & lhs, const Float<T> & rhs) {
      return lhs.x != rhs.x;
   }

   /**
   compare equal

   \param lhs left hand side of the comparison
   \param rhs right hand side of the comparison
   \tparam S length of the lhs Float value
   \tparam T length of the rhs value

   \returns BitString<1> value according to the comparison result
   */
   template <int S, int T>
   BitString<1> operator==(const Fixed<S> & lhs, const Float<T> & rhs) {
      return lhs.x == rhs.x;
   }

   /**
   compare greater or equal

   \param lhs left hand side of the comparison
   \param rhs right hand side of the comparison
   \tparam S length of the lhs Float value
   \tparam T length of the rhs value

   \returns BitString<1> value according to the comparison result
   */
   template <int S, int T>
   BitString<1> operator>=(const Fixed<S> & lhs, const Float<T> & rhs) {
      return lhs.x >= rhs.x;
   }

   /**
   compare greater than

   \param lhs left hand side of the comparison
   \param rhs right hand side of the comparison
   \tparam S length of the lhs Float value
   \tparam T length of the rhs value

   \returns BitString<1> value according to the comparison result
   */
   template <int S, int T>
   BitString<1> operator>(const Fixed<S> & lhs, const Float<T> & rhs) {
      return lhs.x > rhs.x;
   }

   /* -------------------------------------------------------------- */
   /* Character<S> comparisons                                       */
   /* -------------------------------------------------------------- */

   /**
   compare equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam P length of the right side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t S, size_t P>
   BitString<1> operator== (const Character<S> & lhs,
                            const Character<P> & rhs) {
      int r = characterCompare(lhs.data, S, rhs.data, P);
      return BitString<1>(r == 0);
   }

   /**
   compare not equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam P length of the right side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t S, size_t P>
   BitString<1> operator!= (const Character<S> & lhs,
                            const Character<P> & rhs) {
      int r = characterCompare(lhs.data, S, rhs.data, P);
      return BitString<1>(r != 0);
   }

   /**
   compare less operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam P length of the right side character string
   \returns '1'B1, if the first value is less than the second value<br>
            '0'B1 else
   */
   template<size_t S, size_t P>
   BitString<1> operator< (const Character<S> & lhs,
                           const Character<P> & rhs) {
      int r = characterCompare(lhs.data, S, rhs.data, P);
      return BitString<1>(r < 0);
   }

   /**
   compare less equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam P length of the right side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t S, size_t P>
   BitString<1> operator<= (const Character<S> & lhs,
                            const Character<P> & rhs) {
      int r = characterCompare(lhs.data, S, rhs.data, P);
      return BitString<1>(r <= 0);
   }


   /**
   compare greater than operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam P length of the right side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t S, size_t P>
   BitString<1> operator> (const Character<S> & lhs,
                           const Character<P> & rhs) {
      int r = characterCompare(lhs.data, S, rhs.data, P);
      return BitString<1>(r > 0);
   }


   /**
   compare greater or equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam P length of the right side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t S, size_t P>
   BitString<1> operator>= (const Character<S> & lhs,
                            const Character<P> & rhs) {
      int r = characterCompare(lhs.data, S, rhs.data, P);
      return BitString<1>(r >= 0);
   }

   /* -------------------------------------------------------------- */
   /* CharSlice,Character<S> comparisons                             */
   /* -------------------------------------------------------------- */

   /**
   compare equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam P length of the right side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t P>
   BitString<1> operator== (const CharSlice & lhs,
                            const Character<P> & rhs) {
      int r = characterCompare(lhs.data, lhs.length.x, rhs.data, P);
      return BitString<1>(r == 0);
   }

   /**
   compare not equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam P length of the right side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t P>
   BitString<1> operator!= (const CharSlice & lhs,
                            const Character<P> & rhs) {
      int r = characterCompare(lhs.data, lhs.length.x, rhs.data, P);
      return BitString<1>(r != 0);
   }

   /**
   compare less operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam P length of the right side character string
   \returns '1'B1, if the first value is less than the second value<br>
            '0'B1 else
   */
   template<size_t P>
   BitString<1> operator< (const CharSlice & lhs,
                           const Character<P> & rhs) {
      int r = characterCompare(lhs.data, lhs.length.x, rhs.data, P);
      return BitString<1>(r < 0);
   }

   /**
   compare less equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam P length of the right side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t P>
   BitString<1> operator<= (const CharSlice & lhs,
                            const Character<P> & rhs) {
      int r = characterCompare(lhs.data, lhs.length.x, rhs.data, P);
      return BitString<1>(r <= 0);
   }


   /**
   compare greater than operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam P length of the right side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t P>
   BitString<1> operator> (const CharSlice & lhs,
                           const Character<P> & rhs) {
      int r = characterCompare(lhs.data, lhs.length.x, rhs.data, P);
      return BitString<1>(r > 0);
   }


   /**
   compare greater or equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam P length of the right side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t P>
   BitString<1> operator>= (const CharSlice & lhs,
                            const Character<P> & rhs) {
      int r = characterCompare(lhs.data, lhs.length.x, rhs.data, P);
      return BitString<1>(r >= 0);
   }

   /* -------------------------------------------------------------- */
   /* Character<S>,CharSlice> comparisons                            */
   /* -------------------------------------------------------------- */

   /**
   compare equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam S length of the left side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t S>
   BitString<1> operator== (const Character<S> & lhs,
                            const CharSlice & rhs) {
      int r = characterCompare(lhs.data, S, rhs.data, rhs.length.x);
      return BitString<1>(r == 0);
   }

   /**
   compare not equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam S length of the left side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t S>
   BitString<1> operator!= (const Character<S> & lhs,
                            const CharSlice & rhs) {
      int r = characterCompare(lhs.data, S, rhs.data, rhs.length.x);
      return BitString<1>(r != 0);
   }

   /**
   compare less operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam S length of the left side character string
   \returns '1'B1, if the first value is less than the second value<br>
            '0'B1 else
   */
   template<size_t S>
   BitString<1> operator< (const Character<S> & lhs,
                           const CharSlice & rhs) {
      int r = characterCompare(lhs.data, S, rhs.data, rhs.length.x);
      return BitString<1>(r < 0);
   }

   /**
   compare less equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam S length of the left side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t S>
   BitString<1> operator<= (const Character<S> & lhs,
                            const CharSlice & rhs) {
      int r = characterCompare(lhs.data, S, rhs.data, rhs.length.x);
      return BitString<1>(r <= 0);
   }


   /**
   compare greater than operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam S length of the left side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t S>
   BitString<1> operator> (const Character<S> & lhs,
                           const CharSlice & rhs) {
      int r = characterCompare(lhs.data, S, rhs.data, rhs.length.x);
      return BitString<1>(r > 0);
   }


   /**
   compare greater or equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \tparam S length of the left side character string
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   template<size_t S>
   BitString<1> operator>= (const Character<S> & lhs,
                            const CharSlice & rhs) {
      int r = characterCompare(lhs.data, S, rhs.data, rhs.length.x);
      return BitString<1>(r >= 0);
   }

   /* -------------------------------------------------------------- */
   /* CharSlice,CharSlice comparisons 				     */
   /* -------------------------------------------------------------- */

   /**
   compare equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   BitString<1> operator== (const CharSlice & lhs,
                            const CharSlice & rhs);

   /**
   compare not equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   BitString<1> operator!= (const CharSlice & lhs,
                            const CharSlice & rhs);

   /**
   compare less operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if the first value is less than the second value<br>
            '0'B1 else
   */
   BitString<1> operator< (const CharSlice & lhs,
                           const CharSlice & rhs);

   /**
   compare less equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   BitString<1> operator<= (const CharSlice & lhs,
                            const CharSlice & rhs);


   /**
   compare greater than operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   BitString<1> operator> (const CharSlice & lhs,
                           const CharSlice & rhs);


   /**
   compare greater or equal operation

   the shorter type will be expanded to the size of the longer type

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if both are equal<br> '0'B1 else
   */
   BitString<1> operator>= (const CharSlice & lhs,
                            const CharSlice & rhs);

   /* -------------------------------------------------------------- */
   /* Ref<> comparisons (IS,ISNT)				     */
   /* -------------------------------------------------------------- */

   /**
   compare equal operation

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if both pointer are identical
   */
   template <class C>
   BitString<1> operator== (const Ref<C> & lhs,
                            const Ref<C> & rhs) {
      return BitString<1>(lhs.x == rhs.x);
   }    
   template <class C>
   BitString<1> operator== (const C & lhs,
                            const Ref<C> & rhs) {
      return BitString<1>(&lhs == rhs.x);
   }    
   template <class C>
   BitString<1> operator== (const Ref<C> & lhs,
                            const C & rhs) {
      return BitString<1>(lhs.x == &rhs);
   }    
   template <class C>
   BitString<1> operator== (const Ref<char> & lhs,
                            const Ref<C> & rhs) {
      return BitString<1>((C*)(lhs.x) == rhs.x);
   }    
   template <class C>
   BitString<1> operator== (const Ref<C> & lhs,
                            const Ref<char> & rhs) {
      return BitString<1>(lhs.x == (C*)(rhs.x));
   }    

   /**
   compare not equal operation

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if both pointer are not identical
   */
   template <class C>
   BitString<1> operator!= (const Ref<C> & lhs,
                            const Ref<C> & rhs) {
      return BitString<1>(lhs.x != rhs.x);
   }    
   template <class C>
   BitString<1> operator!= (const C & lhs,
                            const Ref<C> & rhs) {
      return BitString<1>(&lhs != rhs.x);
   }    
   template <class C>
   BitString<1> operator!= (const Ref<C> & lhs,
                            const C & rhs) {
      return BitString<1>(lhs.x != &rhs);
   }    
   template <class C>
   BitString<1> operator!= (const Ref<char> & lhs,
                            const Ref<C> & rhs) {
      return BitString<1>((C*)(lhs.x) != rhs.x);
   }    
   template <class C>
   BitString<1> operator!= (const Ref<C> & lhs,
                            const Ref<char> & rhs) {
      return BitString<1>(lhs.x != (C*)(rhs.x));
   }    

   /* -------------------------------------------------------------- */
   /* RefArray<> comparisons (IS,ISNT)				     */
   /* IS: data and descriptor must match 			     */
   /* ISNT: data or descriptor does not match 			     */
   /* -------------------------------------------------------------- */

   /**
   compare equal operation

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if both pointer are identical
   */
   template <class C>
   BitString<1> operator== (const RefArray<C> & lhs,
                            const RefArray<C> & rhs) {
      return BitString<1>(lhs.data == rhs.data && lhs.descriptor == rhs.descriptor);
   }    
   template <class C>
   BitString<1> operator== (const Ref<char> & lhs,
                            const RefArray<C> & rhs) {
      return BitString<1>(rhs.data == NULL && rhs.descriptor == NULL);
   }    
   template <class C>
   BitString<1> operator== (const RefArray<C> & lhs,
                            const Ref<char> & rhs) {
      return BitString<1>(lhs.data == NULL && lhs.descriptor == NULL);
   }    

   /**
   compare not equal operation

   \param lhs the first parameter of the comparison
   \param rhs the second parameter of the comparison
   \returns '1'B1, if both pointer are not identical
   */
   template <class C>
   BitString<1> operator!= (const RefArray<C> & lhs,
                            const RefArray<C> & rhs) {
      return BitString<1>(lhs.data != rhs.data || lhs.descriptor != rhs.descriptor);
   }    
   template <class C>
   BitString<1> operator!= (const Ref<char> & lhs,
                            const RefArray<C> & rhs) {
      return BitString<1>(rhs.data != NULL || rhs.descriptor != NULL);
   }    
   template <class C>
   BitString<1> operator!= (const RefArray<C> & lhs,
                            const Ref<char> & rhs) {
      return BitString<1>(lhs.data != NULL || lhs.descriptor != NULL);
   }    


}
#endif

