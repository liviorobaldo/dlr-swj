package DLRreasoner;

import java.util.*;
import java.io.*;
import java.util.concurrent.*;

import org.apache.jena.riot.*;
import org.apache.jena.query.*;
import org.apache.jena.vocabulary.*;
import org.apache.jena.graph.*;
import org.apache.jena.util.iterator.*;
import org.apache.jena.sparql.core.*;

public class DLRreasoner
{
    private static final File DLRTBoxfile = new File("DLRrules/DLR.ttl");
    private static final File DLRcomplianceTBoxfile = new File("DLRrules/DLRcompliance.ttl");
    public static final Map<String,String> PREFIX_MAP = new HashMap<String,String>(){{
        put("", "https://w3id.org/ontology/dlr#");
        put("xsd","http://www.w3.org/2001/XMLSchema#");
        put("rdf","http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        put("rdfs","http://www.w3.org/2000/01/rdf-schema#");
        put("time","http://www.w3.org/2006/time#");
    }};
    
    private Graph baseGraph = null;
    private ArrayList<Query> inferenceRulesTBox = new ArrayList<Query>();
    private ArrayList<Query> inferenceRulesComplianceChecking = new ArrayList<Query>();
    public DLRreasoner(Graph domainTBox)throws Exception
    {
        Graph DLRTBox = GraphMemFactory.createDefaultGraph();
        FileInputStream fis = new FileInputStream(DLRTBoxfile);
        RDFDataMgr.read(DLRTBox,fis,Lang.TURTLE);
        fis.close();
        inferenceRulesTBox = extractAllInferenceRules(DLRTBox);
        inferenceRulesTBox.addAll(extractAllInferenceRules(domainTBox));
        Graph DLRcomplianceTBox = GraphMemFactory.createDefaultGraph();
        fis = new FileInputStream(DLRcomplianceTBoxfile);
        RDFDataMgr.read(DLRcomplianceTBox,fis,Lang.TURTLE);
        fis.close();
        inferenceRulesComplianceChecking = extractAllInferenceRules(DLRcomplianceTBox);
        baseGraph = GraphMemFactory.createDefaultGraph();
        DLRTBox.find().forEachRemaining(baseGraph::add);
        domainTBox.find().forEachRemaining(baseGraph::add);
    }

    private ArrayList<Query> extractAllInferenceRules(Graph graph)
    {
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String,String> e:PREFIX_MAP.entrySet())
            if(e.getKey().isEmpty())sb.append("PREFIX : <").append(e.getValue()).append(">\n");
            else sb.append("PREFIX ").append(e.getKey()).append(": <").append(e.getValue()).append(">\n");
        String prefixes = sb.toString();

        ArrayList<Query> ret = new ArrayList<Query>();
        Node property = NodeFactory.createURI("https://w3id.org/ontology/dlr#has-sparql-code");
        ExtendedIterator<Triple> it = graph.find(Node.ANY,property,Node.ANY);
        while(it.hasNext())
        {
            Node obj = it.next().getObject();
            if(obj.isLiteral())
            {
                String sparql = obj.getLiteralLexicalForm();
                ret.add(QueryFactory.create(prefixes+sparql));
            }
        }

        return ret;
    }
    
    public final Graph InferResults(Graph ABox,boolean removeVerboseStatementsFromOutput)throws Exception
    {
        Graph graph = GraphMemFactory.createDefaultGraph();
        baseGraph.find().forEachRemaining(graph::add);
        ABox.find().forEachRemaining(graph::add);

        ArrayList<Query> inferenceRules = new ArrayList<Query>(extractAllInferenceRules(ABox)){{addAll(inferenceRulesTBox);}};

        ExecutorService executor= Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        long lastSize=-1;
        while(graph.size()>lastSize)
        {
            lastSize=graph.size();
            Graph snapshotGraph=GraphMemFactory.createDefaultGraph();
            graph.find().forEachRemaining(snapshotGraph::add);

            DatasetGraph dataset = DatasetGraphFactory.create();
            dataset.addGraph(org.apache.jena.sparql.core.Quad.defaultGraphIRI, snapshotGraph);

            List<Future<Graph>> futures=new ArrayList<>();
            for(Query rule:inferenceRules)
                futures.add(executor.submit(()->{try(QueryExecution qe=QueryExecutionFactory.create(rule,dataset)){return qe.execConstruct().getGraph();}}));
            for(Future<Graph> future:futures)future.get().find().forEachRemaining(graph::add);
        }

        lastSize=-1;
        while(graph.size()>lastSize)
        {
            lastSize=graph.size();

            Graph snapshotGraph=GraphMemFactory.createDefaultGraph();
            graph.find().forEachRemaining(snapshotGraph::add);

            DatasetGraph dataset = DatasetGraphFactory.create();
            dataset.addGraph(org.apache.jena.sparql.core.Quad.defaultGraphIRI, snapshotGraph);

            List<Future<Graph>> futures=new ArrayList<>();
            for(Query rule:inferenceRulesComplianceChecking)
                futures.add(executor.submit(()->{try(QueryExecution qe=QueryExecutionFactory.create(rule,dataset)){return qe.execConstruct().getGraph();}}));

            for(Future<Graph> future:futures)future.get().find().forEachRemaining(graph::add);
        }

        executor.shutdown();

            //Remove verbose statements, if the removeVerboseStatementsFromOutput=true. 
        if(removeVerboseStatementsFromOutput)identifyVerboseStatements(graph).forEach(graph::delete);

        return graph;
    }
    
