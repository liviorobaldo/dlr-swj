import java.util.*;
import java.io.*;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileUtils;
import org.apache.jena.riot.*;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.jenax.util.JenaUtil;
import org.topbraid.shacl.rules.RuleUtil;


public class SHACLreasonerFromRobaldoetal2023 
{
    public static void main(String[] args) throws Exception 
    {
        File TBoxDLRandDKBFile = new File("./TBoxDLRandDKB.ttl");
        File DLRandDKBrulesFile = new File("./DLRandDKBrules.ttl");
        File DLRrulesComplianceFile = new File("./DLRrulesCompliance.ttl");
        File CORPUS = new File("./UseCases");
        
        if(args.length>0)
        {
            TBoxDLRandDKBFile = new File(args[0]);
            DLRandDKBrulesFile = new File(args[1]);
            DLRrulesComplianceFile = new File(args[2]);
            CORPUS = new File(args[3]);
        }
		
            //Load the TBox
        Model TBoxDLRandDKB = JenaUtil.createMemoryModel();
        FileInputStream fisTB = new FileInputStream(TBoxDLRandDKBFile);
        TBoxDLRandDKB.read(fisTB, "urn:dummy", FileUtils.langTurtle);
        fisTB.close();
            
            //Load rules
        Model DLRandDKBrules = JenaUtil.createMemoryModel();
        FileInputStream fisRules = new FileInputStream(DLRandDKBrulesFile);
        DLRandDKBrules.read(fisRules, "urn:dummy", FileUtils.langTurtle);
        fisRules.close();
        Model DLRrulesCompliance = JenaUtil.createMemoryModel();
        fisRules = new FileInputStream(DLRrulesComplianceFile);
        DLRrulesCompliance.read(fisRules, "urn:dummy", FileUtils.langTurtle);
        fisRules.close();

        double[] inputStatsSum = new double[]{0,0,0,0};
        double[] outputStatsSum = new double[]{0,0,0,0};
        double timeSum = 0;
        for(File UseCase:CORPUS.listFiles())
        {
            System.out.println("Evaluating "+UseCase.getName());
        
                //Load the whole ontology (Load the ABox and add the TBox to it)
            Model ontology = JenaUtil.createMemoryModel();
            FileInputStream fisAB = new FileInputStream(UseCase);
            ontology.read(fisAB, "urn:dummy", FileUtils.langTurtle).add(TBoxDLRandDKB);
            fisAB.close();
            
            int[] inputStats = retrieveStats(ontology.getGraph());
            for(int k=0;k<inputStats.length;k++)inputStatsSum[k]+=inputStats[k];

            long startTime = System.currentTimeMillis();
            Model inferredModel=RuleUtil.executeRules(ontology,DLRandDKBrules,null,null).add(ontology);
            long size = -1;
            while(size<inferredModel.size())
            {
                size = inferredModel.size();
                inferredModel=RuleUtil.executeRules(inferredModel,DLRandDKBrules,null,null).add(inferredModel);
            }
            inferredModel = RuleUtil.executeRules(inferredModel, DLRrulesCompliance, null, null).add(inferredModel);
            size = -1;
            while(size<inferredModel.size())
            {
                size = inferredModel.size();
                inferredModel=RuleUtil.executeRules(inferredModel,DLRrulesCompliance,null,null).add(inferredModel);
            }
            long stopTime = System.currentTimeMillis();
            
            timeSum += ((double)(stopTime-startTime))/1000;
            int[] outputStats = retrieveStats(inferredModel.getGraph());
            for(int k=0;k<outputStats.length;k++)outputStatsSum[k]+=outputStats[k];
            
            //try(FileOutputStream out=new FileOutputStream(new File("./inferredGraphSHACL.ttl"))){RDFDataMgr.write(out,inferredModel,RDFFormat.TURTLE);}
        }
        
        //computing the averages
        for(int k=0;k<inputStatsSum.length;k++)inputStatsSum[k]/=CORPUS.listFiles().length;
        for(int k=0;k<outputStatsSum.length;k++)outputStatsSum[k]/=CORPUS.listFiles().length;
        timeSum/=CORPUS.listFiles().length;

        System.out.println("\n\n");
        System.out.println("===== input model statistics (average) ======");
        printStats(inputStatsSum);
        System.out.println("\n===== output model statistics (average) =====");
        printStats(outputStatsSum);
        System.out.println("\n================================");
        printDelta(inputStatsSum, outputStatsSum);
        System.out.println("\nAverage execution time: "+timeSum+"s\n");
    }
    
    private static Resource getAssociatedPublishing(Model inferredModel, Resource violated)throws Exception
    {
        Property rdfType = inferredModel.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
        Property hasTheme = inferredModel.getProperty("http://www.licenceusecaseonto.org/has-theme");
        RDFNode result = inferredModel.listObjectsOfProperty(violated, hasTheme).toList().get(0);
        
        //Now we have to take the Publish action with the same use-case index of the result.
        String usecaseIndex = result.asResource().getLocalName();
        usecaseIndex = usecaseIndex.substring(usecaseIndex.indexOf("_")+1, usecaseIndex.indexOf("_", usecaseIndex.indexOf("_")+1)).trim();
        
        RDFNode Publish = inferredModel.getResource("http://www.licenceusecaseonto.org/Publish_"+usecaseIndex);
        ResIterator publishIndividuals = inferredModel.listSubjectsWithProperty(rdfType, Publish);
        while(publishIndividuals.hasNext())
        {
            Resource individual = publishIndividuals.nextResource();
            
            //System.out.println(individual.asResource().getLocalName());
            
            if(inferredModel.listObjectsOfProperty(individual, hasTheme).toList().get(0)==result)
                return individual;
        }
        throw new Exception("Cannot find the publish associated with the remove.");
    }
    
    
        //Come sopra, but this takes in input a folder "XUseCases", e.g., "10UseCases" and returns the files ordered on the size.
    public static ArrayList<File> orderFilesOnSize(File ABox)
    {
        //We order the files according to the size
        ArrayList<File> temp = new ArrayList<File>();
        for(File f:ABox.listFiles())temp.add(f);
        ArrayList<File> orderedFiles = new ArrayList<File>();
        
        while(temp.isEmpty()==false)
        {
            int min = 0;
            String sizeMin = temp.get(0).getName().substring(temp.get(0).getName().indexOf("_Size")+5, temp.get(0).getName().indexOf("_", temp.get(0).getName().indexOf("_Size")+5)).trim();
            for(int i=1;i<temp.size();i++)
            {
                String size = temp.get(i).getName().substring(temp.get(i).getName().indexOf("_Size")+5, temp.get(i).getName().indexOf("_", temp.get(i).getName().indexOf("_Size")+5)).trim();
                if(Integer.parseInt(size)<Integer.parseInt(sizeMin))
                {
                    min=i;
                    sizeMin=size;
                }
            }
            orderedFiles.add(temp.remove(min));
        }
        return orderedFiles;
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