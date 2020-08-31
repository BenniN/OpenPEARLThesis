/*
 [The "BSD license"]
 Copyright (c) 2014-2014 Rainer Mueller
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
/**
\file

\brief module tests for Float<S> class

These tests use google test frame to verify the proper operation of the methode
implemented in Float.h
*/

#include "gtest.h"

#include "Signals.h"
#include "Float.h"
#include "Fixed.h"
#include "compare.h"



/**
Unit tests for sizes
*/
TEST(Float, Sizes) {
   EXPECT_EQ(sizeof((pearlrt::Float<23>)1), 4);
   EXPECT_EQ(sizeof((pearlrt::Float<52>)1), 8);
}

/**
Unit tests for Ctor
*/
TEST(Float, CTor) {
   pearlrt::Fixed<31> x(2000000000);
   pearlrt::Float<23> y(x);
   ASSERT_EQ(y.x, x.x);
}

/**
Unit tests for  operations with Float(23) operands
*/
TEST(Float, Float_23) {
   pearlrt::Float<23> x(1);
   pearlrt::Float<23> y, z;

   ASSERT_THROW(y + y, pearlrt::FloatIsNaNSignal);
   x = (pearlrt::Float<23>)2;
   y = (pearlrt::Float<23>)4;
   z = -y;
   EXPECT_EQ(z.x, -4);
   z = x + y;
   EXPECT_EQ(z.x, 6);
   z = x - y;
   EXPECT_EQ(z.x, -2);
   z = x * y;
   EXPECT_EQ(z.x, 8);
   z = x / y;
   EXPECT_EQ(z.x, 0.5);
   x = (pearlrt::Float<23>)2;
   y = (pearlrt::Float<23>)0;
   ASSERT_THROW(x / y, pearlrt::FloatIsINFSignal);
}

/**
Unit tests for  operations with Float(52) operands
*/
TEST(Float, Float_52) {
   pearlrt::Float<52> x(1);
   pearlrt::Float<52> y, z;

   ASSERT_THROW(y + y, pearlrt::FloatIsNaNSignal);

   x = (pearlrt::Float<52>)2;
   y = (pearlrt::Float<52>)4;
   z = -y;
   EXPECT_EQ(z.x, -4);
   z = x + y;
   EXPECT_EQ(z.x, 6);
   z = x - y;
   EXPECT_EQ(z.x, -2);
   z = x * y;
   EXPECT_EQ(z.x, 8);
   z = x / y;
   EXPECT_EQ(z.x, 0.5);
   x = (pearlrt::Float<52>)2;
   y = (pearlrt::Float<52>)0;
   ASSERT_THROW(x / y, pearlrt::FloatIsINFSignal);

   pearlrt::Float<23> u;
   u = x.fit(u);
   EXPECT_EQ(u.x, 2);

   x = (pearlrt::Float<52>)1e100;
   ASSERT_THROW(x.fit(u), pearlrt::FloatIsINFSignal);

}

/**
Unit tests for mixed operations  with Float(23) and Float(52)
*/
TEST(Float, MixedOperations) {
   pearlrt::Float<23> x(1);
   pearlrt::Float<52> y, z;

   x = (pearlrt::Float<23>)2;
   y = (pearlrt::Float<52>)4;
   z = -x;
   EXPECT_EQ(z.x, -2);
   z = x + y;
   EXPECT_EQ(z.x, 6);
   z = x - y;
   EXPECT_EQ(z.x, -2);
   z = x * y;
   EXPECT_EQ(z.x, 8);
   z = x / y;
   EXPECT_EQ(z.x, 0.5);
   x = (pearlrt::Float<23>)2;
   y = (pearlrt::Float<52>)0;
   ASSERT_THROW(x / y, pearlrt::FloatIsINFSignal);

   x = (pearlrt::Float<23>)4;
   y = (pearlrt::Float<52>)2;
   z = y + x;
   EXPECT_EQ(z.x, 6);
   z = y - x;
   EXPECT_EQ(z.x, -2);
   z = y * x;
   EXPECT_EQ(z.x, 8);
   z = y / x;
   EXPECT_EQ(z.x, 0.5);
   x = (pearlrt::Float<23>)0;
   y = (pearlrt::Float<52>)2;
   ASSERT_THROW(y / x, pearlrt::FloatIsINFSignal);
}

