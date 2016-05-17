
************************************

SYSC3303 Iteration 2
Team #10

************************************

Team Member:
- Liam Brown		100884757		
- Mamush Dejene	 	100910189			
- Zening Jiang		100935033

Iteration 1
Raymond:
 - Transfer Created
Liam:
 - UCM Diagram, UML Diagram
Mamush:
 - Server, Client, Threaded workers
Zening:
 - User Interface, Test file Created
Stephan:
 - UCM Diagram, UML Diagram

Iteration 2
Zening:
 - TFTPClient, TFTPServer, RequestThread, timing Diagrams
Liam:
 - TFTPSim, UCM diagrams
Mamush
 - timeout exception, UML Diagrams
 

 
***********Files**************
Source Files:
-TFTPClient.java
-TFTPServer.java
-TFTPSim.java
-RequestThread.java

Dummy Test File:
-300Bytes
-1024Bytes
-512Bytes
-600Bytes

*************Instruction***************

Normal Mode:
1. Run TFTPServer.java
2. Run TFTPClient.java
3. Select normal mode in console of TFTPClient.java
4. Set up the data infomation in console of TFTPClient.java

Test Mode:
1. Run TFTPServer.java
2. Run TFTPSim.java
3. Set up the test information in console of TFTPSim.java
4. Run TFTPClient.java
5. Select normal mode in console of TFTPClient.java
6. Set up the data information in console of TFTPClient.java

Use Normal mode to transfer files.
Use Test mode to test the network exceptions.


*********NOTES******************
The error simulator delays, duplicates or loses packets but retransmitting the packets on timeout has not been implemented. 
Gets caught in a timeout loop. 