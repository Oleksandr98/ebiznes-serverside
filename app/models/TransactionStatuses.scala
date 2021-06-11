package models

object TransactionStatuses extends Enumeration {
  type Status = Value

  def complete = Value("C")
  def reversed = Value("R")
}

