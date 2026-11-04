//package custom.eom.documaker;

import java.time.LocalDateTime;
import java.io.*;
import java.util.Properties;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Types;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import oracle.jdbc.OracleResultSet;
import java.sql.Timestamp;
import java.util.Date;

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

public class check4StalledTrn implements AutoCloseable
{
  private static final String JNDI_FACTORY = "weblogic.jndi.WLInitialContextFactory";


  Connection connDB;
  String url;
  String urlString;
  String dbConnString;
  String queFactory;   //
  String identifierQ;
  String assemblerQ;
  int    iThreshold;

  
  public check4StalledTrn()
  {

      String userName;
      String password;
	
      try {
	        
			System.out.println( "1" );
		    Properties prop = new Properties();
           //load a properties file from class path, inside static method
            prop.load(new FileInputStream("conf.properties"));

           //get the property value and print it out
            dbConnString = prop.getProperty("dbConnString");
            userName = prop.getProperty("dbuser");
            password = prop.getProperty("dbpassword"); 

            urlString = prop.getProperty("urlJMSString");
	        queFactory = prop.getProperty("queueFactory", "jms.al3.qcf" );   //
            identifierQ = prop.getProperty("queueIdentifier", "jms.al3.identifierreq");
	        assemblerQ = prop.getProperty("queueAssembler", "jms.al3.assemblerreq");
			
		   // Get Threshold check valu in minutes ... Default is 1 hour
            iThreshold = Integer.parseInt( prop.getProperty("threshold", "1" ) );
		
	       // Check location for the END file(s) existent to execute checking for stuck inflight files	
            File directory = new File( prop.getProperty("endFileLoc") );
            String extension = ".end";

            // Create a custom filter to screen out files on the fly
            FilenameFilter filter = new FilenameFilter() 
		    {
               @Override
               public boolean accept(File dir, String name) {
                   // Return true if the file ends with the target extension (case-insensitive)
                   return name.toLowerCase().endsWith(extension.toLowerCase());
               }
            };

            // Fetch the filtered list of file strings
            String[] matchedFiles = directory.list(filter);

            connDB = null;
			System.out.println( dbConnString  );
				
            if ( matchedFiles.length > 0 )	
            {				
              connDB  = DriverManager.getConnection( dbConnString, userName, password ); 
              System.out.println("Connected to database");  			
	        }
			else
			{
		      System.out.println("Not Connected to database");  			
			}
		  }
		  catch( Exception ex )
		  {
		  		  
          }		  		  		 
    }

    // The close() method acts as your deterministic "destructor"
    @Override
    public void close() throws IOException 
	{
		try { 
		 if( connDB != null )
		    connDB.close();
		}
		catch( Exception ex )
		{
		  		  
        }			
    }	

    public void resetUnitOfWork( int iTrnId )
    {
	  try {	
            // Clear the record on the BCHS
    	    PreparedStatement swps = connDB.prepareStatement("DELETE FROM BCHS WHERE BCH_ID IN (SELECT BCH_ID FROM BCHS_RCPS WHERE TRN_ID = ?)");
	        swps.setInt(1,iTrnId);  
	        int updatedTrns = swps.executeUpdate();

	        swps = connDB.prepareStatement("DELETE FROM RCPS WHERE RCP_ID IN (SELECT RCP_ID FROM BCHS_RCPS WHERE TRN_ID = ?)");
	        swps.setInt(1,iTrnId);
	        updatedTrns = swps.executeUpdate();
									
	        swps = connDB.prepareStatement("DELETE FROM BCHS_RCPS WHERE TRN_ID = ?");
	        swps.setInt(1,iTrnId);
	        updatedTrns = swps.executeUpdate();
		
	        swps = connDB.prepareStatement("UPDATE TRNS SET TRNNAPOLXML=null, TRNNAPOLBLOB=null, TRNNAPOLSIZE=0 WHERE TRN_ID = ?");
	        swps.setInt(1,iTrnId);
            updatedTrns = swps.executeUpdate();
		}		
		catch(Exception e){
							e.printStackTrace();
		}
    }

    public void checkJobStatus( )
    { 
	   // Threshold check for JOBS 
	   // Verify JOBS are being processed
       // Update the JOBSTARTTIME IF JOB must he restarted   
       // Query JOBS - looking for 111
       try { 	   
		      String cmd = "SELECT JOB_ID, JOBSTARTTIME FROM JOBS J WHERE JOBSTATUS = 111 AND EXTRACT(HOUR FROM ( ( SELECT SYS_EXTRACT_UTC(SYSTIMESTAMP) from dual ) - JOBSTARTTIME ) ) > " + iThreshold ;
//              System.out.println( cmd );
		      Statement stmt = connDB.createStatement();
              ResultSet res = stmt.executeQuery (cmd);
	          PreparedStatement swps;
		 
     		  while(res.next())
		      {
              // Condition meet for this to be manualy submitted
		         int iJobId = res.getInt("JOB_ID"); 
		 
           		 swps = connDB.prepareStatement("UPDATE JOBS SET JOBSTARTTIME = ( SELECT SYS_EXTRACT_UTC(SYSTIMESTAMP) from dual ) WHERE JOB_ID = ?" );
	             swps.setInt( 1,iJobId );
                 int updatedTrns = swps.executeUpdate();		
	 	 
                 String messageDMTicket = 
"<?xml version='1.0' encoding='UTF-8' standalone='yes'?><JobTicket xmlns='oracle/documaker/schema/tables/jobs'><JOB_ID>" + res.getString("JOB_ID") + "</JOB_ID></JobTicket>";	 

				 WriteJMS( messageDMTicket,
  	    	               identifierQ,
				   		   iJobId );	
						   
	        }
		
			if ( res != null )
			  res.close();
		}		
		catch(Exception e){
							e.printStackTrace();
		}
	  
    }
	
