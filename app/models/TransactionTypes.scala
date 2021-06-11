package models

object TransactionTypes extends Enumeration {
  type Status = Value

  def sale = Value("S")
}
