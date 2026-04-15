import java.io.*;
import org.apache.jena.riot.*;
import org.apache.jena.graph.*;
import DLRreasoner.*;
import org.apache.jena.rdf.model.*;

public class RunExample 
{
    private static File ExampleFile = new File("./Examples/Example1.ttl");
    private static File inferredGraphFile = new File("./inferredGraph.ttl");

        //For simplicity, the TBox of the D-KB also includes all predicates (eventuality types and thematic roles) used in the examples.
    private static File domainTBoxFile = new File("D-KB/DKBTBox.ttl");
    
    public static void main(String[] args)throws Exception
    {
            //Reasoner initialization
        Graph domainTBox = GraphMemFactory.createDefaultGraph();
        try(FileInputStream fis=new FileInputStream(domainTBoxFile)){RDFDataMgr.read(domainTBox,fis,Lang.TURTLE);}
        DLRreasoner reasoner=new DLRreasoner(domainTBox);
        
            //Loading the ABox
        Graph ABox = GraphMemFactory.createDefaultGraph();
        try(FileInputStream fis=new FileInputStream(ExampleFile)){RDFDataMgr.read(ABox,fis,Lang.TURTLE);}

            //Running the reasoner
        Graph inferredGraph = reasoner.InferResults(ABox,true);
        
            //Serialize and save the inferred RDF graph.
        Model outputModel=ModelFactory.createModelForGraph(inferredGraph);
        DLRreasoner.PREFIX_MAP.forEach((prefix,uri)->{outputModel.setNsPrefix(prefix,uri);});
        try(FileOutputStream out=new FileOutputStream(inferredGraphFile)){RDFDataMgr.write(out,outputModel,RDFFormat.TURTLE);}
    }
}