/**
Unit tests for  compare operations  with Float(23)
*/
TEST(Float, CompareOperations_23) {
   pearlrt::Float<23> x(1), y(2);

   EXPECT_TRUE((x == x).getBoolean());
   EXPECT_FALSE((x == y).getBoolean());
   EXPECT_FALSE((x != x).getBoolean());
   EXPECT_TRUE((x != y).getBoolean());
   EXPECT_TRUE((x < y).getBoolean());
   EXPECT_FALSE((y < x).getBoolean());
   EXPECT_TRUE((x <= y).getBoolean());
   EXPECT_TRUE((x <= x).getBoolean());
   EXPECT_FALSE((y <= x).getBoolean());
   EXPECT_FALSE((x > y).getBoolean());
   EXPECT_TRUE((y > x).getBoolean());
   EXPECT_FALSE((x >= y).getBoolean());
   EXPECT_TRUE((y >= x).getBoolean());
   EXPECT_TRUE((x >= x).getBoolean());
}

/**
Unit tests for  compare operations  with Float(52)
*/
TEST(Float, CompareOperations_52) {
   pearlrt::Float<52> x(1), y(2);

   EXPECT_TRUE((x == x).getBoolean());
   EXPECT_FALSE((x == y).getBoolean());
   EXPECT_FALSE((x != x).getBoolean());
   EXPECT_TRUE((x != y).getBoolean());
   EXPECT_TRUE((x < y).getBoolean());
   EXPECT_FALSE((y < x).getBoolean());
   EXPECT_TRUE((x <= y).getBoolean());
   EXPECT_TRUE((x <= x).getBoolean());
   EXPECT_FALSE((y <= x).getBoolean());
   EXPECT_FALSE((x > y).getBoolean());
   EXPECT_TRUE((y > x).getBoolean());
   EXPECT_FALSE((x >= y).getBoolean());
   EXPECT_TRUE((y >= x).getBoolean());
   EXPECT_TRUE((x >= x).getBoolean());
}

/**
Unit tests for  compare operations  with Float(23) and Float(52)
*/
TEST(Float, CompareOperations_23_52) {
   pearlrt::Float<23> x(1);
   pearlrt::Float<52> y(2);

   EXPECT_TRUE((x == x).getBoolean());
   EXPECT_FALSE((x == y).getBoolean());
   EXPECT_FALSE((x != x).getBoolean());
   EXPECT_TRUE((x != y).getBoolean());
   EXPECT_TRUE((x < y).getBoolean());
   EXPECT_FALSE((y < x).getBoolean());
   EXPECT_TRUE((x <= y).getBoolean());
   EXPECT_TRUE((x <= x).getBoolean());
   EXPECT_FALSE((y <= x).getBoolean());
   EXPECT_FALSE((x > y).getBoolean());
   EXPECT_TRUE((y > x).getBoolean());
   EXPECT_FALSE((x >= y).getBoolean());
   EXPECT_TRUE((y >= x).getBoolean());
   EXPECT_TRUE((x >= x).getBoolean());
}

/**
Unit tests for  compare operations  with Float(52) and Float(23)
*/
TEST(Float, CompareOperations_52_23) {
   pearlrt::Float<52> x(1);
   pearlrt::Float<23> y(2);

   EXPECT_TRUE((x == x).getBoolean());
   EXPECT_FALSE((x == y).getBoolean());
   EXPECT_FALSE((x != x).getBoolean());
   EXPECT_TRUE((x != y).getBoolean());
   EXPECT_TRUE((x < y).getBoolean());
   EXPECT_FALSE((y < x).getBoolean());
   EXPECT_TRUE((x <= y).getBoolean());
   EXPECT_TRUE((x <= x).getBoolean());
   EXPECT_FALSE((y <= x).getBoolean());
   EXPECT_FALSE((x > y).getBoolean());
   EXPECT_TRUE((y > x).getBoolean());
   EXPECT_FALSE((x >= y).getBoolean());
   EXPECT_TRUE((y >= x).getBoolean());
   EXPECT_TRUE((x >= x).getBoolean());
}

