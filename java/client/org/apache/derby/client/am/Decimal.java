/*

   Derby - Class org.apache.derby.client.am.Decimal

   Copyright (c) 2001, 2005 The Apache Software Foundation or its licensors, where applicable.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/
package org.apache.derby.client.am;

/**
 * Converters from fixed point decimal bytes to <code>java.math.BigDecimal</code>,
 * <code>double</code>, or <code>long</code>.
 */
public class Decimal
{
  /**
   * Packed Decimal representation
   */
  public final static int PACKED_DECIMAL = 0x30;

  //--------------------------private constants---------------------------------

  private static final int[][] tenRadixMagnitude = {
    { 0x3b9aca00 }, // 10^9
    { 0x0de0b6b3, 0xa7640000 }, // 10^18
    { 0x033b2e3c, 0x9fd0803c, 0xe8000000 }, // 10^27
  };

  //--------------------------constructors--------------------------------------

  // Hide the default constructor, this is a static class.
  private Decimal() {}

  //--------------------------private helper methods----------------------------

  /**
   * Convert a range of packed nybbles (up to 9 digits without overflow) to an int.
   * Note that for performance purpose, it does not do array-out-of-bound checking.
  */
  private static final int packedNybblesToInt (byte[] buffer,
                                         int offset,
                                         int startNybble,
                                         int numberOfNybbles)
  {
    int value = 0;

    int i = startNybble / 2;
    if ((startNybble % 2) != 0) {
      // process low nybble of the first byte if necessary.
      value += buffer[offset+i] & 0x0F;
      i++;
    }

    int endNybble = startNybble + numberOfNybbles -1;
    for (; i<(endNybble+1)/2; i++) {
      value = value*10 + ((buffer[offset+i] & 0xF0) >>> 4); // high nybble.
      value = value*10 +  (buffer[offset+i] & 0x0F);        // low nybble.
    }

    if ((endNybble % 2) == 0) {
      // process high nybble of the last byte if necessary.
      value = value*10 + ((buffer[offset+i] & 0xF0) >>> 4);
    }

    return value;
  }

  /**
   * Convert a range of packed nybbles (up to 18 digits without overflow) to a long.
   * Note that for performance purpose, it does not do array-out-of-bound checking.
  */
  private static final long packedNybblesToLong (byte[] buffer,
                                           int offset,
                                           int startNybble,
                                           int numberOfNybbles)
  {
    long value = 0;

    int i = startNybble / 2;
    if ((startNybble % 2) != 0) {
      // process low nybble of the first byte if necessary.
      value += buffer[offset+i] & 0x0F;
      i++;
    }

    int endNybble = startNybble + numberOfNybbles -1;
    for (; i<(endNybble+1)/2; i++) {
      value = value*10 + ((buffer[offset+i] & 0xF0) >>> 4); // high nybble.
      value = value*10 +  (buffer[offset+i] & 0x0F);        // low nybble.
    }

    if ((endNybble % 2) == 0) {
      // process high nybble of the last byte if necessary.
      value = value*10 + ((buffer[offset+i] & 0xF0) >>> 4);
    }

    return value;
  }

  /**
   * Compute the int array of magnitude from input value segments.
   */
  private static final int[] computeMagnitude(int[] input)
  {
      int length = input.length;
      int[] mag = new int[length];

      mag[length-1] = input[length-1];
      for (int i=0; i<length-1; i++) {
        int carry = 0;
        int j = tenRadixMagnitude[i].length-1;
        int k = length-1;
        for (; j>=0; j--, k--) {
          long product = (input[length-2-i] & 0xFFFFFFFFL) * (tenRadixMagnitude[i][j] & 0xFFFFFFFFL)
                       + (mag[k] & 0xFFFFFFFFL) // add previous value
                       + (carry & 0xFFFFFFFFL); // add carry
          carry  = (int) (product >>> 32);
          mag[k] = (int) (product & 0xFFFFFFFFL);
        }
        mag[k] = (int) carry;
      }
      return mag;
  }

  //--------------entry points for runtime representation-----------------------

