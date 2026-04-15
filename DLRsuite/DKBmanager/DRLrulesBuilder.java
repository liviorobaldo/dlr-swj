package DKBmanager;

import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;


public class DRLrulesBuilder 
{
    private static File SparqlRulesFile = new File("D-KB/SPARQLrulesCompact.txt");
    private static File DKBRules = new File("D-KB/DKBrules.ttl");
    private static File TBoxFile  = new File("D-KB/DKBTBox.ttl");
    
    public static void main(String[] args)
    {
        try
        {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
            ArrayList<Rule> sparqlRules = extractAllRules(SparqlRulesFile);
            List<Hashtable<String,String>> ETandTRandCheckExp = buildDLRfile(sparqlRules,DKBRules);
            buildTBoxFile(TBoxFile,ETandTRandCheckExp.get(0),ETandTRandCheckExp.get(1),ETandTRandCheckExp.get(2));
            
                //We also build and output the SHACL file, if the code of the compliance checkers is present.
            buildSHACLRulesForSHACLreasonerFromRobaldoetal2023();
            
                //finally, this method will print how many rules this procedure built, of which type, etc.
            printStats(sparqlRules);
        }
        catch(Exception e)
        {
            System.out.println(e.getMessage());
        }
    }
        
    private static class Rule
    {
        private String ID;
        private String description;
        ArrayList<String[]> antecedent = new ArrayList<String[]>();
        ArrayList<String[]> consequent = new ArrayList<String[]>();
    }
    
