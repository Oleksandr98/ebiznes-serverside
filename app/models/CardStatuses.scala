package models

object CardStatuses extends Enumeration {
  type Status = Value

  def active = Value("A")
  def closed = Value("C")
  def blocked = Value("B")
  def newStatus = Value("N")
}