  /**
   * Build a <code>java.math.BigDecimal</code> from a fixed point decimal byte representation.
   *
   * @exception IllegalArgumentException if the specified representation is not recognized.
   */
  public static final java.math.BigDecimal getBigDecimal (byte[] buffer,
                                                    int offset,
                                                    int precision,
                                                    int scale
                                                    ) throws java.io.UnsupportedEncodingException
  {
    // The byte-length of a packed decimal with precision <code>p</code> is always <code>p/2 + 1</code>
    int length = precision / 2 + 1;

    // check for sign.
    int signum;
    if ((buffer[offset+length-1] & 0x0F) == 0x0D)
      signum = -1;
    else
      signum =  1;

    if (precision <= 9) {
      // can be handled by int without overflow.
      int value = packedNybblesToInt(buffer, offset, 0, length*2-1);

      // convert value to a byte array of magnitude.
      byte[] magnitude = new byte[4];
      magnitude[0] = (byte)(value >>> 24);
      magnitude[1] = (byte)(value >>> 16);
      magnitude[2] = (byte)(value >>> 8);
      magnitude[3] = (byte)(value);

      return new java.math.BigDecimal (new java.math.BigInteger(signum, magnitude), scale);
    }
    else if (precision <= 18) {
      // can be handled by long without overflow.
      long value = packedNybblesToLong(buffer, offset, 0, length*2-1);

      // convert value to a byte array of magnitude.
      byte[] magnitude = new byte[8];
      magnitude[0] = (byte)(value >>> 56);
      magnitude[1] = (byte)(value >>> 48);
      magnitude[2] = (byte)(value >>> 40);
      magnitude[3] = (byte)(value >>> 32);
      magnitude[4] = (byte)(value >>> 24);
      magnitude[5] = (byte)(value >>> 16);
      magnitude[6] = (byte)(value >>>  8);
      magnitude[7] = (byte)(value);

      return new java.math.BigDecimal (new java.math.BigInteger(signum, magnitude), scale);
    }
    else if (precision <= 27) {
      // get the value of last 9 digits (5 bytes).
      int lo = packedNybblesToInt(buffer, offset, (length-5)*2, 9);
      // get the value of another 9 digits (5 bytes).
      int me = packedNybblesToInt(buffer, offset, (length-10)*2+1, 9);
      // get the value of the rest digits.
      int hi = packedNybblesToInt(buffer, offset, 0, (length-10)*2+1);

      // compute the int array of magnitude.
      int[] value = computeMagnitude(new int[] {hi, me, lo});

      // convert value to a byte array of magnitude.
      byte[] magnitude = new byte[12];
      magnitude[0]  = (byte)(value[0] >>> 24);
      magnitude[1]  = (byte)(value[0] >>> 16);
      magnitude[2]  = (byte)(value[0] >>> 8);
      magnitude[3]  = (byte)(value[0]);
      magnitude[4]  = (byte)(value[1] >>> 24);
      magnitude[5]  = (byte)(value[1] >>> 16);
      magnitude[6]  = (byte)(value[1] >>> 8);
      magnitude[7]  = (byte)(value[1]);
      magnitude[8]  = (byte)(value[2] >>> 24);
      magnitude[9]  = (byte)(value[2] >>> 16);
      magnitude[10] = (byte)(value[2] >>> 8);
      magnitude[11] = (byte)(value[2]);

      return new java.math.BigDecimal (new java.math.BigInteger(signum, magnitude), scale);
    }
    else if (precision <= 31) {
      // get the value of last 9 digits (5 bytes).
      int lo   = packedNybblesToInt(buffer, offset, (length-5)*2, 9);
      // get the value of another 9 digits (5 bytes).
      int meLo = packedNybblesToInt(buffer, offset, (length-10)*2+1, 9);
      // get the value of another 9 digits (5 bytes).
      int meHi = packedNybblesToInt(buffer, offset, (length-14)*2, 9);
      // get the value of the rest digits.
      int hi   = packedNybblesToInt(buffer, offset, 0, (length-14)*2);

      // compute the int array of magnitude.
      int[] value = computeMagnitude(new int[] {hi, meHi, meLo, lo});

      // convert value to a byte array of magnitude.
      byte[] magnitude = new byte[16];
      magnitude[0]  = (byte)(value[0] >>> 24);
      magnitude[1]  = (byte)(value[0] >>> 16);
      magnitude[2]  = (byte)(value[0] >>> 8);
      magnitude[3]  = (byte)(value[0]);
      magnitude[4]  = (byte)(value[1] >>> 24);
      magnitude[5]  = (byte)(value[1] >>> 16);
      magnitude[6]  = (byte)(value[1] >>> 8);
      magnitude[7]  = (byte)(value[1]);
      magnitude[8]  = (byte)(value[2] >>> 24);
      magnitude[9]  = (byte)(value[2] >>> 16);
      magnitude[10] = (byte)(value[2] >>> 8);
      magnitude[11] = (byte)(value[2]);
      magnitude[12] = (byte)(value[3] >>> 24);
      magnitude[13] = (byte)(value[3] >>> 16);
      magnitude[14] = (byte)(value[3] >>> 8);
      magnitude[15] = (byte)(value[3]);

      return new java.math.BigDecimal (new java.math.BigInteger(signum, magnitude), scale);
    }
    else {
      // throw an exception here if nibbles is greater than 31
      throw new java.lang.IllegalArgumentException("Decimal may only be up to 31 digits!");
    }
  }

