package org.omg.CORBA;

/**
* org/omg/CORBA/SequenceDefHolder.java .
* IGNORE Generated by the IDL-to-Java compiler (portable), version "3.2"
* from idlj/src/main/java/com/sun/tools/corba/ee/idl/ir.idl
* IGNORE Sunday, January 21, 2018 1:54:23 PM EST
*/

public final class SequenceDefHolder implements org.omg.CORBA.portable.Streamable
{
  public org.omg.CORBA.SequenceDef value = null;

  public SequenceDefHolder ()
  {
  }

  public SequenceDefHolder (org.omg.CORBA.SequenceDef initialValue)
  {
    value = initialValue;
  }

  public void _read (org.omg.CORBA.portable.InputStream i)
  {
    value = org.omg.CORBA.SequenceDefHelper.read (i);
  }

  public void _write (org.omg.CORBA.portable.OutputStream o)
  {
    org.omg.CORBA.SequenceDefHelper.write (o, value);
  }

  public org.omg.CORBA.TypeCode _type ()
  {
    return org.omg.CORBA.SequenceDefHelper.type ();
  }

}
