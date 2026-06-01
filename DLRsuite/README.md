<div align="justify">
  <p>
    This folder contains the following subfolders:
    <ul>
      <li>
          <code>D-KB</code> contains the rules corresponding to the selected D-KB formulae. The compact version of these rules can be found in the
          file <code>SPARQLrulesCompact.txt</code>. <code>runDRLrulesBuilder.bat</code> takes this file as input and generates the files
          <code>DKBrules.ttl</code> and <code>DKBTBox.ttl</code> in the <code>D-KB</code> subfolder. These files contain the full version of the 
          rules and the TBox to be executed by the DLR reasoner. This subfolder also includes <code>UseCaseBaseline.json</code> and
          <code>reducedUseCaseBaseline.json</code>, which are used by <code>runGenerateAndTestAllRules.bat</code> to generate and test the rules in
          <code>DKBrules.ttl</code>.
      </li>
      <br>
      <li>
        <code>DKBmanager</code> contains the Java classes executed by <code>runDRLrulesBuilder.bat</code>, which generate
        <code>DKBrules.ttl</code> and <code>DKBTBox.ttl</code>.
      </li>
      <br>
      <li>
        <code>DLRreasoner</code> contains the Java source code of the DLR reasoner, which is used by (almost) all other components to execute DLR 
        rules.
      </li>
      <br>
      <li>
        <code>DLRrules</code> contains the core rules of the Deontic Logic Reified framework, used to check compliance, detect violations, etc.
        (file <code>DLRcompliance.ttl</code>), as well as to implement defeasibility and temporal reasoning.
      </li>
      <br>
      <li>
        <code>Examples</code> contains the 12 examples discussed throughout the paper. Each example includes a detailed explanation of the encoded 
        norms and the expected results. These files can be executed via <code>runExample.bat</code>, and the inferred knowledge graph is stored in
        <code>inferredGraph.ttl</code>.
      </li>
      <br>
      <li>
        <code>GenerateAndTest</code> contains the Java classes that implement the generate-and-test methodology, including the random generation of 
        sample ABoxes to be processed by the DLR reasoner. The classes in this subfolder are executed via <code>runGenerateAndTestAllRules.bat</code>
        and <code>runGenerateAndRunRandomABoxes.bat</code>.
      </li>
    </ul>
  </p>
</div>
