package org.smallpearl.compiler.Exception;


/**
 * this exception signals that the repetition factor is not static
 * 
 * This feature is used in CheckIoStatement
 * 
 * @author mueller
 *
 */
public class FactorIsNotConstantException extends RuntimeException {
  public FactorIsNotConstantException() {}

}
