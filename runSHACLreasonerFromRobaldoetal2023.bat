@echo off
cd /d SHACLreasonerFromRobaldoetal2023
@echo on 
java -cp ".;lib\*" -Dfile.encoding=UTF-8 SHACLreasonerFromRobaldoetal2023 ".\TBoxDLRandDKB.ttl" ".\DLRandDKBrules.ttl" ".\DLRrulesCompliance.ttl" ".\UseCases"
@echo off
cd /d ..