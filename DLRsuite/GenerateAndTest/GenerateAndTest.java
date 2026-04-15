package GenerateAndTest;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import org.apache.jena.atlas.json.*;
import org.apache.jena.riot.*;
import org.apache.jena.graph.*;
import DLRreasoner.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

public class GenerateAndTest 
{
//------------------------------------------------------------------------------------------------------------------------ 
// This class generates and tests all possible applications of the rules in templateRulesUseCase0File.
// It uses a boolean vector to assign true/false to each rule. A rule is applied only if its boolean value is true. 
// We then check the inferred models to verify that all rules that should have been applied (expected model) were indeed 
// applied. All possible boolean assignments are generated and tested.
//------------------------------------------------------------------------------------------------------------------------ 
    private static final File UseCaseBaseline = new File("./D-KB/UseCaseBaseline.json");
    private static File DKBTBoxFile  = new File("./D-KB/DKBTBox.ttl");
    private static File DKBrulesFile = new File("./D-KB/DKBrules.ttl");
    public static void main(String[] args)
    {
        try
        {
                //Reasoner initialization
            Graph domainTBox=GraphMemFactory.createDefaultGraph();
            try(FileInputStream fis=new FileInputStream(DKBTBoxFile)){RDFDataMgr.read(domainTBox,fis,Lang.TURTLE);}
            try(FileInputStream fis=new FileInputStream(DKBrulesFile)){RDFDataMgr.read(domainTBox,fis,Lang.TURTLE);}
            DLRreasoner reasoner=new DLRreasoner(domainTBox);
            
                //Load the JsonArray from the JSON file and remove the "IntervalsAndInstants" element
            FileInputStream fis = new FileInputStream(UseCaseBaseline);
            JsonArray DKBrules = JSON.parse(JSON.parse(fis).toString()).get("D-KBrules").getAsArray();
            fis.close();
            String tempIntervalsAndInstants = null;
            for(JsonValue temp:DKBrules)if(temp.getAsObject().hasKey("IntervalsAndInstants"))
            {tempIntervalsAndInstants=temp.getAsObject().getString("IntervalsAndInstants");DKBrules.remove(temp);break;}
            if(tempIntervalsAndInstants==null)throw new Exception("\"IntervalsAndInstants\" is not present!");
            final String IntervalsAndInstants = tempIntervalsAndInstants;

            JsonArray regulativeRules = extractRegulativeRules(DKBrules);
            for(JsonValue rootOfCluster:regulativeRules)
            {            
                JsonArray cluster = extractCluster(rootOfCluster,DKBrules);
                printRulesInCluster(regulativeRules,rootOfCluster,cluster);
                
                    //BitSet vector initialization: toProcess contains the set of combinations for each cluster, each represented by a BitSet.
                ArrayList<BitSet> toProcess = generateCombinations(cluster);
                    //Printing toProcess is useful for debugging. Add a breakpoint at "toProcess=toProcess;" to see which combinations
                    //have been selected for each cluster.
                //for(BitSet r:toProcess)System.out.println(r);
                //toProcess=toProcess;
        
                while(toProcess.isEmpty()==false)
                {
                    System.out.println("\tStill to process on cluster "+(regulativeRules.indexOf(rootOfCluster)+1)+"/"+regulativeRules.size()+": "+toProcess.size());
                    System.out.flush();

                    BitSet combinationOfAssertedFacts=(BitSet)toProcess.remove(0);
                    StringReader reader = new StringReader(generateUseCaseABox(cluster,IntervalsAndInstants,combinationOfAssertedFacts));
                    Graph ABox = GraphMemFactory.createDefaultGraph();
                    try(InputStream in=new ByteArrayInputStream(generateUseCaseABox(cluster,IntervalsAndInstants,combinationOfAssertedFacts).getBytes(StandardCharsets.UTF_8)))
                    {RDFDataMgr.read(ABox,in,Lang.TURTLE);}
                    
                    toProcess.remove(combinationOfAssertedFacts);
                    
                    /**
                    Graph inferredGraph = reasoner.InferResults(ABox,true);
                    Model outputModel1=ModelFactory.createModelForGraph(reasoner.InferResults(ABox,true));
                    DLRreasoner.PREFIX_MAP.forEach((prefix,uri)->{outputModel1.setNsPrefix(prefix,uri);});
                    try(FileOutputStream out=new FileOutputStream(new File("./inferredGraph.ttl"))){RDFDataMgr.write(out,outputModel1,RDFFormat.TURTLE);}
                    /**/
                    
                    try{DKBInferencesVerifier.VerifyInferences(reasoner.InferResults(ABox,false),cluster,combinationOfAssertedFacts);} 
                    catch(Exception e) 
                    {
                        System.out.println("Failed rule application:");
                        System.out.println("\t" + e.getMessage());

                        try{
                            Graph ABox2=GraphMemFactory.createDefaultGraph();
                            System.out.println("BitSet: "+combinationOfAssertedFacts);
                            try(InputStream in=new ByteArrayInputStream(generateUseCaseABox(cluster,IntervalsAndInstants,combinationOfAssertedFacts).getBytes(StandardCharsets.UTF_8))){RDFDataMgr.read(ABox2,in,Lang.TURTLE);}
                            Graph inferredGraph = reasoner.InferResults(ABox2,true);
                            Model outputModel=ModelFactory.createModelForGraph(inferredGraph);
                            DLRreasoner.PREFIX_MAP.forEach((prefix,uri)->{outputModel.setNsPrefix(prefix,uri);});
                            try(FileOutputStream out=new FileOutputStream(new File("./inferredGraph.ttl"))){RDFDataMgr.write(out,outputModel,RDFFormat.TURTLE);}
                            DKBInferencesVerifier.VerifyInferences(inferredGraph,cluster,combinationOfAssertedFacts);
                        }catch(Exception e2) 
                        {
                            e2=e2;
                        }

                        System.exit(0);
                    }
                }
            }
            
            System.out.println("\n\n--------------------------------------------------------------------");
            System.out.println("All possible applications of the rules have been generated & tested.");
        }
        catch(Exception e){System.out.println(e.getMessage());}
    }
    
