package GenerateAndTest;

import java.util.*;
import java.util.stream.*;
import org.apache.jena.atlas.json.*;
import org.apache.jena.query.*;
import org.apache.jena.graph.*;
import org.apache.jena.sparql.core.*;

public class DKBInferencesVerifier
{
    public static void VerifyInferences(Graph inferredGraph,JsonArray cluster,BitSet factsAsserted)throws Exception
    {
        Hashtable<String,String[]> regulatives=new Hashtable<String,String[]>();
        Hashtable<String,String> constitutives=new Hashtable<String,String>();
        Hashtable<String,String> exceptions=new Hashtable<String,String>();

        fetchRulesToVerify(cluster,factsAsserted,regulatives,constitutives,exceptions);

            //There should be only a single regulative rule: the root of this cluster. If there are two or more: exception.
        if(regulatives.size()>1)throw new Exception("Multiple roots in the cluster!");
        String DKBidRegulative = regulatives.keys().nextElement();
        String[] rexistsOnRootOfCluster = regulatives.get(DKBidRegulative);
        
            //This method removes all rules that are overridden by active exceptions, which may themselves include other exceptions.
        removeDefeatedRules(regulatives,constitutives,exceptions);

            //From this point onward, exceptions are no longer needed; only *active* regulative and constitutive rules
            //are considered below. These are the rules contained in the Hashtables 'regulatives' and 'constitutives'.
            //Note that the single regulative rule we had (the root of the cluster) may have been defeated, and
            //'regulatives' may now be empty.
        DatasetGraph dataset = DatasetGraphFactory.create();
        dataset.addGraph(Quad.defaultGraphIRI,inferredGraph);
        checkRegulativeRule(dataset,DKBidRegulative,rexistsOnRootOfCluster,regulatives);
        ArrayList<String> rulesLabels = new ArrayList<String>(constitutives.keySet());
        for(String ruleLabel:rulesLabels)checkConstitutiveRule(dataset,ruleLabel,constitutives.get(ruleLabel));
    }
    
    private static void fetchRulesToVerify
    (
        JsonArray cluster, BitSet factsAsserted,
        Hashtable<String,String[]> regulatives, Hashtable<String,String> constitutives, Hashtable<String,String> exceptions
    )
    {
        for(int i=0;i<cluster.size();i++)
        {
            if(factsAsserted.get(i)==false)continue;
            
            if(cluster.get(i).getAsObject().getString("type").compareToIgnoreCase("regulative")==0)
            {
                Set<String> triples = Arrays.stream(cluster.get(i).getAsObject().getString("consequent").split("\n"))
                    .map(String::trim).filter(line -> !line.isEmpty()).collect(Collectors.toSet());
                ArrayList<String> rexists = new ArrayList<String>();
                for(String triple:triples)if(triple.indexOf(":Rexist")!=-1)rexists.add(triple.substring(0,triple.indexOf(" ")).trim());
                regulatives.put(cluster.get(i).getAsObject().getString("D-KBid"),rexists.toArray(new String[rexists.size()]));
            }
            else if(cluster.get(i).getAsObject().getString("type").compareToIgnoreCase("constitutive")==0)
                constitutives.put(cluster.get(i).getAsObject().getString("D-KBid"),cluster.get(i).getAsObject().getString("consequent"));
            else if(cluster.get(i).getAsObject().getString("type").compareToIgnoreCase("exception")==0)
                exceptions.put(cluster.get(i).getAsObject().getString("D-KBid"),cluster.get(i).getAsObject().getString("consequent"));
        }
        
            //At this point, the constitutives and exceptions map the D-KB IDs to indexes. These must be 
            //replaced with the D-KB IDs of the rules corresponding to those indexes in the cluster.
        Hashtable<String,String> index2DKBid = new Hashtable<String,String>();
        for(JsonValue temp:cluster)index2DKBid.put(temp.getAsObject().getString("index"),temp.getAsObject().getString("D-KBid"));
        ArrayList<String> keys = new ArrayList<String>(constitutives.keySet());
        for(String key:keys)constitutives.put(key,index2DKBid.get(constitutives.get(key)));
        keys = new ArrayList<String>(exceptions.keySet());
        for(String key:keys)exceptions.put(key,index2DKBid.get(exceptions.get(key)));
    }
    
        //This method identifies the rules that have been defeated by exceptions. However, exceptions can also defeat 
        //other exceptions recursively. Therefore, we need to rebuild the exception tree.
    private static void removeDefeatedRules(Hashtable<String,String[]> regulatives, Hashtable<String,String> constitutives, Hashtable<String,String> exceptions)
    {
            //We collect all active exceptions.
        ArrayList<String> activeExceptions = new ArrayList<String>(exceptions.keySet());

            //Since exceptions can also defeat other exceptions, we must incrementally reduce exceptionsLabels by removing 
            //all exceptions defeated by others. If an exception is itself defeated, we must check whether it applies,
            //recursively handling exceptions to exceptions.
            //To manage this, we define a recursive procedure that, given an exception, searches for the lowest-level 
            //exception to remove. We repeat this process until no more exceptions need removal.
            //At the end, exceptionsLabels will contain only exceptions that defeat regulative or constitutive rules
            //(or "spurious" exceptions that defeat exceptions removed from exceptionsLabels during this cycle).
        while(true)
        {
            String exceptionToRemove = null;
            for(String activeException:activeExceptions)
            {
                exceptionToRemove = identifyLowestExceptionToRemove(activeException,activeExceptions,exceptions);
                if(exceptionToRemove!=null)break;
            }
            
                //If there are no more exceptions to remove, we break the while(true)
            if(exceptionToRemove!=null)activeExceptions.remove(exceptionToRemove);
            else break;
        }
        
            //At this point, we only have active exceptions that block regulative or constitutive rules. We remove them from the 
            //Hashtable(s) (removal is attempted on both lists without an "if": if the label is not present, the removal has no effect).
        for(String activeException:activeExceptions)
        {
            regulatives.remove(exceptions.get(activeException));
            constitutives.remove(exceptions.get(activeException));
        }
    }

