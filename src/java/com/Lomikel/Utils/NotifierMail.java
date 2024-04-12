package com.Lomikel.Utils;

// Java Mail
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.InternetAddress;

// Java
import java.util.Properties;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>NotifierMail</code> sends a notifying E-mail.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: refactor
public class NotifierMail {

  /** Set special <em>manager</em> email.
    * @param manager The <em>manager</em> email. */
  public static void setManager(String manager) {
    _manager = manager;
    }

  /** Selftest. */
  public static void main(String[] args) throws Exception {
    new NotifierMail().postMail("Notifier test", "testing");
    }

  /** Sent E-mail using default values.
    * @param subject    The subject.
    * @param message    The message body.
    * @throws MessaginException if mail can't be sent. */
  public static void postMail(String subject, 
                              String message) throws MessagingException {
    postMail(manager(), subject, message, manager(), "cernmx.cern.ch", "text/plain");
    }

  /** Sent E-mail using default values.
    * @param recipients The array of recipients addresses.
    * @param subject    The subject.
    * @param message    The message body.
    * @throws MessaginException if mail can't be sent. */
  public static void postMail(String[] recipients,
                              String   subject, 
                              String   message) throws MessagingException {
    postMail(recipients, subject, message, manager(), "cernmx.cern.ch", "text/plain");
    }

  /** Sent E-mail using default values.
    * @param recipients The recipients addresses, separated by ,.
    * @param subject    The subject.
    * @param message    The message body.
    * @throws MessaginException if mail can't be sent. */
  public static void postMail(String recipients,
                              String subject, 
                              String message) throws MessagingException {
    postMail(recipients.split(","), subject, message);
    }
 
  /** Sent HTML E-mail using default values.
    * @param recipients The array of recipients addresses.
    * @param subject    The subject.
    * @param message    The message body.
    * @throws MessaginException if mail can't be sent. */
  public static void postHTMLMail(String[] recipients,
                                  String   subject, 
                                  String   message) throws MessagingException {
    postMail(recipients, subject, message, manager(), "cernmx.cern.ch", "text/html");
    }
 
  /** Sent HTML E-mail using default values.
    * @param recipients The recipients addresses, separated by ,.
    * @param subject    The subject.
    * @param message    The message body.
    * @throws MessaginException if mail can't be sent. */
  public static void postHTMLMail(String recipients,
                                  String subject, 
                                  String message) throws MessagingException {
    postMail(recipients.split(","), subject, message);
    }

  /** Sent E-mail.
    * @param recipients The recipients addresses, separated by ,.
    * @param subject    The subject.
    * @param message    The message body.
    * @param from       The sender address. If several addresses separated by ,, only the first one is used.
    * @param smtp       The SMTP server hostname. 
    * @throws MessaginException if mail can't be sent. */
  public static void postMail(String recipients, 
                              String subject, 
                              String message, 
                              String from,
                              String smtp,
                              String format) throws MessagingException {
    postMail(recipients.split(","), subject, message, from, smtp, format);
    }

  /** Sent E-mail.
    * @param recipients The array of recipients addresses.
    * @param subject    The subject.
    * @param message    The message body.
    * @param from       The sender address. If several addresses separated by ,, only the first one is used.
    * @param smtp       The SMTP server hostname. 
    * @throws MessaginException if mail can't be sent. */
  public static void postMail(String[] recipients, 
                              String   subject, 
                              String   message, 
                              String   from,
                              String   smtp,
                              String   format) throws MessagingException {
    boolean debug = false;
    if (smtp == null || smtp.equals("")) return;

    // Set the host smtp address
    Properties props = new Properties();
    props.put("mail.smtp.host", smtp);

    // Create some properties and get the default Session
    Session session = Session.getDefaultInstance(props, null);
    session.setDebug(debug);

    // Create a message
    Message msg = new MimeMessage(session);

    // Set the from and to address
    if (from.contains(",")) {
      from = from.split(",")[0];
      }
    InternetAddress addressFrom = new InternetAddress(from);
    msg.setFrom(addressFrom);
    InternetAddress[] addressTo = new InternetAddress[recipients.length]; 
    for (int i = 0; i < recipients.length; i++) {
      addressTo[i] = new InternetAddress(recipients[i]);
      }
    msg.setRecipients(Message.RecipientType.TO, addressTo);

    // Set custom headers
    msg.addHeader("X-Notifier", "Lomikel");

    // Sett the Subject and Content Type
    msg.setSubject(subject);
    msg.setContent(message, format);

    // Send the mail
    Transport.send(msg);
    
    }
    
  /** Give the current <em>manager</em> email.
    * @return The current <em>manager</em> email. */
  private static String manager() {
    return _manager == null ? Info.manager() : _manager;
    }
    
  private static String _manager;

  /** Logging . */
  private static Logger log = LogManager.getLogger(NotifierMail.class);

  }
