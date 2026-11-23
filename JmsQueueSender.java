import javax.jms.*;  // for older JMS 1.1/2.0 versions
import javax.naming.InitialContext;

public class JmsQueueSender {
    public static void main(String[] args) {
        try {
            // 1. Look up the ConnectionFactory and Queue via JNDI
            InitialContext ctx = new InitialContext();
            ConnectionFactory factory = (ConnectionFactory) ctx.lookup("jms/ConnectionFactory");
            Queue queue = (Queue) ctx.lookup("jms/MyQueue");

            // 2. Create connection, session, and producer (try-with-resources auto-closes them)
            try (Connection connection = factory.createConnection();
                 Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                 MessageProducer producer = session.createProducer(queue)) {

                // Optional: Set high priority (9 is highest) to jump ahead in supported brokers
                producer.setPriority(9);

                // 3. Create and send the message
                TextMessage message = session.createTextMessage("Hello, Queue!");
                producer.send(message);
                
                System.out.println("Message sent successfully.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