/**
Unit tests with math functions
*/
TEST(Float, math_23) {
   pearlrt::Float<23> x(0);
   pearlrt::Float<23> y(0);
   pearlrt::Float<23> z(0);
   pearlrt::Fixed<31> f;
   pearlrt::Float<23> epsilon(5.0e-6);

   x = (pearlrt::Float<23>) - 10.4;
   y = (pearlrt::Float<23>)10.4;
   EXPECT_TRUE((x.abs() == y).getBoolean());
   EXPECT_TRUE((x.sign() == pearlrt::Fixed<15>(-1)).getBoolean());

   x = (pearlrt::Float<23>)10.4;
   y = (pearlrt::Float<23>)10.4;
   EXPECT_TRUE((x.abs() == y).getBoolean());
   EXPECT_TRUE((x.sign() == pearlrt::Fixed<15>(1)).getBoolean());

   x = (pearlrt::Float<23>)0;
   y = (pearlrt::Float<23>)0;
   EXPECT_TRUE((x.abs() == y).getBoolean());
   EXPECT_TRUE((x.sign() == pearlrt::Fixed<15>(0)).getBoolean());

   x = (pearlrt::Float<23>)10.0;
   EXPECT_TRUE((x.entier() == pearlrt::Fixed<15>(10)).getBoolean());
   EXPECT_TRUE((x.round() == pearlrt::Fixed<15>(10)).getBoolean());

   x = (pearlrt::Float<23>)10.4;
   EXPECT_TRUE((x.entier() == pearlrt::Fixed<15>(10)).getBoolean());
   EXPECT_TRUE((x.round() == pearlrt::Fixed<15>(10)).getBoolean());

   x = (pearlrt::Float<23>)10.5;
   EXPECT_TRUE((x.entier() == pearlrt::Fixed<15>(10)).getBoolean());
   EXPECT_TRUE((x.round() == pearlrt::Fixed<15>(11)).getBoolean());

   x = (pearlrt::Float<23>) - 10.4;
   EXPECT_TRUE((x.entier() == pearlrt::Fixed<15>(-11)).getBoolean());
   EXPECT_TRUE((x.round() == pearlrt::Fixed<15>(-10)).getBoolean());

   x = (pearlrt::Float<23>) - 10.5;
   EXPECT_TRUE((x.entier() == pearlrt::Fixed<15>(-11)).getBoolean());
   EXPECT_TRUE((x.round() == pearlrt::Fixed<15>(-11)).getBoolean());

   x = (pearlrt::Float<23>) - 2;
   f = (pearlrt::Fixed<31>)0;
   y = (pearlrt::Float<23>)1;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());

   x = (pearlrt::Float<23>) - 1;
   f = (pearlrt::Fixed<31>)0;
   y = (pearlrt::Float<23>)1;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());

   x = (pearlrt::Float<23>)0;
   f = (pearlrt::Fixed<31>)0;
   y = (pearlrt::Float<23>)1;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());

   x = (pearlrt::Float<23>)1;
   f = (pearlrt::Fixed<31>)0;
   y = (pearlrt::Float<23>)1;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());

   x = (pearlrt::Float<23>)2;
   f = (pearlrt::Fixed<31>)0;
   y = (pearlrt::Float<23>)1;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());

   x = (pearlrt::Float<23>) - 2;
   f = (pearlrt::Fixed<31>) - 2;
   y = (pearlrt::Float<23>)0.25;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());

   x = (pearlrt::Float<23>) - 2;
   f = (pearlrt::Fixed<31>) - 3;
   y = (pearlrt::Float<23>) - 0.125;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());

   x = (pearlrt::Float<23>) - 2;
   f = (pearlrt::Fixed<31>)3;
   y = (pearlrt::Float<23>) - 8;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());


   for (double phi = 0; phi < 6.3; phi += 0.01) {
      x = (pearlrt::Float<23>)(phi);
      z = x.sin() * x.sin() + x.cos() * x.cos() - (pearlrt::Float<23>)1;
      EXPECT_TRUE((z.abs() < epsilon).getBoolean());
   }

   for (double phi = -3.0; phi < 3.0; phi += 0.01) {
      x = (pearlrt::Float<23>)(phi);
      z = x.sin() / x.cos() - x.tan();
      EXPECT_TRUE((z.abs() < epsilon).getBoolean());
   }

   for (double phi = -1.5; phi < 1.5; phi += 0.01) {
      x = (pearlrt::Float<23>)(phi);
      z = x.tan().atan() - x;
      EXPECT_TRUE((z.abs() < epsilon).getBoolean());
   }

   for (double phi = -10.0; phi < 10.; phi += 0.01) {
      x = (pearlrt::Float<23>)(phi);
      z = x.exp().ln() - x;
      EXPECT_TRUE((z.abs() < epsilon).getBoolean());
   }

   for (double phi = -10.0; phi < 10.; phi += 0.01) {
      x = (pearlrt::Float<23>)(phi);
      z = x.tanh() -
          ((pearlrt::Float<23>)(1) -
           (pearlrt::Float<23>)(2) /
           ((x + x).exp() + (pearlrt::Float<23>)1));
      EXPECT_TRUE((z.abs() < epsilon).getBoolean());
   }

   for (double phi = 0.0; phi < 1000.; phi += 0.01) {
      x = (pearlrt::Float<23>)(phi);
      z = (x * x).sqrt() - x;
      EXPECT_TRUE((z.abs() < epsilon).getBoolean());
   }

   {
      x = (pearlrt::Float<23>)(-1.0);
      EXPECT_THROW(x.ln(), pearlrt::FunctionParameterOutOfRangeException);
      EXPECT_THROW(x.sqrt(), pearlrt::FunctionParameterOutOfRangeException);
   }

}