        //Recursive procedure to determine, for each exception, whether there are exceptions to it.
        //If so, we continue recursively to check for exceptions to exceptions, and so on, until we identify the lowest-level 
        //exception that should be removed (if any).
    private static String identifyLowestExceptionToRemove(String activeException, ArrayList<String> activeExceptions, Hashtable<String,String> exceptions)
    {
            //We check whether there is an exception to this exception that defeats it.
            //If so, we continue recursively to see whether another exception is defeated at a lower level.
            //If not, the input exception is defeated and must be removed; otherwise, the lower-level exception must be removed.
        for(String exceptionToException:activeExceptions)
        {
            if(activeException.compareToIgnoreCase(exceptions.get(exceptionToException))==0)
            {
                String exceptionToExceptionToException = identifyLowestExceptionToRemove(exceptionToException,activeExceptions,exceptions);
                if(exceptionToExceptionToException!=null)return exceptionToExceptionToException;
                else return activeException;
            }
        }
        
        return null;
    }
    
    private static void checkRegulativeRule(DatasetGraph dataset, String DKBidRegulative, String[] rexistsOnRootOfCluster, Hashtable<String,String[]> regulatives)throws Exception
    {
            //If DKBidRegulative is active (i.e., 'regulatives' is not empty), the method checks that all associated Rexist facts defeasibly fulfill it.
            //If DKBidRegulative is inactive (i.e., 'regulatives' is empty), the method checks that none of the associated Rexist facts defeasibly fulfill it.
        for(String rexistFact:rexistsOnRootOfCluster)
        {
            String query=addPrefixes(
                "SELECT ?dsDKBid WHERE{"
                +rexistFact+" ?p ?ds. "
                +"VALUES ?p {:complies-with :violates :conforms-to :rexists-although-not-obligatory}. "
                +"?ds :has-generating-dkb-rule-id ?dsDKBid}"
            );

            try(QueryExecution qe=QueryExecutionFactory.create(QueryFactory.create(query),dataset))
            {
                List<QuerySolution> results = ResultSetFormatter.toList(qe.execSelect());
                if((regulatives.isEmpty()==false)&&(results.isEmpty()==true))
                    throw new Exception("Rule failure for "+DKBidRegulative+" on "+rexistFact+": no matching deontic statement found.");
                else if((regulatives.isEmpty()==true)&&(results.isEmpty()==false))
                    throw new Exception("Rule failure for "+DKBidRegulative+" on "+rexistFact+": the rule has been defeated, but "+rexistFact+" still D-fulfills it.");
            }
        }
    }
    
    private static void checkConstitutiveRule(DatasetGraph dataset,String DKBidConstitutive,String D_fulfills)throws Exception
    {
        String query=addPrefixes(
            "SELECT ?dsDKBid WHERE{"
            +"?rexist rdf:type :Rexist. "
            +"?rexist :has-generating-dkb-rule-id \""+DKBidConstitutive+"\". "
            +"?rexist ?p ?ds. "
            +"VALUES ?p {:complies-with :violates :conforms-to :rexists-although-not-obligatory}. "
            +"?ds :has-generating-dkb-rule-id ?dsDKBid}"
        );

        List<QuerySolution> results;
        try(QueryExecution qe=QueryExecutionFactory.create(QueryFactory.create(query),dataset)){results=ResultSetFormatter.toList(qe.execSelect());}
        if(results.isEmpty())
        {
            throw new Exception(
                "I found a rule application where the constitutive rule \""+DKBidConstitutive+
                "\" does not produce any really existing eventuality that complies-with/violates/conforms-to/rexists-although-not-obligatory "+
                "a deontic statement from the rule with ID \""+D_fulfills+"\"."
            );
        }

        for(QuerySolution result:results)
        {
            String found = result.getLiteral("dsDKBid").getString();
            if(!D_fulfills.equalsIgnoreCase(found))
            {
                throw new Exception(
                    "I found a rule application where the constitutive rule \""+DKBidConstitutive+
                    "\" produces a really existing eventuality that does not comply-with/violate/conform-to/rexists-although-not-obligatory "+
                    "a deontic statement from the rule with ID \""+D_fulfills+"\". It instead relates to rule \""+found+"\"."
                );
            }
        }
    }

        //Small utility that statically adds prefixes to SPARQL queries, so we don't have to write them every time.
    private static String addPrefixes(String triples)
    {
        return
        "PREFIX : <https://w3id.org/ontology/dlr#>\n"+
        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"+
        "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"+
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"+
        "PREFIX time: <http://www.w3.org/2006/time#>\n"+
        triples;
    }
}