    private static ArrayList<Rule> extractAllRules(File SparqlRulesFile)throws Exception
    {
        ArrayList<String> lines = new ArrayList<String>();
        FileReader fr = new FileReader(SparqlRulesFile);
        BufferedReader br = new BufferedReader(fr);
        String line = null;
        while((line=br.readLine())!=null)if((!line.trim().isEmpty())&&(line.trim().charAt(0)!='#'))lines.add(line.trim());
        br.close();
        fr.close();
        
        ArrayList<Rule> rules = new ArrayList<Rule>();
        while(lines.isEmpty()==false)
        {
            String allRulesText = "";
            String ID = lines.remove(0);
            
            //System.out.println(ID);
            if(ID.indexOf("D-KB ID: statements2Formula1 (gdprC2A5P1p2ref)")!=-1)
                ID=ID;
            
            if(ID.indexOf("D-KB ID:")!=0)throw new Exception("Line "+ID+" should contain the \"D-KB ID\"");
            while((lines.isEmpty()==false)&&(lines.get(0).indexOf("D-KB ID:")!=0))allRulesText+=" "+lines.remove(0);
            
            String[] rulesTexts = allRulesText.split("CONSTRUCT");
            String description = rulesTexts[0].trim();
            for(int i=1;i<rulesTexts.length;i++)
            {
                String ruleText = "CONSTRUCT"+rulesTexts[i];
                        
                Rule rule = new Rule();    
                String construct =  ruleText.substring(ruleText.indexOf("CONSTRUCT"), ruleText.indexOf("WHERE")).trim();
                String where =  ruleText.substring(ruleText.indexOf("WHERE"),ruleText.length()).trim();

                construct = construct.substring(construct.indexOf("{")+1, construct.lastIndexOf("}")).trim();
                while(construct.isEmpty()==false)
                {
                    rule.consequent.add(new String[]
                    {
                        construct.substring(0, construct.indexOf(" ")).trim(),
                        construct.substring(construct.indexOf(" ")+1, construct.indexOf(" ",construct.indexOf(" ")+1)).trim(),
                        construct.substring(construct.indexOf(" ",construct.indexOf(" ")+1)+1, construct.indexOf(".")).trim()
                    });

                    construct = construct.substring(construct.indexOf(".")+1,construct.length()).trim();
                }

                where = where.substring(where.indexOf("{")+1, where.lastIndexOf("}")).trim();
                while(where.isEmpty()==false)
                {
                    if((where.indexOf("FILTER")==0)||(where.indexOf("BIND")==0))
                    {
                        int counter=1;
                        int p=where.indexOf("(")+1;
                        for(;p<where.length()&&counter>0;p++)
                            if(where.charAt(p)=='(')counter++; else if(where.charAt(p)==')')counter--;
                        
                        rule.antecedent.add(new String[]{where.substring(0, p).trim()});
                        where = where.substring(p, where.length()).trim();
                        if((where.length()>0)&&(where.charAt(0)=='.'))where=where.substring(1, where.length()).trim();
                    }
                    else if(where.indexOf("OPTIONAL")==0)
                    {
                        int counter=1;
                        int p=where.indexOf("{")+1;
                        for(;p<where.length()&&counter>0;p++)
                            if(where.charAt(p)=='{')counter++; else if(where.charAt(p)=='}')counter--;
                        
                        rule.antecedent.add(new String[]{where.substring(0, p).trim()});
                        where = where.substring(p, where.length()).trim();
                        if((where.length()>0)&&(where.charAt(0)=='.'))where=where.substring(1, where.length()).trim();
                    }
                        //Maybe there is a UNION. If so, we also create a String[] with a single element.
                    else if(where.indexOf("{")==0)
                    {
                        if((where.indexOf("UNION")==-1)||(where.indexOf("}")==-1)||(where.indexOf("}")>where.indexOf("UNION")))
                            throw new Exception("Ill-formed rule with the WHERE part including: \""+where+"\"");
                        
                        String temp = where.substring(0,where.indexOf("}")+1).trim();
                        where = where.substring(where.indexOf("}")+1, where.length()).trim();
                        
                        if(where.indexOf("UNION")!=0)
                            throw new Exception("Ill-formed rule with the WHERE part including: \""+temp+where+"\"");
                        
                        temp+="UNION";
                        where = where.substring("UNION".length(), where.length()).trim();
                        
                        if((where.indexOf("{")!=0)||(where.indexOf("}")==-1))
                            throw new Exception("Ill-formed rule with the WHERE part including: \""+where+"\"");
                        
                        rule.antecedent.add(new String[]{temp+where.substring(0,where.indexOf("}")+1).trim()});
                            //We remove the UNION but we add "." on top to continue on the next triple/cycle.
                        where = where.substring(where.indexOf("}")+1, where.length()).trim();
                    }
                    else 
                    {
                        rule.antecedent.add(new String[]
                        {
                            where.substring(0, where.indexOf(" ")).trim(),
                            where.substring(where.indexOf(" ")+1, where.indexOf(" ",where.indexOf(" ")+1)).trim(),
                            where.substring(where.indexOf(" ",where.indexOf(" ")+1)+1, where.indexOf(".")).trim()
                        });
                        where = where.substring(where.indexOf(".")+1,where.length()).trim();
                    }
                }

                rule.ID = ID;
                rule.description = description;
                rules.add(rule);
            }
        }
        
        return rules;
    }
    
    
//***************************************************************************************************************************************
//  Build the final DLR rules file
//***************************************************************************************************************************************
    private static List<Hashtable<String,String>> buildDLRfile(ArrayList<Rule> sparqlRules, File DLRrulesFile)throws Exception
    {
        PrintStream DLRrulesPS = new PrintStream(DLRrulesFile, StandardCharsets.UTF_8);
        
        DLRrulesPS.println("@prefix : <https://w3id.org/ontology/dlr#> .");
        DLRrulesPS.println("@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .");
        DLRrulesPS.println("@prefix xml: <http://www.w3.org/XML/1998/namespace> .");
        DLRrulesPS.println("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .");
        DLRrulesPS.println("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .");
        DLRrulesPS.println("@prefix time: <http://www.w3.org/2006/time#> .");
        DLRrulesPS.println("@base <https://w3id.org/ontology/dlr#> .");
    
        String latestID = "";
        for(Rule sparqlRule:sparqlRules)
        {
            //System.out.println(sparqlRule.ID);
            if(sparqlRule.ID.compareToIgnoreCase("D-KB ID: statements2Formula1 (gdprC2A5P1p2ref)")==0)
                sparqlRule=sparqlRule;

            //FIRST STEP: we write the "preface" of the SPARQL rule.
            if(sparqlRule.ID.compareToIgnoreCase(latestID)!=0)
            {
                DLRrulesPS.println();
                DLRrulesPS.println("#"+sparqlRule.ID);
                DLRrulesPS.println("#"+sparqlRule.description);
                latestID = sparqlRule.ID;
            }
            
            String dkbID = sparqlRule.ID.substring(sparqlRule.ID.indexOf(":")+1, sparqlRule.ID.lastIndexOf("(")).trim();
            DLRrulesPS.print("[rdf:type :InferenceRule; "
                    + ":has-dkb-id \""+dkbID+"\"^^xsd:string;"
                    + " :has-sparql-code \"\"\"\n\tCONSTRUCT{");
            
            
                //SECOND STEP: we collect all variables in the antecedent. If the antecedent contains UNION, we store the 
                //variables in the UNION separately (in varsInUNIONs), divided by branch.
                //NB. we constrain the syntax of the rules so that the variables in the CONSTRUCT cannot occure inside the UNION, 
                //unless they also occur *outside* the UNION. This will have to be checked (below).
            Hashtable<String,String> varsInWhere = new Hashtable<String,String>();
            ArrayList<Hashtable<String,String>> varsInUNIONs = new ArrayList<Hashtable<String,String>>();
            collectsVarsInAntecedent(sparqlRule, varsInWhere, varsInUNIONs);
            
                //THIRD STEP: we collect all variables that must go in BNode. 
                //Meanwhile, we also check the variables listed in the check-exceptions and those in the UNION.
            Hashtable<String,String> varsToBNode = collectsVarsInBNode_andcheckchecksException_andcheckVarsInUNIONs(sparqlRule, varsInWhere, varsInUNIONs);
            
                //FOURTH STEP: we build the CONSTRUCT.
            String construct = "";
            for(String[] ps:sparqlRule.consequent)
            {
                for(String p:ps)construct+=(p+" ");
                construct=construct.trim()+". ";
            }
            
                //For each variable in BNode, we check that this is an eventuality that is Obligatory, Permitted or Rexist.
                //Or, it is an Interval: we also permit the creation of new intervals.
                //Only these eventualities may be created as anonymous individuals, the thematic roles' values cannot, so
                //if we find a variable in BNode that is not rdf:type a modality, it's an error.
                //If, instead, the variable is an eventuality, we associate it with the ID of the DK-B rule to support traceability.
            ArrayList<String> vars = new ArrayList<String>(varsToBNode.keySet());
            ArrayList<String> varsAlreadyInBind = new ArrayList<String>();
            for(String var:vars)
            {
                boolean BIND=false;
                for(String[] temp:sparqlRule.antecedent)if((temp[0].indexOf("BIND")!=-1)&&(temp[0].indexOf(("AS "+var))!=-1)){BIND=true;varsAlreadyInBind.add(var);break;}
                if(BIND==true)continue;
                if((construct.indexOf(var+" :anytimebefore")!=-1)||(construct.indexOf(var+" :anytimeafter")!=-1))continue;
                
                
                if
                (
                    (construct.indexOf(var+" rdf:type :A-Obligatory")==-1)&&(construct.indexOf(var+" rdf:type :M-Obligatory")==-1)&&
                    (construct.indexOf(var+" rdf:type :A-Permitted")==-1)&&(construct.indexOf(var+" rdf:type :M-Permitted")==-1)&&
                    (construct.indexOf(var+" rdf:type :Rexist")==-1)&&(construct.indexOf(var+" rdf:type time:Interval")==-1)&&
                    (construct.indexOf(var+" :defeats")==-1)
                )throw new Exception("In "+dkbID+", the variable "+var+" is create through BNode but it is not an eventuality!");
                construct=construct.trim()+" "+var+" :has-generating-dkb-rule-id \""+dkbID+"\"^^xsd:string. ";
            }
            DLRrulesPS.println(construct.trim()+"}");
            
                //FIFTH STEP: we build the WHERE
            DLRrulesPS.print("\tWHERE{");
            String where = "";
            for(String[] ps:sparqlRule.antecedent)
            {
                if(ps.length==1)where+=(ps[0]+" ");//this is when we have FILTER, UNION, or similar
                else for(String p:ps)where+=(p+" ");
                where=where.trim()+". ";
            }
       
                //In the next cycle we add the BNode
            vars = new ArrayList<String>(varsToBNode.keySet());
            vars.removeAll(varsAlreadyInBind);
            for(String var:vars)where=where.trim()+" BIND(BNode() AS "+var+")";
            
                //the clause NOT EXISTS prevent the rule to loop infinitely. We ask that the model does not already
                //includes the triples inferred, if these contain new anonymous individuals, i.e., if varsToBNode 
                //is not empty. Therefore, the triples in CONSTRUCT are copied in NOT EXISTS, but we change the name
                //of the variables in varsToBNode to avoid names to clash (we add an "r" at the end).
            if(vars.size()>0)
            {
                String notExists = "NOT EXISTS{"+construct.trim()+"}";
                for(String var:vars)notExists=notExists.replaceAll(("\\?"+var.substring(1, var.length())), ("\\?"+var.substring(1, var.length())+"r"));
                where=where.trim()+" "+notExists;
            }
            
            DLRrulesPS.println(where.trim()+"}\"\"\"^^xsd:string].");
        }
        
        DLRrulesPS.close();
        
            //We extract and return all eventuality types and thematic roles that we will have
            //to write with buildETsTRsTBoxRulesFile below.
        Hashtable<String,String> EventualityTypes = new Hashtable<String,String>();
        Hashtable<String,String> ThematicRoles = new Hashtable<String,String>();
        Hashtable<String,String> CheckExceptionsAndDefeats = new Hashtable<String,String>();
        getETsAndTRsExcsAndDeft(sparqlRules, EventualityTypes, ThematicRoles, CheckExceptionsAndDefeats);
        
        return Arrays.asList(EventualityTypes, ThematicRoles, CheckExceptionsAndDefeats);
    }
    
