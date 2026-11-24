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
import javax.naming.NamingException;import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


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

    private static final Logger logger = Logger.getLogger(check4StalledTrn .class.getName());

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE_BITS = 128;      // 128-bit works on ALL Java 1.8 updates without policy files
    private static final int IV_LENGTH_BYTES = 12;     // Standard 96-bit IV for GCM
    private static final int TAG_LENGTH_BITS = 128;    // 128-bit authentication tag
    
    String base64Key = "n8n3tvukHFJW15UiVzUpvQ==";  // keyToString(key);   

    // 'true' enables append mode so previous logs aren't overwritten
    FileHandler fileHandler;

    // Generates a random 128-bit AES key
    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(KEY_SIZE_BITS, new SecureRandom());
        return keyGen.generateKey();
    }

    // Helper: Convert SecretKey to Base64 String (for saving in config/env)
    public static String keyToString(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    // Helper: Restore SecretKey from a Base64 String
    public static SecretKey stringToKey(String base64Key) {
        byte[] decodedKey = Base64.getDecoder().decode(base64Key);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

    public static String encrypt(String plainPassword, SecretKey key) throws Exception {
        // 1. Generate a fresh random IV for each encryption
        byte[] iv = new byte[IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);

        // 2. Initialize Cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        // 3. Encrypt
        byte[] cipherText = cipher.doFinal(plainPassword.getBytes(StandardCharsets.UTF_8));

        // 4. Combine [IV + Ciphertext] so IV is available for decryption
        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
        byteBuffer.put(iv);
        byteBuffer.put(cipherText);

        // 5. Return Base64 string
        return Base64.getEncoder().encodeToString(byteBuffer.array());
    }

    public static String decrypt(String encryptedBase64, SecretKey key) throws Exception {
        // 1. Decode Base64
        byte[] decoded = Base64.getDecoder().decode(encryptedBase64);

        // 2. Split [IV] and [Ciphertext]
        ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
        byte[] iv = new byte[IV_LENGTH_BYTES];
        byteBuffer.get(iv);
        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);

        // 3. Initialize Cipher for decryption
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        // 4. Decrypt
        byte[] plainBytes = cipher.doFinal(cipherText);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }
  
  public check4StalledTrn()
  {

      String userName;
      String password;
	
      try {
	   
            LocalDate today = LocalDate.now();

            // 'true' enables append mode so previous logs aren't overwritten
            fileHandler = new FileHandler(".\\logs\\Check4StalledTrn_" + today + ".log", true);
            
            // Format logs as readable plain text instead of the default XML
            fileHandler.setFormatter(new SimpleFormatter());
            
            logger.addHandler(fileHandler);
    			
		    Properties prop = new Properties();
           //load a properties file from class path, inside static method
            prop.load(new FileInputStream("conf.properties"));

           //get the property value and print it out
            dbConnString = prop.getProperty("dbConnString");
            userName = prop.getProperty("dbuser");
            password = prop.getProperty("dbpassword"); 

           // Decrypt
           String decrypted = decrypt(password, stringToKey(base64Key));
//           System.out.println( "PWD: " + decrypted );

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
			
 		    // Prevent from executing
		    if ( prop.getProperty("pause", "no" ).equals("no") == false )
		    {
				System.out.println("Process will not execute.  Pause propery not equal to 'no'." );  	
				return;
     	    }

            if ( matchedFiles.length > 0 )	
            {				
              connDB  = DriverManager.getConnection( dbConnString, userName, decrypted ); 
              System.out.println("Connected to database\n");  			
	        }
			else
			{
		      System.out.println("Not Connected to database");  			
			}
		  }
		  catch( Exception ex )
		  {
                     ex.printStackTrace();
		  		  
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
                   ex.printStackTrace();
		  		  
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
		
	        swps = connDB.prepareStatement("UPDATE TRNS SET TRNSTATUS = 221, TRNNAPOLXML=null, TRNNAPOLBLOB=null, TRNNAPOLSIZE=0, TRNCUSDEC001 = NVL( TRNCUSDEC001, 0 ) + 1 WHERE TRN_ID = ?");
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
                      System.out.println( cmd );
		      Statement stmt = connDB.createStatement();
                      ResultSet res = stmt.executeQuery (cmd);
                     res = stmt.executeQuery (cmd);
	          PreparedStatement swps;

                     if ( !res.next() )
                     {
                         System.out.println( "Nothing Retrieved from the JOBS \n" );  
                         return;
                     }                    


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
		    String cmd = "SELECT TRN_ID, TRNSTARTTIME FROM TRNS T WHERE TRNSTATUS IN ( 221,231,311,321,331 ) AND EXTRACT( DAY FROM ( ( SELECT SYS_EXTRACT_UTC(SYSTIMESTAMP) from dual ) - TRNSTARTTIME ) ) = 0 AND EXTRACT( HOUR FROM ( ( SELECT SYS_EXTRACT_UTC(SYSTIMESTAMP) from dual ) - TRNMODIFYTIME ) ) * 60 + EXTRACT( MINUTE FROM ( ( SELECT SYS_EXTRACT_UTC(SYSTIMESTAMP) from dual ) - TRNMODIFYTIME ) )  >" + iThreshold;;
            System.out.println( cmd );
    		Statement stmt = connDB.createStatement();
            ResultSet res = stmt.executeQuery (cmd);
	        PreparedStatement swps;

              if ( !res.next() )
                System.out.println( "Nothing Retrieved from the TRNS (211 check)\n" );

		 	while(res.next())
		 	{
         	// Condition meet for this to be manualy submitted
    			swps = connDB.prepareStatement("UPDATE TRNS SET TRNNAPOLXML=null, TRNNAPOLBLOB=null, TRNNAPOLSIZE=0 WHERE TRN_ID = ?");
            	int updatedTrns = swps.executeUpdate();		
	 	 
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


    public void checkTrnStatus( )
    { 
	   // Threshold check for TRNS
         try
         {		 
		    String cmd = "SELECT TRN_ID, TRNSTARTTIME FROM TRNS T WHERE TRNSTATUS IN ( 221,231,311,321,331 ) AND EXTRACT( DAY FROM ( ( SELECT SYS_EXTRACT_UTC(SYSTIMESTAMP) from dual ) - TRNSTARTTIME ) ) = 0 AND EXTRACT( HOUR FROM ( ( SELECT SYS_EXTRACT_UTC(SYSTIMESTAMP) from dual ) - TRNMODIFYTIME ) ) * 60 + EXTRACT( MINUTE FROM ( ( SELECT SYS_EXTRACT_UTC(SYSTIMESTAMP) from dual ) - TRNMODIFYTIME ) ) > " + iThreshold;;
            System.out.println( cmd );
		    Statement stmt = connDB.createStatement();
            ResultSet res = stmt.executeQuery (cmd);
	        PreparedStatement swps;

              if ( !res.next() )
                System.out.println( "Nothing Retrieved from the TRNS. \n " );
		 
		    while(res.next())
		    {
         // Condition meet for this to be manualy submitted
		    resetUnitOfWork( res.getInt("TRN_ID") );
	 	 
               String messageDMTicket = 
"<?xml version='1.0' encoding='UTF-8' standalone='yes'?><TransactionTicket xmlns='oracle/documaker/schema/tables/trns'><TRN_ID>" + res.getInt("TRN_ID")  + "</TRN_ID></TransactionT(icket>";

			   WriteJMS( messageDMTicket,
                         assemblerQ,
				   	     res.getInt("TRN_ID"));
         
                         // Write log entries 
                logger.info("TRN_ID - " + res.getInt("TRN_ID") + " was resubmitted for reprocessing as the threshold wait time was met." );
 						 
            }
		
	     	if ( res != null )
     		  res.close();	
		 }
		 catch(Exception e){
							e.printStackTrace();
                            	logger.severe( e.getMessage() ); 
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
//		      obj.checkJobStatus();
//		      obj.checkTrnStatus211();
		      obj.checkTrnStatus();		  
            }
		}
        catch(Exception ex) {
              ex.printStackTrace();
		      return;
        }
  }
}
