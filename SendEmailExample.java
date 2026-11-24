import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class SendEmailExample {

    public static void main(String[] args) {
        // 1. Sender & Recipient details
        final String username = "your-email@example.com";
        final String password = "your-app-password"; // Use an App Password if 2FA is enabled
        String recipient = "recipient@example.com";

        // 2. Configure SMTP server properties
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.example.com"); // e.g., smtp.gmail.com
        props.put("mail.smtp.port", "587");

        // 3. Create a Session with an Authenticator
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            // 4. Build the email message
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(
                Message.RecipientType.TO,
                InternetAddress.parse(recipient)
            );
            message.setSubject("JavaMail API Test");
            message.setText("Hello! This email was sent using JavaMail 1.6.2.");

            // 5. Send the message
            Transport.send(message);
            System.out.println("Email sent successfully!");

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}