    private static void collectsVarsInAntecedent(Rule sparqlRule, Hashtable<String,String> varsInWhere, ArrayList<Hashtable<String,String>> varsInUNIONs)throws Exception
    {        
        for(String[] ps:sparqlRule.antecedent)
        {
            if(ps.length==1)
            {
                if(ps[0].indexOf("UNION")!=-1)
                {
                    Hashtable<String,String> varsInUNION = new Hashtable<String,String>();
                    varsInUNIONs.add(varsInUNION);
                    String temp = ps[0];
                    while(temp.indexOf("?")!=-1)
                    {
                        temp = temp.substring(temp.indexOf("?"), temp.length()).trim();

                        if((temp.indexOf(" ")==-1)&&(temp.indexOf(".")==-1))
                            throw new Exception("Even inside the UNIONs you must terminate *all* triples (including the last ones) with \".\". It seems you didn't...");

                        String var = temp.substring(0, (temp.indexOf(" ")==-1)?temp.indexOf("."):temp.indexOf(" ")).replaceAll("\\.","");
                        if(var.indexOf("}")!=-1)var=var.substring(0,var.indexOf("}"));
                        varsInUNION.put(var,"");
                        temp = temp.substring(1, temp.length());
                    }
                }
                else if(ps[0].indexOf("BIND")!=-1)
                {
                    String var = ps[0].substring(ps[0].lastIndexOf("AS")+2,ps[0].lastIndexOf(")")).trim();
                    varsInWhere.put(var,"");
                }
                continue;
            }
                //If there is a checks-exception in the WHERE, all variables in its object must be included
            else if(ps[1].indexOf("checks-exception")!=-1)
            {
                String[] varsInList = ps[2].substring(ps[2].indexOf("(")+1, ps[2].lastIndexOf(")")).split(" ");
                for(String varInList:varsInList)varsInWhere.put(varInList,"");
            }
            
            if(ps[0].charAt(0)=='?')varsInWhere.put(ps[0],"");
            if(ps[2].charAt(0)=='?')varsInWhere.put(ps[2],"");
        }
    }
    
