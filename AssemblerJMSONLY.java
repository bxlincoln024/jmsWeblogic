import java.sql.*;
import org.apache.commons.cli.*;

import java.util.Hashtable;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSession;
import javax.jms.Session;

import javax.jms.BytesMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

class AssemblerJMSONLY {
	
	private static final String SQL_VALIDATE_TRNS = "select count(*) from TRNS where TRN_ID = ?";
	private static final String SQL_UPDATE_TRNS = "update TRNS set TRNSTATUS = ? WHERE TRN_ID = ?";	
	private static final String JNDI_FACTORY = "weblogic.jndi.WLInitialContextFactory";

	private static InitialContext getInitialContext(String url) throws NamingException
	{
		Hashtable<String, String> env = new Hashtable<>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, JNDI_FACTORY);
		env.put(Context.PROVIDER_URL, url);
		return new InitialContext(env);
	}

	public static void main(String[] args)
	{
							try
							{
System.out.println( "1");
        							InitialContext namingContext = getInitialContext("t3://10.40.3.14:11001");

System.out.println( "1");

								QueueConnectionFactory queueConnectionFactory = (QueueConnectionFactory) namingContext.lookup("jms.al1.qcf");
System.out.println( "1");

								Queue queue = (Queue) namingContext.lookup("jms.al1.assemblerreq"); 

System.out.println( "1");
								QueueConnection  conn = queueConnectionFactory.createQueueConnection();						
System.out.println( "1");
								QueueSession session = conn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

								String messageText = "<?xml version='1.0' encoding='UTF-8' standalone='yes'?><TransactionTicket xmlns='oracle/documaker/schema/tables/trns'><TRN_ID>00000</TRN_ID></TransactionTicket>";								

								BytesMessage message = session.createBytesMessage();

System.out.println( messageText ) ;
								message.writeBytes(messageText.getBytes("UTF-8"));
									conn.start();						
									session.createSender(queue).send(message);
									conn.close();					
									System.out.println("Message posted to for TRNID ");
							}
							catch(JMSException e){
								e.printStackTrace();
							}	
							catch(Exception e){
								e.printStackTrace();
							}	

	}
}