@echo off
cd /d DLRsuite
@echo on 
java -cp ".;lib\*" -Dfile.encoding=UTF-8 VerifyInferencesExample4 ".\Examples\Example4.ttl" "inferredGraph.ttl"
@echo off
cd /d ..