    private static Hashtable<String,String> collectsVarsInBNode_andcheckchecksException_andcheckVarsInUNIONs(Rule sparqlRule, Hashtable<String,String> varsInWhere, ArrayList<Hashtable<String,String>> varsInUNIONs)throws Exception
    {
        Hashtable<String,String> varsToBNode = new Hashtable<String,String>();
        
        for(String[] ps:sparqlRule.consequent)
        {
                //If there is a "checks-exception", we must check that each variable in the list as object occurs in WHERE 
                //(they cannot be BNode).
            if(ps[1].indexOf("checks-exception")!=-1)
            {
                String[] varsInObject = ps[2].trim().substring(1, ps[2].trim().length()-1).trim().split(" ");
                for(String var:varsInObject)
                    if(varsInWhere.get(var)==null)
                        throw new Exception("The var "+var+" object of "+ps[1]+" does not exist in WHERE");
            }
            
            if((ps[0].trim().charAt(0)=='?')&&(varsInWhere.get(ps[0].trim())==null))varsToBNode.put(ps[0].trim(), "");
            if((ps[1].trim().charAt(0)=='?')&&(varsInWhere.get(ps[1].trim())==null))varsToBNode.put(ps[1].trim(), "");
            if((ps[2].trim().charAt(0)=='?')&&(varsInWhere.get(ps[2].trim())==null))varsToBNode.put(ps[2].trim(), "");
        }
                
            //Now we check that the variables in UNION (if any) either occur in WHERE or 
            //do *not* occur in varsToBNode.
        for(int i=0;i<varsInUNIONs.size();i++)
        {
            Hashtable<String,String> ht = varsInUNIONs.get(i);
            List<String> vars = new ArrayList<String>(ht.keySet());
            for(String var:vars)
            {
                if(varsInWhere.get(var)!=null)continue;
                if(varsToBNode.get(var)!=null)
                    throw new Exception("At least a variable in the UNION is bound on a BNode()!");
            }
        }
        
        return varsToBNode;
    }

    private static void getETsAndTRsExcsAndDeft
    (
        ArrayList<Rule> sparqlRules, 
        Hashtable<String,String> EventualityTypes, 
        Hashtable<String,String> ThematicRoles,
        Hashtable<String,String> CheckExceptionsAndDefeats
    )
    {
        for(Rule sparqlRule:sparqlRules)
        {
                //We collect all triples of the rule in triples.
            ArrayList<String[]> triples = new ArrayList<String[]>();
            for(String[] antecedent:sparqlRule.antecedent)
                if((antecedent.length==1)&&(antecedent[0].indexOf("UNION")==-1))continue;//if it is FILTER we skip
                    //if it's UNION, we collect and add all triples in the UNION.
                else if((antecedent.length==1)&&(antecedent[0].indexOf("UNION")!=-1))
                {
                    String temp = antecedent[0].substring(antecedent[0].indexOf("{")+1,antecedent[0].indexOf("}")).trim();
                    if(temp.charAt(temp.length()-1)!='.')temp=temp+".";
                    temp=temp+" "+antecedent[0].substring(antecedent[0].lastIndexOf("{")+1,antecedent[0].lastIndexOf("}")).trim();
                    if(temp.charAt(temp.length()-1)!='.')temp=temp+".";
                    String[] temp2 = temp.split("\\.");
                    for(String temp3:temp2)triples.add(temp3.trim().split(" "));
                }
                else triples.add(antecedent);
            for(String[] consequent:sparqlRule.consequent)
                if((consequent.length==1)&&(consequent[0].indexOf("UNION")==-1))continue;//if it is FILTER we skip
                    //if it's UNION, we collect and add all triples in the UNION.
                else if((consequent.length==1)&&(consequent[0].indexOf("UNION")!=-1))
                {
                    String temp = consequent[0].substring(consequent[0].indexOf("{")+1,consequent[0].indexOf("}")).trim();
                    if(temp.charAt(temp.length()-1)!='.')temp=temp+".";
                    temp=temp+" "+consequent[0].substring(consequent[0].lastIndexOf("{")+1,consequent[0].lastIndexOf("}")).trim();
                    if(temp.charAt(temp.length()-1)!='.')temp=temp+".";
                    String[] temp2 = temp.split("\\.");
                    for(String temp3:temp2)triples.add(temp3.trim().split(" "));
                }
                else triples.add(consequent);

                //Now we collect all eventualities that are true for some modality.
            Hashtable<String,String> eventualitiesOnModality = new Hashtable<String,String>();        
            for(String[] triple:triples)
                if((triple[1].compareTo("rdf:type")==0)&&
                   ((triple[2].compareTo(":Rexist")==0)||(triple[2].compareTo(":A-Obligatory")==0)||(triple[2].compareTo(":M-Obligatory")==0)||
                    (triple[2].compareTo(":A-Prohibited")==0)||(triple[2].compareTo(":M-Prohibited")==0)||(triple[2].compareTo(":Permitted")==0)))
                    eventualitiesOnModality.put(triple[0], "");
            
                //Finally, we do another cycle on all triples:
                // - If the subject is an eventuality on modality collected in the previous cycle, and the predicate is rdf:type,
                //   then the object is an EventualityType.
                // - if the predicate is ":has-*", the predicate is a ThematicRole.
                // - if the predicate is ":checks-exception-*", its ending must be collected in CheckExceptionsAndDefeats.
            for(String[] triple:triples)
                if((eventualitiesOnModality.get(triple[0])!=null)&&(triple[1].compareTo("rdf:type")==0)&&
                   ((triple[2].compareTo(":Rexist")!=0)&&(triple[2].compareTo(":A-Obligatory")!=0)&&(triple[2].compareTo(":M-Obligatory")!=0)&&
                    (triple[2].compareTo(":A-Prohibited")!=0)&&(triple[2].compareTo(":M-Prohibited")!=0)&&(triple[2].compareTo(":Permitted")!=0)))
                    EventualityTypes.put(triple[2],"");
                else if((eventualitiesOnModality.get(triple[0])!=null)&&(triple[1].indexOf(":has-")==0))
                    if(triple[1].indexOf("/")!=-1)ThematicRoles.put(triple[1].substring(0, triple[1].indexOf("/")),"");
                    else ThematicRoles.put(triple[1],"");
                else if(triple[1].indexOf(":checks-exception-in")==0)
                    CheckExceptionsAndDefeats.put(triple[1].substring(":checks-exception-in".length(),triple[1].length()),"");
        }
    }
    
