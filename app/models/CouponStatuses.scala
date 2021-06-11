package models

object CouponStatuses extends Enumeration {
  type Status = Value

  def used = Value("U")
  def cancelled = Value("C")
  def newStatus = Value("N")
}