/**
Unit tests with math functions

Note that the internal assignments are done with Float<23> -> Float<52>.
This is without problems, since the data are small and exact enough
*/
TEST(Float, math_52) {
   pearlrt::Float<52> x(0);
   pearlrt::Float<52> y(0);
   pearlrt::Float<52> z(0);
   pearlrt::Fixed<31> f;
   pearlrt::Float<52> epsilon(5.0e-12);

   x = (pearlrt::Float<52>) - 10.4;
   y = (pearlrt::Float<52>)10.4;
   EXPECT_TRUE((x.abs() == y).getBoolean());
   EXPECT_TRUE((x.sign() == pearlrt::Fixed<15>(-1)).getBoolean());

   x = (pearlrt::Float<52>)10.4;
   y = (pearlrt::Float<52>)10.4;
   EXPECT_TRUE((x.abs() == y).getBoolean());
   EXPECT_TRUE((x.sign() == pearlrt::Fixed<15>(1)).getBoolean());

   x = (pearlrt::Float<52>)0;
   y = (pearlrt::Float<52>)0;
   EXPECT_TRUE((x.abs() == y).getBoolean());
   EXPECT_TRUE((x.sign() == pearlrt::Fixed<15>(0)).getBoolean());

   x = (pearlrt::Float<52>)10.0;
   EXPECT_TRUE((x.entier() == pearlrt::Fixed<15>(10)).getBoolean());
   EXPECT_TRUE((x.round() == pearlrt::Fixed<15>(10)).getBoolean());

   x = (pearlrt::Float<52>)10.4;
   EXPECT_TRUE((x.entier() == pearlrt::Fixed<15>(10)).getBoolean());
   EXPECT_TRUE((x.round() == pearlrt::Fixed<15>(10)).getBoolean());

   x = (pearlrt::Float<52>)10.5;
   EXPECT_TRUE((x.entier() == pearlrt::Fixed<15>(10)).getBoolean());
   EXPECT_TRUE((x.round() == pearlrt::Fixed<15>(11)).getBoolean());

   x = (pearlrt::Float<52>) - 10.4;
   EXPECT_TRUE((x.entier() == pearlrt::Fixed<15>(-11)).getBoolean());
   EXPECT_TRUE((x.round() == pearlrt::Fixed<15>(-10)).getBoolean());

   x = (pearlrt::Float<52>) - 10.5;
   EXPECT_TRUE((x.entier() == pearlrt::Fixed<15>(-11)).getBoolean());
   EXPECT_TRUE((x.round() == pearlrt::Fixed<15>(-11)).getBoolean());

   x = (pearlrt::Float<52>) - 2;
   f = (pearlrt::Fixed<31>)0;
   y = (pearlrt::Float<52>)1;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());

   x = (pearlrt::Float<52>) - 1;
   f = (pearlrt::Fixed<31>)0;
   y = (pearlrt::Float<52>)1;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());

   x = (pearlrt::Float<52>)0;
   f = (pearlrt::Fixed<31>)0;
   y = (pearlrt::Float<52>)1;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());

   x = (pearlrt::Float<52>)1;
   f = (pearlrt::Fixed<31>)0;
   y = (pearlrt::Float<52>)1;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());

   x = (pearlrt::Float<52>)2;
   f = (pearlrt::Fixed<31>)0;
   y = (pearlrt::Float<52>)1;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());

   x = (pearlrt::Float<52>) - 2;
   f = (pearlrt::Fixed<31>) - 2;
   y = (pearlrt::Float<52>)0.25;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());

   x = (pearlrt::Float<52>) - 2;
   f = (pearlrt::Fixed<31>) - 3;
   y = (pearlrt::Float<52>) - 0.125;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());

   x = (pearlrt::Float<52>) - 2;
   f = (pearlrt::Fixed<31>)3;
   y = (pearlrt::Float<52>) - 8;
   EXPECT_TRUE((x.pow(f) == y).getBoolean());


   for (double phi = 0; phi < 6.3; phi += 0.01) {
      x = (pearlrt::Float<52>)(phi);
      z = x.sin() * x.sin() + x.cos() * x.cos() - (pearlrt::Float<52>)1;
      EXPECT_TRUE((z.abs() < epsilon).getBoolean());
   }

   for (double phi = -3.0; phi < 3.0; phi += 0.01) {
      x = (pearlrt::Float<52>)(phi);
      z = x.sin() / x.cos() - x.tan();
      EXPECT_TRUE((z.abs() < epsilon).getBoolean());
   }

   for (double phi = -1.5; phi < 1.5; phi += 0.01) {
      x = (pearlrt::Float<52>)(phi);
      z = x.tan().atan() - x;
      EXPECT_TRUE((z.abs() < epsilon).getBoolean());
   }

   for (double phi = -10.0; phi < 10.; phi += 0.01) {
      x = (pearlrt::Float<52>)(phi);
      z = x.exp().ln() - x;
      EXPECT_TRUE((z.abs() < epsilon).getBoolean());
   }

   for (double phi = -10.0; phi < 10.; phi += 0.01) {
      x = (pearlrt::Float<52>)(phi);
      z = x.tanh() -
          ((pearlrt::Float<52>)(1) -
           (pearlrt::Float<52>)(2) /
           ((x + x).exp() + (pearlrt::Float<23>)1));
      EXPECT_TRUE((z.abs() < epsilon).getBoolean());
   }

   for (double phi = 0.0; phi < 1000.; phi += 0.01) {
      x = (pearlrt::Float<52>)(phi);
      z = (x * x).sqrt() - x;
      EXPECT_TRUE((z.abs() < epsilon).getBoolean());
   }

   {
      x = (pearlrt::Float<52>)(-1.0);
      EXPECT_THROW(x.ln(), pearlrt::FunctionParameterOutOfRangeException);
      EXPECT_THROW(x.sqrt(), pearlrt::FunctionParameterOutOfRangeException);
   }
}

