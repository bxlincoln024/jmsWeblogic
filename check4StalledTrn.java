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

public class check4StalledTrn
{

  public check4StalledTrn()
  {
     System.out.println( "hERE" );
  }

   public static void main(String args[]) throws Exception
  {
   // DEV
   //System.out.println(startBatches("jdbc:oracle:thin:@//172.16.4.17:1521/dmkrdev.databasesubnet.devvcn.oraclevcn.com","dmkr_asline","Asline12"));

   // PROD
   //System.out.println(startBatches("jdbc:oracle:thin:@//172.31.34.115:1521/DCMKRPDB.proddbsb.prodvcn.oraclevcn.com","dmkr_asline","Asline12"));

     System.exit( stalledTrnChk() );

  }

  public static int stalledTrnChk() throws Exception
  {
	
      String userName;
      String password;
      String urlString;
	
      String message;
	  
	   Connection conn;

      try {

            check4StalledTrn OBJ = new check4StalledTrn();
	        
		    Properties prop = new Properties();
           //load a properties file from class path, inside static method
            prop.load(new FileInputStream("conf.properties"));

           //get the property value and print it out
            urlString = prop.getProperty("urlString");
            userName = prop.getProperty("dbuser");
            password = prop.getProperty("dbpassword"); 
			
      
            conn  = DriverManager.getConnection( urlString, userName, password ); 
            System.out.println("Connected to database");


            // Get the END file directory locatt( endFileDirectory 
			String endFileDirectory = prop.getProperty("EndFileDir");
			
			Statement stmt = conn.createStatement();
			
            //  Query JOBS	   looking for 111                 
			String cmd = "SELECT JOB_ID, JOBSTATUS, JOBSTARTTIME FROM JOBS J WHERE JOBSTATUS = 111 ORDER BY JOB_ID DESC -- FETCH FIRST 1 ROWS ONLY" ;

            System.out.println( cmd );
            ResultSet res = stmt.executeQuery (cmd);
		    if(res.next())
			{
			  	  System.out.println( res.getString("JOBSTARTTIME") );
				  String ret = res.getString("JOBSTARTTIME");
                                
                                  res.next();
			  	  System.out.println( res.getString("JOBSTARTTIME") );
                                  System.out.println( res.getString("JOB_ID") );
				  res.close();
			}



			//  Query TRNS ... looking for 211, 221, 231, 311, 321, 331


        String sql = "SELECT CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS TIMESTAMP) AS utc_time FROM DUAL";

        // try-with-resources ensures your statement and result set are closed properly
        PreparedStatement pstmt = conn.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Retrieve the value directly as a LocalDateTime
                LocalDateTime utcTime = rs.getObject("utc_time", LocalDateTime.class);
                
                System.out.println( utcTime );
                
                // Now you can use the 'utcTime' variable in your program logic

            }

            System.out.println("FINISHED ... ");

            return( 0 );
      }
      catch(Exception ex) {

            System.out.println( ex.getMessage() );
            return( -1 );
      }
   }

}