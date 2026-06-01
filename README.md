<div align="justify">
  <p>
    This GitHub repository contains the Java source code accompanying the paper
    "Deontic Logic Reified: a deontic logic for the Semantic Web". The logic and all examples shown in the paper
    are implemented in RDF and SPARQL, and make use of the
    <a href="https://jena.apache.org/">Apache Jena</a> libraries for execution.
  </p>

  <p>
    There are two main subfolders: <code>DLRsuite</code> and <code>SHACLreasonerFromRobaldoetal2023</code>.
    The RDF and SPARQL resources defining Deontic Logic Reified (DLR) are contained in <code>DLRsuite</code>,
    while <code>SHACLreasonerFromRobaldoetal2023</code> contains the SHACL-based reasoner from the paper
    <a href="https://link.springer.com/article/10.1007/s10506-023-09360-z">[Robaldo et al., 2023]</a>,
    adapted to operate on the same DLR examples for comparison purposes.
  </p>
  <p>
    <code>compileAll.bat</code> compiles all Java files in both folders under Windows. Once these files have been compiled, one of the following run scripts can be executed:
    <ul>
      <li>
        <code>runExample.bat</code> executes the 12 examples in the <code>DLRsuite\Examples</code> folder, as well as additional examples that readers may prepare themselves, using the DLR reasoner. The inferred knowledge graph is stored in the file
        <code>DLRsuite\inferredGraph.ttl</code>. The specific example to be executed is provided as a parameter to the batch file.
      </li>
      <br>
      <li>
        <code>runDRLrulesBuilder.bat</code> takes as input the compact representation of the DLR rules corresponding to the encoded formulae from the DAPRECO knowledge base (see
        <a href="https://link.springer.com/article/10.1007/s10849-019-09309-z">[Robaldo et al., 2020]</a>) and generates the full versions of the rules to be processed by the DLR reasoner, as well as their counterparts adapted for the SHACL-based reasoner from
        <a href="https://link.springer.com/article/10.1007/s10506-023-09360-z">[Robaldo et al., 2023]</a>.
      </li>
      <br>
      <li>
        <code>runGenerateAndTestAllRules.bat</code> generates all possible combinations of ABoxes for the DLR rules produced by the previous batch file and, for each of them, verifies that the expected outcomes are present in the inferred knowledge graph computed by the DLR reasoner. The generate-and-test evaluation is guided by the file
        <code>DLRsuite\D-KB\UseCaseBaseline.json</code>, as explained in Section 6 of the paper.
      </li>
      <br>
      <li>
        <code>runGenerateAndRunRandomABoxes.bat</code> uses the file <code>DLRsuite\D-KB\UseCaseBaseline.json</code> to generate <code>n</code> random ABoxes, which are then processed by the DLR reasoner. The script also measures computational performance. In addition, it produces corresponding ABoxes in the input format of the SHACL-based reasoner, which can then be executed using <code>runSHACLreasonerFromRobaldoetal2023.bat</code>. The latter also measures computational performance, showing that the DLR reasoner outperforms the SHACL-based one (see Section 7 of the paper).
      </li>
    </ul>
  </p>
  <p>
    Further info can be found in the <code>DLRsuite</code> and <code>SHACLreasonerFromRobaldoetal2023</code> subfolders.
  </p>
</div>