        //This method scans all JSON objects in DKBrules and returns those associated with a regulative rule.
        //Each of these is called the "root" of a cluster and is processed one by one in the main method. In each iteration, 
        //we process the "tree" with one (or more) regulative rules at the root, along with all their constitutive rules
        //and exceptions beneath them (including exceptions to constitutive rules or to other exceptions).
    protected static JsonArray extractRegulativeRules(JsonArray DKBrules)throws Exception
    {
        JsonArray DKBregulativeRules = new JsonArray();
        for(JsonValue temp:DKBrules)
            if(temp.getAsObject().getString("type").compareToIgnoreCase("regulative")==0)
                DKBregulativeRules.add(temp);
        return DKBregulativeRules;
    }
    
        //This method builds a cluster starting from the root. It first searches for the constitutive rules
        //and exceptions associated with the root, and then recursively for the exceptions of those rules.
    private static JsonArray extractCluster(JsonValue rootOfCluster, JsonArray DKBrules)throws Exception
    {
        JsonArray cluster = new JsonArray();
        cluster.add(rootOfCluster);

            //rulesDKBids maps each JSON object index to its corresponding D-KB ID.
            //We start from the one in rootOfCluster.
        Hashtable<String,String> rulesDKBids = new Hashtable<String,String>();
        rulesDKBids.put(rootOfCluster.getAsObject().getString("index"),rootOfCluster.getAsObject().getString("D-KBid"));
        
            //Until the transitive closure is reached, JSON objects are incrementally added to the cluster
            //if they are associated with a constitutive rule that D-fulfills a rule in rulesDKBids, or with
            //an exception that defeats a rule in rulesDKBids.
        while(true)
        {
            boolean addedAtLeastOne = false;
            
            for(JsonValue temp:DKBrules)
            {
                boolean exists = false;
                for(JsonValue inthecluster:cluster)if(inthecluster==temp){exists=true;break;}
                if(exists==true)continue;//if it was already to the cluster, we skip it.

                    //The JSON object is added to the cluster if its consequent matches one of the labels
                    //in rulesDKBids, i.e., if the rule belongs to the "tree" rooted at rootOfCluster.
                if(rulesDKBids.get(temp.getAsObject().get("consequent").getAsString().value())!=null)
                {
                    rulesDKBids.put(temp.getAsObject().getString("index"),temp.getAsObject().getString("D-KBid"));
                    cluster.add(temp);
                    addedAtLeastOne=true;
                    break;
                }
            }
            
            if(addedAtLeastOne==false)break;//If no new JSON objects have been added to the cluster, we exit.
        }

        return cluster;
    }
    
