package models

object CustomerStatuses extends Enumeration {
  type Status = Value

  def active = Value("A")
  def closed = Value("C")
  def blocked = Value("B")

}
