import javax.jms.*;  // for older JMS 1.1/2.0 versions
import javax.jms.BytesMessage;
import javax.naming.Context;
import javax.naming.InitialContext;

import javax.naming.NamingException;import javax.crypto.Cipher;

import org.apache.commons.cli.*;

import java.util.Hashtable;



public class JmsQueueSender {


  private static final String JNDI_FACTORY = "weblogic.jndi.WLInitialContextFactory";


	private static InitialContext getInitialContext(String url) throws NamingException
	{
		Hashtable<String, String> env = new Hashtable<>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, JNDI_FACTORY);
		env.put(Context.PROVIDER_URL, url);
		return new InitialContext(env);
	}

    public static void main(String[] args) {
        try {
            // 1. Look up the ConnectionFactory and Queue via JNDI
            InitialContext ctx = getInitialContext( "t3://wstgidocweb2:11001" );
            ConnectionFactory factory = (ConnectionFactory) ctx.lookup("jms.al3.qcf");
            Queue queue = (Queue) ctx.lookup("jms.al3.assemblerreq");

            // 2. Create connection, session, and producer (try-with-resources auto-closes them)
            try (Connection connection = factory.createConnection();
                 Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                 MessageProducer producer = session.createProducer(queue)) {

                // Optional: Set high priority (9 is highest) to jump ahead in supported brokers
                producer.setPriority(9);

                // 3. Create and send the message  
                  TextMessage message = session.createTextMessage("<?xml version='1.0' encoding='UTF-8' standalone='yes'?><TransactionTicket xmlns='oracle/documaker/schema/tables/trns'><TRN_ID>00000</TRN_ID></TransactionTicket>");
                  producer.send(message);
                
                System.out.println("Message sent successfully.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