        //The key inferences of the proposed framework concern compliance and violations: the goal is to determine whether norms
        //have been complied with or violated. Some inferred statements are not relevant to these core derivations, so including
        //them in the output file would be unnecessarily verbose. This method identifies such statements and returns them, so that
        //the previous method can remove them.
    private List<Triple> identifyVerboseStatements(Graph graph)throws Exception
    {
            //All triples in the TBox (see constructor) can be removed.
        ArrayList<Triple> toRemove = new ArrayList<>();
        baseGraph.find().forEachRemaining(toRemove::add);

        graph.find().forEachRemaining(stmt->{
            Node s = stmt.getSubject();
            Node p = stmt.getPredicate();
            Node o = stmt.getObject();
            if(s.toString().contains("CONSTRUCT"))toRemove.add(stmt);
            if(p.equals(NodeFactory.createURI(PREFIX_MAP.get("")+"has-sparql-code")))toRemove.add(stmt);
            if(p.equals(NodeFactory.createURI(PREFIX_MAP.get("")+"has-dkb-id")))toRemove.add(stmt);
            if(p.equals(NodeFactory.createURI(PREFIX_MAP.get("")+"Modality")))toRemove.add(stmt);
            if(p.equals(NodeFactory.createURI(PREFIX_MAP.get("")+"InferenceRule")))toRemove.add(stmt);
            if(p.equals(NodeFactory.createURI(PREFIX_MAP.get("")+"ThematicRole")))toRemove.add(stmt);
            if(p.equals(NodeFactory.createURI(PREFIX_MAP.get("")+"EventualityType")))toRemove.add(stmt);
            if(p.equals(RDF.type.asNode())&&o.equals(RDF.Property.asNode()))toRemove.add(stmt);
            if(p.equals(RDF.type.asNode())&&o.equals(RDFS.Class.asNode()))toRemove.add(stmt);
            if(p.equals(RDF.type.asNode())&&o.equals(RDF.Statement.asNode()))toRemove.add(stmt);
            if(p.equals(RDF.type.asNode())&&o.equals(NodeFactory.createURI(RDF.uri+"List")))toRemove.add(stmt);
            if(p.equals(RDF.type.asNode())&&o.equals(NodeFactory.createURI(PREFIX_MAP.get("time")+"GeneralDurationDescription")))toRemove.add(stmt);
            if(p.equals(RDF.type.asNode())&&o.equals(NodeFactory.createURI(PREFIX_MAP.get("time")+"TemporalDuration")))toRemove.add(stmt);
            if(p.equals(RDF.type.asNode())&&o.equals(NodeFactory.createURI(PREFIX_MAP.get("")+"Modality")))toRemove.add(stmt);
            if(p.equals(RDF.type.asNode())&&o.equals(NodeFactory.createURI(PREFIX_MAP.get("")+"InferenceRule")))toRemove.add(stmt);
            if(p.equals(RDF.rest.asNode()))toRemove.add(stmt);
            if(p.equals(RDF.first.asNode()))toRemove.add(stmt);
            if(p.equals(RDFS.subPropertyOf.asNode()))toRemove.add(stmt);
        });

        List<Node> subjects = new ArrayList<>();
        graph.find(Node.ANY,NodeFactory.createURI(PREFIX_MAP.get("")+"includes"),Node.ANY).forEachRemaining(t->{if(!subjects.contains(t.getSubject()))subjects.add(t.getSubject());});
        for(Node subject:subjects)
        {
            graph.find(subject,NodeFactory.createURI(PREFIX_MAP.get("")+"includes"),subject).forEachRemaining(toRemove::add);
            graph.find(subject,NodeFactory.createURI(PREFIX_MAP.get("")+"intersects"),subject).forEachRemaining(toRemove::add);
            List<Node> ends = new ArrayList<>();
            graph.find(subject,NodeFactory.createURI("http://www.w3.org/2006/time#hasBeginning"),Node.ANY).forEachRemaining(t->ends.add(t.getObject()));
            graph.find(subject,NodeFactory.createURI("http://www.w3.org/2006/time#hasEnd"),Node.ANY).forEachRemaining(t->ends.add(t.getObject()));
            for(Node be:ends)
            {
                graph.find(subject,NodeFactory.createURI(PREFIX_MAP.get("")+"includes"),be).forEachRemaining(toRemove::add);
                graph.find(subject,NodeFactory.createURI(PREFIX_MAP.get("")+"intersects"),be).forEachRemaining(toRemove::add);
                graph.find(be,NodeFactory.createURI(PREFIX_MAP.get("")+"intersects"),subject).forEachRemaining(toRemove::add);
            }
        }

        return toRemove;
    }
}