    private static void buildTBoxFile
    (
        File TBoxFile, 
        Hashtable<String,String> EventualityTypes, 
        Hashtable<String,String> ThematicRoles,
        Hashtable<String,String> CheckExceptionsAndDefeats
    )throws Exception
    {
        PrintStream TBoxPS = new PrintStream(TBoxFile, StandardCharsets.UTF_8);
        
        TBoxPS.println("\n\n#========================================================================================================");
        TBoxPS.println("#THIS FILE HAS BEEN AUTOMATICALLY GENERATED VIA THE METHOD \"buildTBoxFile\" IN \"DRLrulesBuilder.java\"");
        TBoxPS.println("#========================================================================================================\n");
        
        TBoxPS.println("@prefix : <https://w3id.org/ontology/dlr#> .");
        TBoxPS.println("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .");
        TBoxPS.println("@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .");
        TBoxPS.println("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .");
        TBoxPS.println("@prefix time: <http://www.w3.org/2006/time#> .");

        TBoxPS.println("@base <https://w3id.org/ontology/dlr#> .\n");

        TBoxPS.println("#Eventualities types in the examples and the D-KB");
        TBoxPS.println(":Pay rdf:type rdfs:Class,:EventualityType.");
        TBoxPS.println(":Give rdf:type rdfs:Class,:EventualityType.");
        TBoxPS.println(":Submit rdf:type rdfs:Class,:EventualityType.");
        TBoxPS.println(":Enter rdf:type rdfs:Class,:EventualityType.");
        TBoxPS.println(":Park rdf:type rdfs:Class,:EventualityType.");
        TBoxPS.println(":Identify rdf:type rdfs:Class,:EventualityType.");
        ArrayList<String> ETs = new ArrayList<String>(EventualityTypes.keySet());
        for(String ET:ETs)TBoxPS.println(ET+" rdf:type rdfs:Class,:EventualityType.");

        TBoxPS.println("\n#Thematic roles in the examples and the D-KB");
        TBoxPS.println(":has-instrument rdf:type rdf:Property,:ThematicRole;rdfs:domain :Eventuality.");
        TBoxPS.println(":has-recipient rdf:type rdf:Property,:ThematicRole;rdfs:domain :Eventuality.");
        TBoxPS.println(":has-aim rdf:type rdf:Property,:ThematicRole;rdfs:domain :Eventuality.");
        ArrayList<String> TRs = new ArrayList<String>(ThematicRoles.keySet());
        for(String TR:TRs)
            if(TR.compareToIgnoreCase(":has-time")==0)TBoxPS.println(TR+" rdf:type rdf:Property,:ThematicRole;rdfs:domain :Eventuality; rdfs:range time:Interval.");
            else TBoxPS.println(TR+" rdf:type rdf:Property,:ThematicRole;rdfs:domain :Eventuality.");

            //We introduce two individuals, :mi and :pi, representing −∞ and +∞, respectively, as well as the 
            //individual :IsWithoutDelayWrt, which defines an absolute notion of "without delay" (set to one day).            
        TBoxPS.println("\n#Individuals for the temporal notions");
        TBoxPS.println(":mi rdf:type time:Instant,:minusInfinity.");
        TBoxPS.println(":pi rdf:type time:Instant,:plusInfinity.");
        TBoxPS.println(":reasonableDelayDuration :has-value 'P1D'^^xsd:duration.");
        
        TBoxPS.println("\n#Subproperties of :checks-exception and :defeats");
        ArrayList<String> CEandDs = new ArrayList<String>(CheckExceptionsAndDefeats.keySet());
        for(String CEandD:CEandDs)
        {
            TBoxPS.println(":checks-exception-in"+CEandD+" rdfs:subPropertyOf :checks-exception.");
            TBoxPS.println(":defeats-on"+CEandD+" rdfs:subPropertyOf :defeats.");
        }
        
        TBoxPS.println("\n#TBox rules");
        
            //If ?t1 represents the time interval during which some personal data processing occurs, this rule creates the interval
            //from −∞ to the start of ?t1 **only if it does not already exist**. This prevents creating duplicate intervals.
        TBoxPS.println("[rdf:type :InferenceRule; :has-sparql-code \""+
        "CONSTRUCT{?t2 rdf:type time:Interval; time:hasBeginning :mi; time:hasEnd ?t1b}"+
	"WHERE{?ep rdf:type :ProcessPersonalData. ?ep :has-time ?t1. ?t1 time:hasBeginning ?t1b. BIND(BNode() AS ?t2) "+
        "NOT EXISTS{?t2r rdf:type time:Interval; time:hasBeginning :mi; time:hasEnd ?t1b}}\"^^xsd:string].\n");

            //If ?t1 represents the time interval during which personal data processing occurs, and ?t1e is its end, this rule 
            //creates the interval from ?t1e to ?t1e+?d, where ?d is the value of reasonableDelayDuration defined above.
            //As with the previous rule, this interval is created **only if it does not already exist**, preventing duplicates.
        TBoxPS.println("[rdf:type :InferenceRule; :has-sparql-code \""+
        "CONSTRUCT{?t2 rdf:type time:Interval; time:hasBeginning ?t1e; time:hasEnd ?t2e; :is-without-delay ?t1. ?t2e time:inXSDDateTime ?t2edt}"+
	"WHERE{?ep rdf:type :ProcessPersonalData. ?ep :has-time ?t1. ?t1 time:hasEnd ?t1e. "+
        "?t1e time:inXSDDateTime ?t1edt. :reasonableDelayDuration :has-value ?d. BIND(xsd:dateTime(?t1edt)+?d AS ?t2edt) "+
        "BIND(BNode() AS ?t2) BIND(BNode() AS ?t2e) "+
        "NOT EXISTS{?t2r rdf:type time:Interval; time:hasBeginning ?t1e; time:hasEnd ?t2er; :is-without-delay ?t1. ?t2er time:inXSDDateTime ?t2edt}}\"^^xsd:string].\n");
        
        TBoxPS.close();
    }