        //This method prints the rules in the cluster, including the regulative rule at the root
        //and all constitutive rules and exceptions beneath it.
    private static void printRulesInCluster(JsonArray regulativeRules, JsonValue rootOfCluster, JsonArray cluster)
    {
        System.out.println("------------------------------------------------------------------------");
        System.out.println("CLUSTER #"+(regulativeRules.indexOf(rootOfCluster)+1));

        Set<String> regulativesSet = new HashSet<String>();
        Set<String> constitutivesSet = new HashSet<String>();
        Set<String> exceptionsSet = new HashSet<String>();

        for(JsonValue objInCluster:cluster)
        {
            String type=objInCluster.getAsObject().getString("type");
            String id=objInCluster.getAsObject().getString("D-KBid").trim();
            if(type.equalsIgnoreCase("regulative"))regulativesSet.add(id);
            else if(type.equalsIgnoreCase("constitutive"))constitutivesSet.add(id);
            else if(type.equalsIgnoreCase("exception"))exceptionsSet.add(id);
        }

        Comparator<String> cmp=new Comparator<String>()
        {
            public int compare(String s1,String s2)
            {
                int i1=s1.indexOf("statements")+"statements".length();
                int j1=s1.indexOf("Formula");
                int n1=Integer.parseInt(s1.substring(i1,j1));

                int i2=s2.indexOf("statements")+"statements".length();
                int j2=s2.indexOf("Formula");
                int n2=Integer.parseInt(s2.substring(i2,j2));

                if(n1!=n2)return n1-n2;
                return s1.compareTo(s2);
            }
        };

        List<String> regulatives = new ArrayList<>(regulativesSet);
        List<String> constitutives = new ArrayList<>(constitutivesSet);
        List<String> exceptions = new ArrayList<>(exceptionsSet);
        Collections.sort(regulatives,cmp);
        Collections.sort(constitutives,cmp);
        Collections.sort(exceptions,cmp);

        System.out.print(" - REGULATIVE RULE (root): ");
        for(int i=0;i<regulatives.size();i++){System.out.print(regulatives.get(i));if(i<regulatives.size()-1)System.out.print(", ");}
        System.out.println();
        System.out.print(" - CONSTITUTIVE RULES: ");
        for(int i=0;i<constitutives.size();i++){System.out.print(constitutives.get(i));if(i<constitutives.size()-1)System.out.print(", ");}
        System.out.println();
        System.out.print(" - EXCEPTIONS: ");
        for(int i=0;i<exceptions.size();i++){System.out.print(exceptions.get(i));if(i<exceptions.size()-1)System.out.print(", ");}
        System.out.println();
        System.out.flush();
    }