  /**
   * Build a Java <code>double</code> from a fixed point decimal byte representation.
   *
   * @exception IllegalArgumentException if the specified representation is not recognized.
   */
  public static final double getDouble (byte[] buffer,
                                  int offset,
                                  int precision,
                                  int scale
                                  ) throws java.io.UnsupportedEncodingException
  {
    // The byte-length of a packed decimal with precision <code>p</code> is always <code>p/2 + 1</code>
    int length = precision / 2 + 1;

    // check for sign.
    int signum;
    if ((buffer[offset+length-1] & 0x0F) == 0x0D)
      signum = -1;
    else
      signum =  1;

    if (precision <= 9) {
      // can be handled by int without overflow.
      int value = packedNybblesToInt(buffer, offset, 0, length*2-1);

      return signum * value / Math.pow(10, scale);
    }
    else if (precision <= 18) {
      // can be handled by long without overflow.
      long value = packedNybblesToLong(buffer, offset, 0, length*2-1);

      return signum * value / Math.pow(10, scale);
    }
    else if (precision <= 27) {
      // get the value of last 9 digits (5 bytes).
      int lo = packedNybblesToInt(buffer, offset, (length-5)*2, 9);
      // get the value of another 9 digits (5 bytes).
      int me = packedNybblesToInt(buffer, offset, (length-10)*2+1, 9);
      // get the value of the rest digits.
      int hi = packedNybblesToInt(buffer, offset, 0, (length-10)*2+1);

      return signum * (lo / Math.pow(10, scale) +
                       me * Math.pow(10, 9-scale) +
                       hi * Math.pow(10, 18-scale));
    }
    else if (precision <= 31) {
      // get the value of last 9 digits (5 bytes).
      int lo   = packedNybblesToInt(buffer, offset, (length-5)*2, 9);
      // get the value of another 9 digits (5 bytes).
      int meLo = packedNybblesToInt(buffer, offset, (length-10)*2+1, 9);
      // get the value of another 9 digits (5 bytes).
      int meHi = packedNybblesToInt(buffer, offset, (length-14)*2, 9);
      // get the value of the rest digits.
      int hi   = packedNybblesToInt(buffer, offset, 0, (length-14)*2);

      return signum * (lo   / Math.pow(10, scale) +
                       meLo * Math.pow(10, 9-scale) +
                       meHi * Math.pow(10, 18-scale) +
                       hi   * Math.pow(10, 27-scale));
    }
    else {
      // throw an exception here if nibbles is greater than 31
      throw new java.lang.IllegalArgumentException ("Decimal may only be up to 31 digits!");
    }
  }

  /**
   * Build a Java <code>long</code> from a fixed point decimal byte representation.
   *
   * @exception IllegalArgumentException if the specified representation is not recognized.
   */
  public static final long getLong (byte[] buffer,
                              int offset,
                              int precision,
                              int scale
                              ) throws java.io.UnsupportedEncodingException
  {
    if (precision > 31) {
      // throw an exception here if nibbles is greater than 31
      throw new java.lang.IllegalArgumentException ("Decimal may only be up to 31 digits!");
    }

    // The byte-length of a packed decimal with precision <code>p</code> is always <code>p/2 + 1</code>
    int length = precision / 2 + 1;

    // check for sign.
    int signum;
    if ((buffer[offset+length-1] & 0x0F) == 0x0D)
      signum = -1;
    else
      signum =  1;

    // compute the integer part only.
    int leftOfDecimalPoint = length*2-1-scale;
    long integer = 0;
    if (leftOfDecimalPoint > 0) {
      int i = 0;
      for (; i<leftOfDecimalPoint/2; i++) {
        integer = integer*10 + signum*((buffer[offset+i] & 0xF0) >>> 4); // high nybble.
        integer = integer*10 + signum* (buffer[offset+i] & 0x0F);        // low nybble.
      }
      if ((leftOfDecimalPoint % 2) == 1) {
        // process high nybble of the last byte if necessary.
        integer = integer*10 + signum*((buffer[offset+i] & 0xF0) >>> 4);
      }
    }

    return integer;
  }