        //This procedure populates the file regulativerules_0.ttl in compliancecheckers-main\SHACL\INDEXED_RULES
        //(if the directory exists), while deleting all other files. The conversion from SPARQL to SHACL-SPARQL
        //is quite straightforward.
    private static void buildSHACLRulesForSHACLreasonerFromRobaldoetal2023()throws Exception
    {
        File directory = new File("../SHACLreasonerFromRobaldoetal2023");
        if(directory.exists()==false)return;
        
            //The following loop parses the DKBRules file, which has just been created, and extracts the full SPARQL rules.
        ArrayList<String> rules = new ArrayList<>();
        try(BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(DKBRules),StandardCharsets.UTF_8))) 
        {
            String line;
            String constructLine = null;
            while((line=br.readLine())!=null) 
            {
                line = line.trim();
                if(line.contains("CONSTRUCT"))constructLine=line;
                else if(line.contains("WHERE")&&constructLine!=null) 
                {
                    String cleanedWhere = line.replaceAll("\"\"\"\\^\\^xsd:string\\]\\.", "");// Clean the WHERE line ending
                    String rule = constructLine+" "+cleanedWhere;
                    rules.add(rule);
                    constructLine = null;
                }
            }
        }
        
            //We add all rules from the D-KB TBox (TBoxFile) and from the Deontic Logic Reified TBox (DLRrules/DLR.ttl).
            //The merged TBox is also saved in "../compliancecheckers-main/SHACL/TBoxDLRandDKB.ttl".
        Model TBoxDLRandDKB = RDFDataMgr.loadModel(new File("DLRrules/DLR.ttl").getAbsolutePath()).add(RDFDataMgr.loadModel(TBoxFile.getAbsolutePath()));
        Property hasSparqlCode = TBoxDLRandDKB.createProperty("https://w3id.org/ontology/dlr#has-sparql-code");
        StmtIterator it = TBoxDLRandDKB.listStatements(null,hasSparqlCode,(RDFNode)null);
        while(it.hasNext())rules.add(it.nextStatement().getObject().asLiteral().getString());
        try(FileOutputStream out=new FileOutputStream(new File("../SHACLreasonerFromRobaldoetal2023/TBoxDLRandDKB.ttl"))){RDFDataMgr.write(out,RDFDataMgr.loadModel(new File("DLRrules/DLR.ttl").getAbsolutePath()).add(TBoxDLRandDKB),RDFFormat.TURTLE);}

        printRules(rules,new File("../SHACLreasonerFromRobaldoetal2023/DLRandDKBrules.ttl"));
    }
    
    private static void printRules(ArrayList<String> rules, File outputFile)throws Exception
    {
        try(PrintStream ps=new java.io.PrintStream(new FileOutputStream(outputFile),true,"UTF-8")) 
        {
            ps.println("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .");
            ps.println("@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#>.");
            ps.println("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#>.");
            ps.println("@prefix time: <http://www.w3.org/2006/time#>.");
            ps.println("@prefix sh: <http://www.w3.org/ns/shacl#> .");
            ps.println("@prefix : <https://w3id.org/ontology/dlr#> .\n\n");
         
            Hashtable<String,Hashtable<String,String[]>> target2rule = new Hashtable<String,Hashtable<String,String[]>>();
            for(int i=0;i<rules.size();i++)
            {
                String rule = rules.get(i);
                String[] target = searchForTarget(rule);
                if(target2rule.get(target[1])==null)target2rule.put(target[1],new Hashtable<String,String[]>());
                target2rule.get(target[1]).put(rule,target);
            }
            
            ArrayList<String> keys = new ArrayList<String>(target2rule.keySet());
            for(int i=0;i<keys.size();i++)
            {
                String key = keys.get(i);
                ps.println(":rule"+i+" rdf:type sh:NodeShape;");
                
                Hashtable<String,String[]> sparqlCode2target = target2rule.get(key);
                ArrayList<String> sparqlCodes = new ArrayList<String>(sparqlCode2target.keySet());
                for(String sparqlCode:sparqlCodes)
                {
                    String[] target = sparqlCode2target.get(sparqlCode);
                    sparqlCode = sparqlCode.replaceAll((java.util.regex.Pattern.quote(target[2])+" "),"\\$this ");
                    sparqlCode = sparqlCode.replaceAll((java.util.regex.Pattern.quote(target[2])+"\\."),"\\$this. ");
                    sparqlCode = sparqlCode.replaceAll((java.util.regex.Pattern.quote(target[2])+"\\}"),"\\$this}");
                    sparqlCode = sparqlCode.replaceAll((java.util.regex.Pattern.quote(target[2])+">>"),"\\$this>>");

                    ps.println("\tsh:rule[rdf:type sh:SPARQLRule;\n"+
                        "\t\tsh:prefixes[sh:declare\n"+
                        "\t\t\t[sh:prefix\"xsd\";sh:namespace\"http://www.w3.org/2001/XMLSchema#\"^^xsd:anyURI],\n"+
                        "\t\t\t[sh:prefix\"rdf\";sh:namespace\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"^^xsd:anyURI],\n"+
                        "\t\t\t[sh:prefix\"rdfs\";sh:namespace\"http://www.w3.org/2000/01/rdf-schema#\"^^xsd:anyURI],\n"+
                        "\t\t\t[sh:prefix\"time\";sh:namespace\"http://www.w3.org/2006/time#\"^^xsd:anyURI],\n"+
                        "\t\t\t[sh:prefix\"\";sh:namespace\"https://w3id.org/ontology/dlr#\"^^xsd:anyURI]];\n"+
                        "\t\t\tsh:construct \"\"\""+sparqlCode+"\"\"\"];");
                }
                
                String[] target = sparqlCode2target.get(sparqlCodes.get(0));//all SPARQL codes are linked to the same target.
                ps.println(target[0]+" "+target[1]+".");
                ps.println();
            }
        }
    }
    
        //This rule search for the class to put in sh:targetClass and the corresponding $this.
    private static String[] searchForTarget(String rule)throws Exception
    {
        if(rule.indexOf("?ep rdf:type :ProcessPersonalData.")!=-1)return new String[]{"sh:targetClass",":ProcessPersonalData","?ep"};
        else if(rule.indexOf("?w rdf:type :DataSubject.")!=-1)return new String[]{"sh:targetClass",":DataSubject","?w"};
        else if(rule.indexOf("?y :controller-of ?z.")!=-1)return new String[]{"sh:targetSubjectsOf",":controller-of","?y"};
        else if(rule.indexOf("?epu rdf:type :PublicInterest.")!=-1)return new String[]{"sh:targetClass",":PublicInterest","?epu"};
        else if(rule.indexOf("?epu rdf:type :Research.")!=-1)return new String[]{"sh:targetClass",":Research","?epu"};
        else if(rule.indexOf("?epu rdf:type :Statistic.")!=-1)return new String[]{"sh:targetClass",":Statistic","?epu"};
        else if(rule.indexOf("?e rdf:type :Delete.")!=-1)return new String[]{"sh:targetClass",":Delete","?e"};
        else if(rule.indexOf("?e rdf:type :Rectify.")!=-1)return new String[]{"sh:targetClass",":Rectify","?e"};
        else if(rule.indexOf("?d rdf:type :EthicalData.")!=-1)return new String[]{"sh:targetClass",":EthicalData","?d"};
        else if(rule.indexOf("?d rdf:type :OpinionData.")!=-1)return new String[]{"sh:targetClass",":OpinionData","?d"};
        else if(rule.indexOf("?d rdf:type :ReligiousOrPhilosophicalBeliefsData.")!=-1)return new String[]{"sh:targetClass",":ReligiousOrPhilosophicalBeliefsData","?d"};
        else if(rule.indexOf("?d rdf:type :TradeUnionMembership.")!=-1)return new String[]{"sh:targetClass",":TradeUnionMembership","?d"};
        else if(rule.indexOf("?d rdf:type :HealthData.")!=-1)return new String[]{"sh:targetClass",":HealthData","?d"};
        else if(rule.indexOf("?d rdf:type :SexualData.")!=-1)return new String[]{"sh:targetClass",":SexualData","?d"};
        else if(rule.indexOf("?d rdf:type :SexualOrientationData.")!=-1)return new String[]{"sh:targetClass",":SexualOrientationData","?d"};
        else if(rule.indexOf("?ex :checks-exception-in-8-1-ms (?w ?age).")!=-1)return new String[]{"sh:targetSubjectsOf",":checks-exception-in-8-1-ms","?ex"};
        else if(rule.indexOf("?ex :checks-exception-in-9-2-a (?egc).")!=-1)return new String[]{"sh:targetSubjectsOf",":checks-exception-in-9-2-a","?ex"};
        else if(rule.indexOf("?enp :checks-exception-in-9-2 (?w ?t1 ?x ?z ?epu).")!=-1)return new String[]{"sh:targetSubjectsOf",":checks-exception-in-9-2","?enp"};
        else if(rule.indexOf("?oa rdf:type :OfficialAuthority.")!=-1)return new String[]{"sh:targetClass",":OfficialAuthority","?oa"};
        else if(rule.indexOf("?el :checks-exception-in-21-1 (?t1 ?w ?ep ?y).")!=-1)return new String[]{"sh:targetSubjectsOf",":checks-exception-in-21-1","?el"};
        else if(rule.indexOf("?ex :checks-exception-in-21-1-NW (?ep ?w ?y).")!=-1)return new String[]{"sh:targetSubjectsOf",":checks-exception-in-21-1-NW","?ex"};
        else if(rule.indexOf("?ek :checks-exception-in-89-1 (?epu).")!=-1)return new String[]{"sh:targetSubjectsOf",":checks-exception-in-89-1","?ek"};
        else if(rule.indexOf("?I1 :includes ?I2")!=-1)return new String[]{"sh:targetSubjectsOf",":includes","?I1"};
        else if(rule.indexOf("?e :not|^:not ?ne.")!=-1)return new String[]{"sh:targetSubjectsOf",":not","?e"};
        else if(rule.indexOf("?I time:hasBeginning/rdf:type :minusInfinity")!=-1)return new String[]{"sh:targetClass","time:Interval","?I"};
        else if(rule.indexOf("?I (time:hasBeginning|time:hasEnd) ?be")!=-1)return new String[]{"sh:targetSubjectsOf","time:hasBeginning","?I"};
        else if(rule.indexOf("?sc rdfs:subClassOf+ ?c.")!=-1)return new String[]{"sh:targetSubjectsOf","rdfs:subClassOf","?sc"};
        else if(rule.indexOf("?sp rdfs:subPropertyOf ?p.")!=-1)return new String[]{"sh:targetSubjectsOf","rdfs:subPropertyOf","?sp"};
        else if(rule.indexOf("?p rdfs:range ?c.")!=-1)return new String[]{"sh:targetSubjectsOf","rdfs:range","?p"};
        else if(rule.indexOf("?p rdfs:domain ?c.")!=-1)return new String[]{"sh:targetSubjectsOf","rdfs:domain","?p"};
        else if(rule.indexOf("?I rdf:type time:Interval")!=-1)return new String[]{"sh:targetClass",":Interval","?I"};
        else throw new Exception("No predicate can be identified for sh:targetClass");
    }
    
    

    private static void printStats(ArrayList<Rule> sparqlRules)
    {
        System.out.println("A total of "+sparqlRules.size()+" reified Deontic Logic rules have been encoded for processing by DLRreasoner.java");
        
        int regulatives = 0;
            int obligations = 0;
            int permissions = 0;
            int notObligations = 0;
            int notPermissions = 0;
        int constitutives = 0;
        int exceptions = 0;
        for(Rule sparqlRule:sparqlRules)
        {
            boolean regulative = false;
            boolean exception = false;
            for(String[] triple:sparqlRule.consequent)
            {
                if((triple[2].indexOf(">>")!=-1)&&(triple[2].indexOf(":false")!=-1)&&(triple[2].indexOf("Obligatory")!=-1)){notObligations++;regulative=true;}
                else if((triple[2].indexOf(">>")!=-1)&&(triple[2].indexOf(":false")!=-1)&&(triple[2].indexOf("Permitted")!=-1)){notPermissions++;regulative=true;}        
                else if(triple[2].indexOf("Obligatory")!=-1){obligations++;regulative=true;}
                else if(triple[2].indexOf("Permitted")!=-1){permissions++;regulative=true;}
                else if(triple[1].indexOf(":defeats")!=-1)exception=true;
            }
            
            if(regulative==true)regulatives++;
            else if(exception==true)exceptions++;
            else constitutives++;
        }
        
        System.out.println("Among them:");
        System.out.println(" => "+regulatives+" regulative rules, triggering:");
        System.out.println("\t- "+obligations+" obligations");
        System.out.println("\t- "+permissions+" permissions.");
        System.out.println("\t- "+notObligations+" not-obligations.");
        System.out.println("\t- "+notPermissions+" not-permissions.");
        System.out.println(" => "+constitutives+" constitutive rules.");
        System.out.println(" => "+exceptions+" exceptions.");
    }
}
