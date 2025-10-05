# DnsClient

This project implements a simple DNS client in Java.  
It constructs and sends DNS queries to a specified DNS server and parses the responses for A, NS, and MX records.  
The client also supports configurable timeout, retries, and port options.

## Compilation Instructions

1. Navigate to the source directory:
   cd src
2. Compile using javac   
   javac DnsClient.java

# Usage 

java DnsClient [-t timeout] [-r max-retries] [-p port] [-mx|-ns] @server name
-Server IP address is mandatory
-Server name is mandatory
-All other arguments are optionnal
