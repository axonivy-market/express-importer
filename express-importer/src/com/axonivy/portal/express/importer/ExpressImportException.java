package com.axonivy.portal.express.importer;
public class ExpressImportException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ExpressImportException(String message, Throwable cause) {
    super(message, cause);
  }

  public ExpressImportException(String message) {
    super(message);
  }

  public ExpressImportException(Throwable cause) {
    super(cause);
  }
}
