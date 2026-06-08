package GenerateAndTest;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.jena.atlas.json.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.graph.*;
import org.apache.jena.util.iterator.*;
import DLRreasoner.*;

public class GenerateAndRunRandomABoxes 
{
        //This file generates a sample ABox for the rules in the D-KB, using the same files and procedures as the
        //GenerateAndTest package. However: (1) it considers a single cluster encompassing all rules in the D-KB;
        //and (2) it does not verify the rules, as they have already been fully verified by GenerateAndTest over 
        //all possible combinations. Each rule in the D-KB is activated or deactivated according to the specified 
        //probability. Setting probability=100 activates all rules; setting probability=0 activates none. Setting 
        //probability=X causes a random number between 1 and 100 to be generated for each rule; if the number is less 
        //than or equal to X, the rule is activated; otherwise, it is not. An ABox is then generated as in GenerateAndTest, 
        //and the reasoner is executed on it.
    private static final File UseCaseBaseline = new File("./D-KB/UseCaseBaseline.json");
    private static File DKBTBoxFile  = new File("./D-KB/DKBTBox.ttl");
    private static File DKBrulesFile = new File("./D-KB/DKBrules.ttl");
    
        //The SHACL versions of the use cases generated here are added to this subfolder so that the SHACL reasoner 
        //can be run on the same ABoxes.
    private static File directory = new File("../SHACLreasonerFromRobaldoetal2023/UseCases");
    
        //The first parameter is the default probability (e.g., 50%), the second one is the default number of random samples.
    private static String[] defaultInput = new String[]{"50","5"};
            
    public static void main(String[] args)
    {
        if((args==null)||(args.length==0))args=defaultInput;
        int probability = Integer.parseInt(args[0]);
        int iterations = Integer.parseInt(args[1]);

        try
        {
                //Initializing the reasoner
            Graph domainTBox = GraphMemFactory.createDefaultGraph();
            FileInputStream fis = new FileInputStream(DKBTBoxFile);
            RDFDataMgr.read(domainTBox, fis, Lang.TURTLE);
            fis.close();
            fis = new FileInputStream(DKBrulesFile);
            RDFDataMgr.read(domainTBox, fis, Lang.TURTLE);
            fis.close();
            DLRreasoner reasoner = new DLRreasoner(domainTBox);

                //Loading the JSON file
            fis = new FileInputStream(UseCaseBaseline);
            JsonArray DKBrules = JSON.parse(JSON.parse(fis).toString()).get("D-KBrules").getAsArray();
            fis.close();
            String tempIntervalsAndInstants = null;
            for(JsonValue temp:DKBrules)
            {
                if(temp.getAsObject().hasKey("IntervalsAndInstants"))
                {
                    tempIntervalsAndInstants = temp.getAsObject().getString("IntervalsAndInstants");
                    DKBrules.remove(temp);
                    break;
                }
            }
            if(tempIntervalsAndInstants==null)throw new Exception("\"IntervalsAndInstants\" is not present!");
            final String IntervalsAndInstants = tempIntervalsAndInstants;

                //Re-executing the experiment iterations time, each time with a different combination.
            Graph ABox = GraphMemFactory.createDefaultGraph();
            ArrayList<Double> times = new ArrayList<Double>();
            for(int iteration=0; iteration<iterations; iteration++)
            {
                System.out.println("Processing use case #"+(iteration+1));

                BitSet combinationOfAssertedFacts = new BitSet(DKBrules.size());
                if((probability<0)||(probability>100))throw new Exception("Probability must be between 0 and 100!");

                Random rand = new Random();
                for(int i=0;i<DKBrules.size();i++)if(rand.nextInt(100)<probability)combinationOfAssertedFacts.set(i);
                ABox.clear();
                String ABoxString = GenerateAndTestAllRules.generateUseCaseABox(DKBrules,IntervalsAndInstants,combinationOfAssertedFacts);
                RDFDataMgr.read(ABox,new StringReader(ABoxString),null,Lang.TURTLE);
                storeABoxForSHACLreasonerFromRobaldoetal2023(ABoxString,iteration,probability);

                long startTime = System.currentTimeMillis();
                Graph inferredGraph = reasoner.InferResults(ABox,false);
                long stopTime = System.currentTimeMillis();
                times.add((((double)(stopTime-startTime))/1000));
                
                    //Serialize and save the inferred RDF graph (but use this with iterations=1!)
                /*
                Model outputModel=ModelFactory.createModelForGraph(inferredGraph);
                DLRreasoner.PREFIX_MAP.forEach((prefix,uri)->{outputModel.setNsPrefix(prefix,uri);});
                try(FileOutputStream out=new FileOutputStream(new File("./inferredGraph.ttl"))){RDFDataMgr.write(out,outputModel,RDFFormat.TURTLE);}
                /**/
            }

                //computing and printing the average time
            double sum = 0;
            for(int i=0;i<times.size();i++)sum+=times.get(i);
            sum/=iterations;
            System.out.println("\nAverage execution time: "+sum+" seconds\n");
        }
        catch(Exception e)
        {
            System.out.println(e.getMessage());
            System.exit(0);
        }
    }
    
    private static void storeABoxForSHACLreasonerFromRobaldoetal2023(String ABoxString, int iteration, int probability)throws Exception
    {
            //If the SHACL reasoner has been downloaded as instructed on GitHub, all files are deleted
            //in the SHACL CORPUS subfolder and the 0UseCases directory is created.
        if(iteration==0)for(File file:directory.listFiles())file.delete();
        
            //The generated ABox is added to the 0UseCases folder.
        File ABox = new File(directory.getAbsolutePath()+"/UseCase"+(iteration+1)+"_Probability"+probability+".ttl");
        try(BufferedWriter bw=Files.newBufferedWriter(ABox.toPath(),java.nio.charset.StandardCharsets.UTF_8)){bw.write(ABoxString);}
    }
}