/**
Float + Fixed operations
*/
TEST(Float, fixed) {
   pearlrt::Float<23> x23(1);
   pearlrt::Float<52> x52(1);
   pearlrt::Fixed<15> y(2);
   pearlrt::Float<23> z23(3);
   pearlrt::Float<52> z52(3);
   pearlrt::Fixed<31> y31(2);

   z23 = x23 + y;
   EXPECT_EQ(z23.x, 3);
   z23 = y + x23;
   EXPECT_EQ(z23.x, 3);
   z52 = x52 + y;
   EXPECT_EQ(z52.x, 3);
   z52 = y + x52;
   EXPECT_EQ(z52.x, 3);
   z52 = x23 - y31;
   z52 = x52 - y31;
}

/**
TOFLOAT
*/
TEST(Float, TOFLOAT) {
   pearlrt::Float<23> fl23;
   pearlrt::Float<52> fl52;

   pearlrt::Fixed<15> f15(15);
   pearlrt::Fixed<31> f31(31);
   pearlrt::Fixed<55> f55(55);

   fl23 = pearlrt::Float<23>(f15);
   EXPECT_EQ(fl23.x, f15.x);
   fl23 = pearlrt::Float<23>(f31);
   EXPECT_EQ(fl23.x, f31.x);
   fl23 = pearlrt::Float<23>(f55);
   EXPECT_EQ(fl23.x, f55.x);

   fl52 = pearlrt::Float<52>(f15);
   EXPECT_EQ(fl52.x, f15.x);
   fl52 = pearlrt::Float<52>(f31);
   EXPECT_EQ(fl52.x, f31.x);
   fl52 = pearlrt::Float<52>(f55);
   EXPECT_EQ(fl52.x, f55.x);

}
