package com.Lomikel.Utils;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

/** Offline regression tests: the transport is replaced before any postMail call. */
public class NotifierMailTest {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void htmlStringRecipients() throws Exception {
    final Message[] captured = {null};
    NotifierMail.sender = message -> captured[0] = message;
    try {
      NotifierMail.setManager("manager@example.invalid");
      NotifierMail.postHTMLMail("first@example.invalid,second@example.invalid", "HTML", "<b>hello</b>");
      check(captured[0] != null, "no message captured");
      ((MimeMessage) captured[0]).saveChanges(); // Transport.send normally finalizes MIME headers.
      check(captured[0].getContentType().toLowerCase().startsWith("text/html"),
            "string recipient overload lost HTML MIME: " + captured[0].getContentType());
      check(captured[0].getRecipients(Message.RecipientType.TO).length == 2, "recipient list changed");
      check("<b>hello</b>".equals(captured[0].getContent()), "HTML body changed");
    } finally {
      NotifierMail.sender = javax.mail.Transport::send;
      NotifierMail.setManager(null);
    }
  }

  private static void isolatedBoundedSessions() throws Exception {
    final Message[] captured = {null};
    NotifierMail.sender = message -> captured[0] = message;
    try {
      NotifierMail.postMail(new String[]{"to@example.invalid"}, "one", "body",
                            "from@example.invalid", "smtp-one.invalid", "text/plain");
      Session first = ((MimeMessage) captured[0]).getSession();
      check("smtp-one.invalid".equals(first.getProperty("mail.smtp.host")), "first SMTP host changed");
      check("10000".equals(first.getProperty("mail.smtp.connectiontimeout")), "no connect timeout");
      check("10000".equals(first.getProperty("mail.smtp.timeout")), "no read timeout");
      check("10000".equals(first.getProperty("mail.smtp.writetimeout")), "no write timeout");
      NotifierMail.postMail(new String[]{"to@example.invalid"}, "two", "body",
                            "from@example.invalid", "smtp-two.invalid", "text/plain");
      Session second = ((MimeMessage) captured[0]).getSession();
      check(first != second, "sessions shared across calls");
      check("smtp-two.invalid".equals(second.getProperty("mail.smtp.host")), "second SMTP host changed");
      check("smtp-one.invalid".equals(first.getProperty("mail.smtp.host")), "first session mutated");
    } finally {
      NotifierMail.sender = javax.mail.Transport::send;
    }
  }

  private static void emptySmtpRemainsNoOp() throws Exception {
    NotifierMail.sender = message -> { throw new AssertionError("empty SMTP attempted delivery"); };
    try {
      for (String smtp : new String[]{null, ""}) {
        NotifierMail.postMail(new String[]{"to@example.invalid"}, "subject", "body",
                              "from@example.invalid", smtp, "text/plain");
      }
    } finally {
      NotifierMail.sender = javax.mail.Transport::send;
    }
  }

  public static void main(String[] args) throws Exception {
    htmlStringRecipients();
    isolatedBoundedSessions();
    emptySmtpRemainsNoOp();
    System.out.println("NotifierMailTest passed");
  }
}