  //--------------entry points for runtime representation-----------------------

  /**
    Write a Java <code>java.math.BigDecimal</code> to packed decimal bytes.
  */
  public static final int bigDecimalToPackedDecimalBytes (byte[] buffer,
                                                    int offset,
                                                    java.math.BigDecimal b,
                                                    int declaredPrecision,
                                                    int declaredScale)
                                                    throws ConversionException
  {
    // packed decimal may only be up to 31 digits.
    if (declaredPrecision > 31)  
      throw new ConversionException ("Packed decimal may only be up to 31 digits!");

    // get absolute unscaled value of the BigDecimal as a String.
    String unscaledStr = b.unscaledValue().abs().toString();

    // get precision of the BigDecimal.
    int bigPrecision = unscaledStr.length();

    if (bigPrecision > 31)
      throw new ConversionException ("The numeric literal \"" +
                             b.toString() +
                             "\" is not valid because its value is out of range.",
                             "42820",
                             -405);

    int bigScale = b.scale();
    int bigWholeIntegerLength = bigPrecision - bigScale;
    if ( (bigWholeIntegerLength > 0) && (!unscaledStr.equals ("0")) ) {
      // if whole integer part exists, check if overflow.
      int declaredWholeIntegerLength = declaredPrecision - declaredScale;
      if (bigWholeIntegerLength > declaredWholeIntegerLength)
        throw new ConversionException ("Overflow occurred during numeric data type conversion of \"" +
                                       b.toString() +
                                       "\".",
                                       "22003",
                                       -413);
    }

    // convert the unscaled value to a packed decimal bytes.

    // get unicode '0' value.
    int zeroBase = '0';

    // start index in target packed decimal.
    int packedIndex = declaredPrecision-1;

    // start index in source big decimal.
    int bigIndex;

    if (bigScale >= declaredScale) {
      // If target scale is less than source scale,
      // discard excessive fraction.

      // set start index in source big decimal to ignore excessive fraction.
      bigIndex = bigPrecision-1-(bigScale-declaredScale);

      if (bigIndex < 0) {
        // all digits are discarded, so only process the sign nybble.
        buffer[offset+(packedIndex+1)/2] =
          (byte) ( (b.signum()>=0)?12:13 ); // sign nybble
      }
      else {
        // process the last nybble together with the sign nybble.
        buffer[offset+(packedIndex+1)/2] =
          (byte) ( ( (unscaledStr.charAt(bigIndex)-zeroBase) << 4 ) + // last nybble
                 ( (b.signum()>=0)?12:13 ) ); // sign nybble
      }
      packedIndex-=2;
      bigIndex-=2;
    }
    else {
      // If target scale is greater than source scale,
      // pad the fraction with zero.

      // set start index in source big decimal to pad fraction with zero.
      bigIndex = declaredScale-bigScale-1;

      // process the sign nybble.
      buffer[offset+(packedIndex+1)/2] =
        (byte) ( (b.signum()>=0)?12:13 ); // sign nybble

      for (packedIndex-=2, bigIndex-=2; bigIndex>=0; packedIndex-=2, bigIndex-=2)
        buffer[offset+(packedIndex+1)/2] = (byte) 0;

      if (bigIndex == -1) {
        buffer[offset+(packedIndex+1)/2] =
          (byte) ( (unscaledStr.charAt(bigPrecision-1)-zeroBase) << 4 ); // high nybble

        packedIndex-=2;
        bigIndex = bigPrecision-3;
      }
      else {
        bigIndex = bigPrecision-2;
      }
    }

    // process the rest.
    for (; bigIndex>=0; packedIndex-=2, bigIndex-=2) {
      buffer[offset+(packedIndex+1)/2] =
        (byte) ( ( (unscaledStr.charAt(bigIndex)-zeroBase) << 4 ) + // high nybble
               ( unscaledStr.charAt(bigIndex+1)-zeroBase ) ); // low nybble
    }

    // process the first nybble when there is one left.
    if (bigIndex == -1) {
      buffer[offset+(packedIndex+1)/2] =
        (byte) (unscaledStr.charAt(0) - zeroBase);

      packedIndex-=2;
    }

    // pad zero in front of the big decimal if necessary.
    for (; packedIndex>=-1; packedIndex-=2)
      buffer[offset+(packedIndex+1)/2] = (byte) 0;

    return declaredPrecision/2 + 1;
  }
}
