
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that inferredGraph.ttl contains ALL AND ONLY the :intersects / :includes
 * relations expected between the pairs of temporal entities in each test case
 * of Example4.ttl.
 *
 * - Example4.ttl is read as a text file: the expected relations are in the
 *   "# Expected:" comments.
 * - inferredGraph.ttl is loaded as an RDF graph using Apache Jena.
 *
 * "Only" means that, between the two intervals in a pair (in both directions),
 * no :intersects / :includes triples may exist other than those listed.
 * Relations involving entities from other test cases are ignored (as explained
 * in the description of Example4.ttl).
 *
 */
public class VerifyInferencesExample4 {

    static final String NS = "https://w3id.org/ontology/dlr#";
    static final String[] RELATIONS = {"intersects", "includes"};

    // "# Expected:" (on a line by itself)
    static final Pattern EXPECTED_HEADER = Pattern.compile("^#\\s*Expected:\\s*$");
    // "# :tp371 :intersects :tp372 ."
    static final Pattern EXPECTED_TRIPLE =
            Pattern.compile("^#\\s*:(\\S+)\\s+:(intersects|includes)\\s+:(\\S+?)\\s*\\.\\s*$");
    // "# No :intersects or :includes triple between :tp21 and :tp22, in either direction."
    static final Pattern EXPECTED_NONE =
            Pattern.compile("^#\\s*No\\s+:intersects or :includes triple between\\s+:(\\S+)\\s+and\\s+:(\\S+)\\s*,.*$");

    /** A test case: the pair (a, b), the expected triples, and the line where it appears. */
    record TestCase(String a, String b, Set<String> expected, int line) {}

    public static void main(String[] args) throws IOException {
        String examplePath = args.length > 0 ? args[0] : "Example4.ttl";
        String inferredPath = args.length > 1 ? args[1] : "inferredGraph.ttl";

        List<String> errors = new ArrayList<>();
        List<TestCase> cases = parseExpected(examplePath, errors);
        if (!errors.isEmpty()) {
            errors.forEach(e -> System.err.println("PARSING ERROR: " + e));
            System.exit(2);
        }
        System.out.println("Test cases found in " + examplePath + ": " + cases.size());

        Model inferred = RDFDataMgr.loadModel(inferredPath);
        System.out.println("Triples loaded from " + inferredPath + ": " + inferred.size());
        System.out.println();

        int ok = 0, ko = 0;
        for (TestCase tc : cases) {
            Set<String> actual = actualRelations(inferred, tc.a(), tc.b());

            Set<String> missing = new TreeSet<>(tc.expected());
            missing.removeAll(actual);
            Set<String> unexpected = new TreeSet<>(actual);
            unexpected.removeAll(tc.expected());

            if (missing.isEmpty() && unexpected.isEmpty()) {
                ok++;
            } else {
                ko++;
                System.out.printf("FAIL  :%s / :%s  (Example4.ttl, line %d)%n",
                        tc.a(), tc.b(), tc.line());
                missing.forEach(t -> System.out.println("   missing    : " + t + " ."));
                unexpected.forEach(t -> System.out.println("   unexpected : " + t + " ."));
            }
        }

        System.out.println();
        System.out.printf("Result: %d OK, %d FAIL out of %d test cases%n",
                ok, ko, cases.size());
        System.exit(ko == 0 ? 0 : 1);
    }

    /** Extracts the test cases from the "# Expected:" comments in Example4.ttl. */
    static List<TestCase> parseExpected(String path, List<String> errors) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
        List<TestCase> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            if (!EXPECTED_HEADER.matcher(lines.get(i).trim()).matches()) continue;

            int startLine = i + 1; // 1-based line numbering
            String a = null, b = null;
            Set<String> expected = new LinkedHashSet<>();
            List<String[]> triples = new ArrayList<>();

            int j = i + 1;
            for (; j < lines.size(); j++) {
                String l = lines.get(j).trim();
                if (!l.startsWith("#") || l.startsWith("####")) break; // end of comment block

                Matcher mt = EXPECTED_TRIPLE.matcher(l);
                Matcher mn = EXPECTED_NONE.matcher(l);
                if (mt.matches()) {
                    triples.add(new String[]{mt.group(1), mt.group(2), mt.group(3)});
                } else if (mn.matches()) {
                    if (a == null) {
                        a = mn.group(1);
                        b = mn.group(2);
                    }
                }
                // Ignore other comment lines within the block.
            }
            i = j - 1;

            if (a == null && !triples.isEmpty()) {
                a = triples.get(0)[0];
                b = triples.get(0)[2];
            }
            if (a == null) {
                errors.add("the 'Expected' block at line " + startLine
                        + " contains neither triples nor a 'No ...' statement");
                continue;
            }

            for (String[] t : triples) {
                boolean inPair = (t[0].equals(a) && t[2].equals(b))
                        || (t[0].equals(b) && t[2].equals(a));
                if (!inPair) {
                    errors.add("line " + startLine + ": triple ':" + t[0] + " :" + t[1]
                            + " :" + t[2] + "' does not belong to the pair :" + a + " / :" + b);
                }
                expected.add(fmt(t[0], t[1], t[2]));
            }
            result.add(new TestCase(a, b, expected, startLine));
        }
        return result;
    }

    /** Returns all :intersects / :includes triples in the graph between a and b, in both directions. */
    static Set<String> actualRelations(Model m, String a, String b) {
        Set<String> found = new LinkedHashSet<>();
        String[][] directions = {{a, b}, {b, a}};
        for (String[] d : directions) {
            Resource s = m.createResource(NS + d[0]);
            Resource o = m.createResource(NS + d[1]);
            for (String rel : RELATIONS) {
                Property p = m.createProperty(NS + rel);
                if (m.contains(s, p, o)) {
                    found.add(fmt(d[0], rel, d[1]));
                }
            }
        }
        return found;
    }

    static String fmt(String s, String rel, String o) {
        return ":" + s + " :" + rel + " :" + o;
    }
}
