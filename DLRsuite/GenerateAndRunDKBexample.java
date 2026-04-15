import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.jena.atlas.json.*;
import org.apache.jena.riot.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.graph.*;
import org.apache.jena.util.iterator.*;
import DLRreasoner.*;
import GenerateAndTest.*;

public class GenerateAndRunDKBexample 
{
        //This file generates a sample ABox for the rules in the D-KB, using the same files and procedures as the GenerateAndTest package.
        //However: (1) it considers a single cluster spanning all rules in the D-KB; (2) it does not verify the rules, as they are already
        //fully verified in GenerateAndTest on all possible combinations.
        //Each rule in the D-KB is activated or not depending on the specified probability. Setting probability=100 activates all rules;
        //setting probability=0 activates none; setting probability=X generates a random number from 1 to 100 for each rule and if the 
        //number is greater than X, the rule is activated; otherwise it is not.
        //An ABox is then generated as in GenerateAndTest, and the reasoner is executed on it.
    private static final File UseCaseBaseline = new File("./D-KB/UseCaseBaseline.json");
    private static File DKBTBoxFile  = new File("./D-KB/DKBTBox.ttl");
    private static File DKBrulesFile = new File("./D-KB/DKBrules.ttl");
        
    public static void main(String[] args)
    {
        if((args==null)||(args.length==0))args=new String[]{"50","5"};

        double[] inputStatsSum = new double[]{0,0,0,0};
        double[] outputStatsSum = new double[]{0,0,0,0};
        double timeSum = 0;
        
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
            for(int iteration=0; iteration<iterations; iteration++)
            {
                System.out.println("Use case #"+(iteration+1));

                BitSet combinationOfAssertedFacts = new BitSet(DKBrules.size());
                if((probability<0)||(probability>100))throw new Exception("Probability must be between 0 and 100!");

                Random rand = new Random();
                for(int i=0;i<DKBrules.size();i++)if(rand.nextInt(100)<probability)combinationOfAssertedFacts.set(i);
                ABox.clear();
                String ABoxString = GenerateAndTest.generateUseCaseABox(DKBrules,IntervalsAndInstants,combinationOfAssertedFacts);
                RDFDataMgr.read(ABox,new StringReader(ABoxString),null,Lang.TURTLE);
                storeABoxForSHACLreasonerFromRobaldoetal2023(ABoxString,iteration,probability);

                int[] inputStats = retrieveStats(ABox);
                for(int k=0;k<inputStats.length;k++)inputStatsSum[k]+=inputStats[k];

                long startTime = System.currentTimeMillis();
                Graph inferredGraph = reasoner.InferResults(ABox,false);
                long stopTime = System.currentTimeMillis();
                timeSum += ((double)(stopTime-startTime))/1000;
                
                int[] outputStats = retrieveStats(inferredGraph);
                for(int k=0;k<outputStats.length;k++)outputStatsSum[k]+=outputStats[k];
                
                    //Serialize and save the inferred RDF graph (but use this with iterations=1!)
                /*
                Model outputModel=ModelFactory.createModelForGraph(inferredGraph);
                DLRreasoner.PREFIX_MAP.forEach((prefix,uri)->{outputModel.setNsPrefix(prefix,uri);});
                try(FileOutputStream out=new FileOutputStream(new File("./inferredGraph.ttl"))){RDFDataMgr.write(out,outputModel,RDFFormat.TURTLE);}
                /**/
            }

                //computing the averages
            for(int k=0;k<inputStatsSum.length;k++)inputStatsSum[k]/=iterations;
            for(int k=0;k<outputStatsSum.length;k++)outputStatsSum[k]/=iterations;
            timeSum/=iterations;

            System.out.println("\n\n");
            System.out.println("===== input model statistics (average) ======");
            printStats(inputStatsSum);
            System.out.println("\n===== output model statistics (average) =====");
            printStats(outputStatsSum);
            System.out.println("\n================================");
            printDelta(inputStatsSum, outputStatsSum);
            System.out.println("\nAverage execution time: "+timeSum+"s\n");
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
        File directory = new File("../SHACLreasonerFromRobaldoetal2023/UseCases");
        if(iteration==0)for(File file:directory.listFiles())file.delete();
        
            //The generated ABox is added to the 0UseCases folder.
        File ABox = new File(directory.getAbsolutePath()+"/UseCase"+(iteration+1)+"_Probability"+probability+".ttl");
        try(BufferedWriter bw=Files.newBufferedWriter(ABox.toPath(),java.nio.charset.StandardCharsets.UTF_8)){bw.write(ABoxString);}
    }
    
    
    private static int[] retrieveStats(Graph graph)
    {
        Set<Node> anonIndividuals = new HashSet<>();

        // Count anonymous individuals (subjects of rdf:type that are blank nodes)
        ExtendedIterator<Triple> it = graph.find(Node.ANY, RDF.type.asNode(), Node.ANY);
        while(it.hasNext())
        {
            Triple t = it.next();
            Node s = t.getSubject();
            if(s.isBlank())anonIndividuals.add(s);
        }

        long numAnonIndividuals = anonIndividuals.size();
        long classAssertions = graph.find(Node.ANY, RDF.type.asNode(), Node.ANY).toList().size();
        long objectPropertyAssertions = 0;
        long dataPropertyAssertions = 0;

        ExtendedIterator<Triple> all = graph.find();
        while(all.hasNext())
        {
            Triple t = all.next();
            if(t.getPredicate().equals(RDF.type.asNode())) continue;
            Node obj = t.getObject();
            if(obj.isLiteral()) dataPropertyAssertions++;
            else objectPropertyAssertions++;
        }

        return new int[]{
            (int) numAnonIndividuals,
            (int) classAssertions,
            (int) objectPropertyAssertions,
            (int) dataPropertyAssertions
        };
    }
    
    private static void printStats(double[] stats)
    {
        System.out.println("Anonymous individuals: " + stats[0]);
        System.out.println("Class assertions: " + stats[1]);
        System.out.println("Object property assertions: " + stats[2]);
        System.out.println("Data property assertions: " + stats[3]);
    }
    
    private static void printDelta(double[] inputStats, double[] outputStats)
    {
        double newIndividuals = outputStats[0]-inputStats[0];
        double newAssertions =
                (outputStats[1] - inputStats[1]) +
                (outputStats[2] - inputStats[2]) +
                (outputStats[3] - inputStats[3]);

        System.out.println("Average number of new anonymous individuals created: " + newIndividuals);
        System.out.println("Average number of new class or property assertions: " + newAssertions);
    }
}