        //This method, given a cluster, generates all possible combinations of facts to be asserted. If the cluster contains 
        //n JSON objects, the total number of combinations is, in principle, 2^n, since the facts in each JSON object can be 
        //asserted or not. However, some objects refer to constitutive rules that D-fulfill the same regulative rules. 
        //These can be tested in isolation, so combinations in which two or more of them are asserted are removed; only 
        //combinations where none or exactly one of them is asserted are kept. The same applies to exceptions: for groups of 
        //exceptions that defeat the same rule, all combinations in which two or more are asserted are removed, leaving only
        //combinations where none or exactly one of them is asserted.
    private static ArrayList<BitSet> generateCombinations(JsonArray cluster)
    {
        ArrayList<BitSet> ret = new ArrayList<>();
        
            //First, all 2^n possible combinations are generated, where n is the size of the cluster.
        BitSet generator = new BitSet(cluster.size());
        generator.set(0);//first bit is set to 1/true, all the others to 0/false by defeault.
        int totalCombinations = 1<<cluster.size();//this calculates 2^applications.length            
        for(int i=1;i<totalCombinations;i++) 
        {
                //Only the combinations including the root are kept, as it is the only regulative rule in the cluster.
            if(generator.get(0)==true)ret.add((BitSet)generator.clone());
            for(int j=0;j<cluster.size();j++) 
            {
                if(!generator.get(j)){generator.set(j);break;}
                else generator.clear(j);
            }
        }
        
            //The next cycle removes all combinations that activate more than one rule within groups of constitutive rules
            //or groups of exceptions that D-fulfill or defeat the same rule. Rules within these groups can be tested
            //in isolation from the others.
        Hashtable<String,String> indexesDone = new Hashtable<String,String>();
        for(int i=0;i<cluster.size();i++)
        {
            JsonObject first = cluster.get(i).getAsObject();
            try{Integer.parseInt(first.getString("consequent"));}catch(Exception e){continue;}//this will skip regulative rules.
            if(indexesDone.get(first.getString("consequent"))!=null)continue;//and this the indexes that we already processed.
            
                //The indexes in the bit mask of non-regulative rules that D-fulfill or defeat the same rule are identified:
            indexesDone.put(first.getString("consequent"),"");
            Hashtable<Integer,String> bitIndexes = new Hashtable<Integer,String>();
            bitIndexes.put(i,"");
            for(int j=i+1;j<cluster.size();j++)
                if(first.getString("consequent").compareToIgnoreCase(cluster.get(j).getAsObject().getString("consequent"))==0)
                    bitIndexes.put(j,"");
            if(bitIndexes.size()==1)continue;
            
                //If there are two or more bits, this indicates a group of rules, each of which can be tested in isolation
                //from the others. All combinations that activate two or more of these rules are therefore removed.
            for(int k=0;k<ret.size();k++)
            {
                BitSet bs = ret.get(k);
                int counter=0;
                for(int z=0;(z<bs.length()&&(counter<2));z++)
                    if((bs.get(z)==true)&&(bitIndexes.get(z)!=null))
                        counter++;
                if(counter==2)
                {
                    ret.remove(bs);
                    k--;
                }
            }
        }
        
            //From the remaining combinations, all those containing at least one active exception 
            //that is not connected to any active rule are removed.
        for(int i=0;i<ret.size();i++)
        {
            BitSet bs = ret.get(i);
            for(int j=0;j<bs.length();j++)
            {
                if(bs.get(j)==true)
                {
                    JsonObject rule = cluster.get(j).getAsObject();
                    if(rule.getString("type").compareToIgnoreCase("exception")!=0)continue;
                    String index = rule.getString("consequent");
                    for(JsonValue temp:cluster)
                        if(temp.getAsObject().getString("index").compareToIgnoreCase(index)==0)
                        {rule=temp.getAsObject();break;}
                    
                        //If this is true, an exception not connected to any active rule has been identified.
                        //Therefore, the combination is removed.
                    if(bs.get(cluster.indexOf(rule))==false)
                    {
                        ret.remove(i);
                        i--;
                        break;
                    }
                }    
            }   
        }
        
        return ret;
    }
    
        //To generate the ABox for a specific use case, the antecedent facts of the active rules (i.e., those for which the bit
        //in rulesAsserted is true) are combined with the consequents of the regulative rules.
    public static String generateUseCaseABox(JsonArray cluster, String IntervalsAndInstants, BitSet rulesAsserted)throws Exception
    {
        String ABox = 
            "@prefix : <https://w3id.org/ontology/dlr#> .\n"+
            "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n"+
            "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n"+
            "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n"+
            "@prefix time: <http://www.w3.org/2006/time#> .\n"+
            "@base <https://w3id.org/ontology/dlr#> .\n"+
            IntervalsAndInstants+"\n";
        
        for(int index=0;index<cluster.size();index++)
        {
            if(rulesAsserted.get(index)==false)continue;//only the facts with the boolean true are added.
            
                //We add the facts in the antecedent; if the rule is regulative, we also add the facts in the consequent.
            ABox+=cluster.get(index).getAsObject().get("antecedent").getAsString().value();
            if(cluster.get(index).getAsObject().get("type").getAsString().value().compareToIgnoreCase("regulative")==0)
                ABox+=cluster.get(index).getAsObject().get("consequent").getAsString().value();
        }
        
        return ABox;
    }
}