    public void checkTrnStatus211( )
    { 
	   // Threshold check for TRNS 
		try 
		{
		    String cmd = "SELECT TRN_ID, TRNSTARTTIME FROM TRNS T WHERE TRNSTATUS = 211 AND EXTRACT(HOUR FROM ( ( SELECT SYS_EXTRACT_UTC(SYSTIMESTAMP) from dual ) - TRNSTARTTIME ) )  > " + iThreshold;
            System.out.println( cmd );
    		Statement stmt = connDB.createStatement();
            ResultSet res = stmt.executeQuery (cmd);
	        PreparedStatement swps;

		 	while(res.next())
		 	{
         	// Condition meet for this to be manualy submitted
    			swps = connDB.prepareStatement("UPDATE TRNS SET TRNNAPOLXML=null, TRNNAPOLBLOB=null, TRNNAPOLSIZE=0 WHERE TRN_ID = ?");
//            	int updatedTrns = swps.executeUpdate();		
	 	 
            	String messageDMTicket = 
"<?xml version='1.0' encoding='UTF-8' standalone='yes'?><TransactionTicket xmlns='oracle/documaker/schema/tables/trns'><TRN_ID>" + res.getInt("TRN_ID")  + "</TRN_ID></TransactionT(icket>";

/*
			    WriteJMS( messageDMTicket,
                          identifierQ,
					      res.getInt("TRN_ID") );
 */          }
		
		    if ( res != null )
		       res.close();	

		}		
		catch(Exception e){
							e.printStackTrace();
		}	  
    }


    public void checkTrnStatus( )
    { 
	   // Threshold check for TRNS
         try
         {		 
		    String cmd = "SELECT TRN_ID, TRNSTARTTIME FROM TRNS T WHERE TRNSTATUS IN ( 221,231,311,321,331) AND EXTRACT(HOUR FROM ( ( SELECT SYS_EXTRACT_UTC(SYSTIMESTAMP) from dual ) - TRNSTARTTIME ) )  > " + iThreshold;
            System.out.println( cmd );
		    Statement stmt = connDB.createStatement();
            ResultSet res = stmt.executeQuery (cmd);
	        PreparedStatement swps;
		 
		    while(res.next())
		    {
         // Condition meet for this to be manualy submitted
		    resetUnitOfWork( res.getInt("TRN_ID") );
	 	 
               String messageDMTicket = 
"<?xml version='1.0' encoding='UTF-8' standalone='yes'?><TransactionTicket xmlns='oracle/documaker/schema/tables/trns'><TRN_ID>" + res.getInt("TRN_ID")  + "</TRN_ID></TransactionT(icket>";

			   WriteJMS( messageDMTicket,
                         assemblerQ,
				   	     res.getInt("TRN_ID"));
 						 
            }
		
	     	if ( res != null )
     		  res.close();	
		 }
		 catch(Exception e){
							e.printStackTrace();		
		 }		  
    }

	
	public void WriteJMS( String messageDMTicket,
	                      String queueLookup,
                          int iID  )
	{
	    try {
             InitialContext namingContext = getInitialContext( urlString );
   		     QueueConnectionFactory queueConnectionFactory = (QueueConnectionFactory) namingContext.lookup( queFactory );
		     Queue queue = (Queue) namingContext.lookup( queueLookup );
		     QueueConnection  connQC = queueConnectionFactory.createQueueConnection();
		     QueueSession session = connQC.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
//      	 String messageText = "<?xml version='1.0' encoding='UTF-8' standalone='yes'?><TransactionTicket xmlns='oracle/documaker/schema/tables/trns'><TRN_ID>" + iID + "</TRN_ID></TransactionTicket>";
			 BytesMessage message = session.createBytesMessage();

    	     message.writeBytes( messageDMTicket.getBytes("UTF-8"));		
			 connQC.start();						
			 session.createSender(queue).send(message);
		     connQC.close();					
			 
			}
		    catch(JMSException e){
								e.printStackTrace();
			}	
			catch(Exception e){
							e.printStackTrace();
			}	
	}
	
	private static InitialContext getInitialContext(String url) throws NamingException
	{
		Hashtable<String, String> env = new Hashtable<>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, JNDI_FACTORY);
		env.put(Context.PROVIDER_URL, url);
		return new InitialContext(env);
	}
	
	
  public static void main(String args[]) throws Exception
  {
    	
        try ( check4StalledTrn obj = new check4StalledTrn() ) 
        {
			if ( obj.connDB != null )
            {
              System.out.println( " Working " ); 				
		      obj.checkJobStatus();
		      obj.checkTrnStatus211();
		      obj.checkTrnStatus();		  
            }
		}
        catch(Exception ex) {
              System.out.println( ex.getMessage() );
		      return;
        }
  }
}